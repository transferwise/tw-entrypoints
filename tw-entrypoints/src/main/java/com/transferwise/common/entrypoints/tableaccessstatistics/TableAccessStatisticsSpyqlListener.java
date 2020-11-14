package com.transferwise.common.entrypoints.tableaccessstatistics;

import static com.transferwise.common.entrypoints.EntryPointsMetrics.METRIC_PREFIX_ENTRYPOINTS;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.transferwise.common.context.TwContext;
import com.transferwise.common.context.TwContextMetricsTemplate;
import com.transferwise.common.entrypoints.EntryPointsMetrics;
import com.transferwise.common.spyql.event.GetConnectionEvent;
import com.transferwise.common.spyql.event.StatementExecuteEvent;
import com.transferwise.common.spyql.event.StatementExecuteFailureEvent;
import com.transferwise.common.spyql.listener.SpyqlConnectionListener;
import com.transferwise.common.spyql.listener.SpyqlDataSourceListener;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class TableAccessStatisticsSpyqlListener implements SpyqlDataSourceListener {

  public static final String METRIC_PREFIX_ENTRYPOINTS_TAS = METRIC_PREFIX_ENTRYPOINTS + "Tas.";
  public static final String METRIC_PREFIX_SQL_PARSER_RESULT_CACHE = METRIC_PREFIX_ENTRYPOINTS_TAS + "SqlParseResultsCache.";

  public static final String METRIC_FAILED_PARSES = METRIC_PREFIX_ENTRYPOINTS_TAS + "FailedParses";
  public static final String METRIC_FIRST_TABLE_ACCESS = METRIC_PREFIX_ENTRYPOINTS_TAS + "FirstTableAccess";
  public static final String METRIC_TABLE_ACCESS = METRIC_PREFIX_ENTRYPOINTS_TAS + "TableAccess";

  private static final long MIB = 1_000_000;

  private final MeterRegistry meterRegistry;
  private final String databaseName;

  final LoadingCache<String, SqlParseResult> sqlParseResultsCache;

  public TableAccessStatisticsSpyqlListener(MeterRegistry meterRegistry, Executor executor, String databaseName, long sqlParserCacheSizeMib) {
    this.databaseName = databaseName;
    this.meterRegistry = meterRegistry;
    meterRegistry.config().meterFilter(new TasMeterFilter());

    sqlParseResultsCache = Caffeine.newBuilder().maximumWeight(sqlParserCacheSizeMib * MIB).recordStats()
        .executor(executor)
        .weigher((String k, SqlParseResult v) -> k.length() * 2)
        .build(this::parseSql);

    Gauge.builder(METRIC_PREFIX_SQL_PARSER_RESULT_CACHE + "size", () -> sqlParseResultsCache.estimatedSize()).register(meterRegistry);
    Gauge.builder(METRIC_PREFIX_SQL_PARSER_RESULT_CACHE + "hitRatio", () -> sqlParseResultsCache.stats().hitRate()).register(meterRegistry);
    Gauge.builder(METRIC_PREFIX_SQL_PARSER_RESULT_CACHE + "hitCount", () -> sqlParseResultsCache.stats().hitCount()).register(meterRegistry);
  }

  @Override
  public SpyqlConnectionListener onGetConnection(GetConnectionEvent event) {
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
      meterRegistry.counter(METRIC_FAILED_PARSES, EntryPointsMetrics.TAG_DATABASE, databaseName).increment();
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
      registerSql(event.getSql(), event.isInTransaction(), true, event.getExecutionTimeNs());
    }

    @Override
    public void onStatementExecuteFailure(StatementExecuteFailureEvent event) {
      registerSql(event.getSql(), event.getTransaction() != null, false, event.getExecutionTimeNs());
    }

    protected void registerSql(String sql, boolean isInTransaction, boolean succeeded, long executionTimeNs) {
      TwContext context = TwContext.current();
      Tag dbTag = Tag.of(EntryPointsMetrics.TAG_DATABASE, databaseName);
      Tag entryPointGroupTag = Tag.of(TwContextMetricsTemplate.TAG_EP_GROUP, context.getGroup());
      Tag entryPointNameTag = Tag.of(TwContextMetricsTemplate.TAG_EP_NAME, context.getName());
      Tag entryPointOwnerTag = Tag.of(TwContextMetricsTemplate.TAG_EP_OWNER, context.getOwner());
      Tag inTransactionTag = Tag.of("inTransaction", Boolean.toString(isInTransaction));
      Tag successTag = Tag.of("success", Boolean.toString(succeeded));

      SqlParseResult sqlParseResult = sqlParseResultsCache.get(sql);

      if (sqlParseResult == null) {
        // Already counted in failed parses.
        return;
      }

      sqlParseResult.operations.forEach((opName, op) -> {
        Tag operationTag = Tag.of("operation", opName);
        String firstTableName = null;
        for (String tableName : op.getTableNames()) {
          if (firstTableName == null) {
            firstTableName = tableName;
          }
          Tag tableTag = Tag.of("table", tableName);

          Tags tags = Tags.of(dbTag, entryPointGroupTag, entryPointNameTag, entryPointOwnerTag, inTransactionTag, operationTag, successTag, tableTag);
          meterRegistry.counter(METRIC_TABLE_ACCESS, tags).increment();
        }
        if (firstTableName != null) {
          Tag tableTag = Tag.of("table", firstTableName);
          Tags tags = Tags.of(dbTag, entryPointGroupTag, entryPointNameTag, entryPointOwnerTag, inTransactionTag, operationTag, successTag, tableTag);
          meterRegistry.timer(METRIC_FIRST_TABLE_ACCESS, tags).record(executionTimeNs, TimeUnit.NANOSECONDS);
        }
      });
    }
  }

  @Data
  @Accessors(chain = true)
  static class SqlParseResult {

    @NonNull
    private Map<String, SqlOperation> operations = new HashMap<>();

    @Data
    @Accessors(chain = true)
    static class SqlOperation {

      private Set<String> tableNames = new HashSet<>();
    }
  }
}
