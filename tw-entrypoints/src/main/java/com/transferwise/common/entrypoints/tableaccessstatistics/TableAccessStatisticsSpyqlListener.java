package com.transferwise.common.entrypoints.tableaccessstatistics;

import static com.transferwise.common.entrypoints.EntryPointsMetrics.METRIC_PREFIX_ENTRYPOINTS;
import static com.transferwise.common.entrypoints.EntryPointsMetrics.TAG_IN_TRANSACTION;
import static com.transferwise.common.entrypoints.EntryPointsMetrics.TAG_OPERATION;
import static com.transferwise.common.entrypoints.EntryPointsMetrics.TAG_SUCCESS;
import static com.transferwise.common.entrypoints.EntryPointsMetrics.TAG_TABLE;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.baseutils.meters.cache.TagsSet;
import com.transferwise.common.context.TwContext;
import com.transferwise.common.context.TwContextMetricsTemplate;
import com.transferwise.common.entrypoints.EntryPointsMetrics;
import com.transferwise.common.entrypoints.tableaccessstatistics.TableAccessStatisticsSpyqlListener.SqlParseResult.SqlOperation;
import com.transferwise.common.spyql.event.GetConnectionEvent;
import com.transferwise.common.spyql.event.StatementExecuteEvent;
import com.transferwise.common.spyql.event.StatementExecuteFailureEvent;
import com.transferwise.common.spyql.listener.SpyqlConnectionListener;
import com.transferwise.common.spyql.listener.SpyqlDataSourceListener;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
  public static final String METRIC_SQL_PARSER_RESULT_CACHE_HIT_COUNT = METRIC_PREFIX_SQL_PARSER_RESULT_CACHE + "hitCount";
  public static final String METRIC_SQL_PARSER_RESULT_CACHE_HIT_RATIO = METRIC_PREFIX_SQL_PARSER_RESULT_CACHE + "hitRatio";
  public static final String METRIC_SQL_PARSER_RESULT_CACHE_SIZE = METRIC_PREFIX_SQL_PARSER_RESULT_CACHE + "size";

  public static final String METRIC_FAILED_PARSES = METRIC_PREFIX_ENTRYPOINTS_TAS + "FailedParses";
  public static final String METRIC_FIRST_TABLE_ACCESS = METRIC_PREFIX_ENTRYPOINTS_TAS + "FirstTableAccess";
  public static final String METRIC_TABLE_ACCESS = METRIC_PREFIX_ENTRYPOINTS_TAS + "TableAccess";

  private static final long MIB = 1_000_000;

  private static final Tag TAG_IN_TRANSACTION_TRUE = Tag.of(TAG_IN_TRANSACTION, "true");
  private static final Tag TAG_IN_TRANSACTION_FALSE = Tag.of(TAG_IN_TRANSACTION, "false");

  private static final Tag TAG_SUCCESS_TRUE = Tag.of(TAG_SUCCESS, "true");
  private static final Tag TAG_SUCCESS_FALSE = Tag.of(TAG_SUCCESS, "false");

  private final MeterRegistry meterRegistry;
  private final String databaseName;
  private final Tag dbTag;

  private final IMeterCache meterCache;

  final LoadingCache<String, SqlParseResult> sqlParseResultsCache;

  public TableAccessStatisticsSpyqlListener(MeterRegistry meterRegistry, IMeterCache meterCache, Executor executor, String databaseName,
      long sqlParserCacheSizeMib) {
    this.databaseName = databaseName;
    this.dbTag = Tag.of(EntryPointsMetrics.TAG_DATABASE, databaseName);
    this.meterRegistry = meterRegistry;
    this.meterCache = meterCache;
    meterRegistry.config().meterFilter(new TasMeterFilter());

    sqlParseResultsCache = Caffeine.newBuilder().maximumWeight(sqlParserCacheSizeMib * MIB).recordStats()
        .executor(executor)
        .weigher((String k, SqlParseResult v) -> k.length() * 2)
        .build(this::parseSql);

    Gauge.builder(METRIC_SQL_PARSER_RESULT_CACHE_SIZE, sqlParseResultsCache::estimatedSize).register(meterRegistry);
    Gauge.builder(METRIC_SQL_PARSER_RESULT_CACHE_HIT_RATIO, () -> sqlParseResultsCache.stats().hitRate()).register(meterRegistry);
    Gauge.builder(METRIC_SQL_PARSER_RESULT_CACHE_HIT_COUNT, () -> sqlParseResultsCache.stats().hitCount()).register(meterRegistry);
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
        // Intern() makes later equal checks much faster.
        String opName = getOperationName(stmt).intern();
        CustomTablesNamesFinder tablesNamesFinder = new CustomTablesNamesFinder();
        List<String> tableNames = tablesNamesFinder.getTableList(stmt);

        SqlParseResult.SqlOperation sqlOp = result
            .getOperations()
            .computeIfAbsent(opName, k -> new SqlParseResult.SqlOperation());
        for (String tableName : tableNames) {
          // Intern() makes later equal checks much faster.
          sqlOp.getTableNames().add(tableName.intern());
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
      Tag entryPointGroupTag = Tag.of(TwContextMetricsTemplate.TAG_EP_GROUP, context.getGroup());
      Tag entryPointNameTag = Tag.of(TwContextMetricsTemplate.TAG_EP_NAME, context.getName());
      Tag entryPointOwnerTag = Tag.of(TwContextMetricsTemplate.TAG_EP_OWNER, context.getOwner());
      Tag inTransactionTag = isInTransaction ? TAG_IN_TRANSACTION_TRUE : TAG_IN_TRANSACTION_FALSE;
      Tag successTag = succeeded ? TAG_SUCCESS_TRUE : TAG_SUCCESS_FALSE;

      SqlParseResult sqlParseResult = sqlParseResultsCache.get(sql);

      if (sqlParseResult == null) {
        // Already counted in failed parses.
        return;
      }

      for (Entry<String, SqlOperation> entry : sqlParseResult.operations.entrySet()) {
        String opName = entry.getKey();
        SqlOperation op = entry.getValue();
        Tag operationTag = Tag.of(TAG_OPERATION, opName);
        String firstTableName = null;
        for (String tableName : op.getTableNames()) {
          Tag tableTag = Tag.of(TAG_TABLE, tableName);
          TagsSet tagsSet = TagsSet.of(
              dbTag, entryPointGroupTag, entryPointNameTag, entryPointOwnerTag, inTransactionTag, operationTag, successTag, tableTag);

          if (firstTableName == null) {
            firstTableName = tableName;
            meterCache.timer(METRIC_FIRST_TABLE_ACCESS, tagsSet).record(executionTimeNs, TimeUnit.NANOSECONDS);
          }
          meterCache.counter(METRIC_TABLE_ACCESS, tagsSet).increment();
        }
      }
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
