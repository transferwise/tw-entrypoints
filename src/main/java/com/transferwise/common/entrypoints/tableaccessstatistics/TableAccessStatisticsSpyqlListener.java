package com.transferwise.common.entrypoints.tableaccessstatistics;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.transferwise.common.entrypoints.EntryPointContext;
import com.transferwise.common.entrypoints.EntryPoints;
import com.transferwise.common.entrypoints.EntryPointsMetricUtils;
import com.transferwise.common.entrypoints.IEntryPointsRegistry;
import com.transferwise.common.entrypoints.databaseaccessstatistics.DatabaseAccessStatistics;
import com.transferwise.common.spyql.event.GetConnectionEvent;
import com.transferwise.common.spyql.event.StatementExecuteEvent;
import com.transferwise.common.spyql.event.StatementExecuteFailureEvent;
import com.transferwise.common.spyql.listener.SpyqlConnectionListener;
import com.transferwise.common.spyql.listener.SpyqlDataSourceListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.transferwise.common.entrypoints.EntryPointsMetricUtils.TAG_PREFIX_ENTRYPOINTS;

@Slf4j
public class TableAccessStatisticsSpyqlListener implements SpyqlDataSourceListener {
    private static final long MIB = 1_000_000;

    private final EntryPoints entryPoints;
    private final MeterRegistry meterRegistry;
    private final IEntryPointsRegistry entryPointsRegistry;
    private final String databaseName;

    private LoadingCache<String, SqlParseResult> sqlParseResultsCache;

    public TableAccessStatisticsSpyqlListener(EntryPoints entryPoints, IEntryPointsRegistry entryPointsRegistry,
                                              MeterRegistry meterRegistry, String databaseName, long sqlParserCacheSizeMib) {
        this.entryPoints = entryPoints;
        this.databaseName = databaseName;
        this.meterRegistry = meterRegistry;
        this.entryPointsRegistry = entryPointsRegistry;

        sqlParseResultsCache = Caffeine.newBuilder().maximumWeight(sqlParserCacheSizeMib * MIB)
                                       .weigher((String k, SqlParseResult v) -> k.length()).build(sql -> parseSql(sql));

        CaffeineCacheMetrics
            .monitor(meterRegistry, sqlParseResultsCache, TAG_PREFIX_ENTRYPOINTS + "Tas.sqlParseResultsCache");
    }

    @Override
    public SpyqlConnectionListener onGetConnection(GetConnectionEvent event) {
        DatabaseAccessStatistics databaseAccessStatistics = DatabaseAccessStatistics
            .get(entryPoints.currentContext(), databaseName);
        if (databaseAccessStatistics != null) {
            databaseAccessStatistics.registerConnectionOpened();
        }

        return new ConnectionListener();
    }

    protected SqlParseResult parseSql(String sql) {
        SqlParseResult result = new SqlParseResult();
        try {
            Statements stmts = CCJSqlParserUtil.parseStatements(sql);
            for (Statement stmt : stmts.getStatements()) {
                String opName = getOperationName(stmt);
                CustomTablesNamesFinder tablesNamesFinder = new CustomTablesNamesFinder();
                List<String> tableNames = tablesNamesFinder.getTableList(stmt);

                SqlParseResult.SqlOperation sqlOp = result
                    .getOperations()
                    .computeIfAbsent(opName, k -> new SqlParseResult.SqlOperation());
                for (String tableName : tableNames) {
                    sqlOp.getTableNames().add(tableName);
                }
            }
        } catch (Throwable t) {
            meterRegistry
                .counter(TAG_PREFIX_ENTRYPOINTS + "Tas.FailedParses", EntryPointsMetricUtils.TAG_DATABASE, databaseName)
                .increment();
            log.debug(t.getMessage(), t);
        }
        return result;
    }

    protected String getOperationName(Statement stmt) {
        // class.getSimpleName() is very slow on JDK 8
        return StringUtils.substringAfterLast(stmt.getClass().getName(), ".").toLowerCase();
    }

    class ConnectionListener implements SpyqlConnectionListener {
        @Override
        public void onStatementExecute(StatementExecuteEvent event) {
            registerSql(event.getSql(), event.isInTransaction(), true);
        }

        @Override
        public void onStatementExecuteFailure(StatementExecuteFailureEvent event) {
            registerSql(event.getSql(), event.getTransactionId() != null, false);
        }

        protected void registerSql(String sql, boolean isInTransaction, boolean succeeded) {
            EntryPointContext context = entryPoints.currentContextOrUnknown();
            if (entryPointsRegistry.registerEntryPoint(context)) {
                String name = EntryPointsMetricUtils.normalizeNameForMetric(context.getName());
                String group = EntryPointsMetricUtils.normalizeNameForMetric(context.getGroup());

                SqlParseResult sqlParseResult = sqlParseResultsCache.get(sql);

                sqlParseResult.operations.forEach((opName, op) -> {
                    for (String tableName : op.getTableNames()) {
                        Tag dbTag = Tag.of(EntryPointsMetricUtils.TAG_DATABASE, databaseName);
                        Tag entryPointNameTag = Tag.of(EntryPointsMetricUtils.TAG_ENTRYPOINT_NAME, name);
                        Tag entryPointGroupTag = Tag.of(EntryPointsMetricUtils.TAG_ENTRYPOINT_GROUP, group);
                        Tag successTag = Tag.of("success", Boolean.toString(succeeded));
                        Tag operationTag = Tag.of("operation", opName);
                        Tag tableTag = Tag.of("table", tableName);
                        Tag inTransactionTag = Tag.of("inTransaction", Boolean.toString(isInTransaction));

                        List<Tag> tags = Arrays
                            .asList(dbTag, entryPointNameTag, entryPointGroupTag, successTag, operationTag, tableTag,
                                    inTransactionTag);

                        meterRegistry.counter(TAG_PREFIX_ENTRYPOINTS + "Tas.TableAccess", tags).increment();
                    }
                });
            }
        }
    }

    @Data
    @Accessors(chain = true)
    static class SqlParseResult {
        private Map<String, SqlOperation> operations = new HashMap<>();

        @Data
        @Accessors(chain = true)
        static class SqlOperation {
            private Set<String> tableNames = new HashSet<>();
        }
    }
}
