package com.transferwise.common.entrypoints.tableaccessstatistics;

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
import com.transferwise.common.entrypoints.EntryPointsProperties;
import com.transferwise.common.entrypoints.tableaccessstatistics.ParsedQuery.SqlOperation;
import com.transferwise.common.entrypoints.tableaccessstatistics.TasQueryParsingInterceptor.InterceptResult.Decision;
import com.transferwise.common.spyql.event.GetConnectionEvent;
import com.transferwise.common.spyql.event.StatementExecuteEvent;
import com.transferwise.common.spyql.event.StatementExecuteFailureEvent;
import com.transferwise.common.spyql.listener.SpyqlConnectionListener;
import com.transferwise.common.spyql.listener.SpyqlDataSourceListener;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.Statement;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class TableAccessStatisticsSpyqlListener implements SpyqlDataSourceListener {

  public static final String GAUGE_SQL_PARSER_RESULT_CACHE_HIT_COUNT = "EntryPoints_Tas_SqlParseResultsCache_hitCount";
  public static final String GAUGE_SQL_PARSER_RESULT_CACHE_MISS_COUNT = "EntryPoints_Tas_SqlParseResultsCache_missCount";
  public static final String GAUGE_SQL_PARSER_RESULT_CACHE_EVICT_COUNT = "EntryPoints_Tas_SqlParseResultsCache_evictCount";
  public static final String GAUGE_SQL_PARSER_RESULT_CACHE_HIT_RATIO = "EntryPoints_Tas_SqlParseResultsCache_hitRatio";
  public static final String GAUGE_SQL_PARSER_RESULT_CACHE_SIZE = "EntryPoints_Tas_SqlParseResultsCache_size";

  public static final String COUNTER_PARSES = "EntryPoints_Tas_Parses";
  public static final String COUNTER_FAILED_PARSES = "EntryPoints_Tas_FailedParses";
  public static final String COUNTER_SLOW_PARSES = "EntryPoints_Tas_SlowParses";
  public static final String COUNTER_UNCOUNTED_QUERIES = "EntryPoints_Tas_UncountedQueries";
  public static final String TIMER_FIRST_TABLE_ACCESS = "EntryPoints_Tas_FirstTableAccess";
  public static final String COUNTER_TABLE_ACCESS = "EntryPoints_Tas_TableAccess";

  private static final long MIB = 1_000_000;

  private static final Tag TAG_IN_TRANSACTION_TRUE = Tag.of(TAG_IN_TRANSACTION, "true");
  private static final Tag TAG_IN_TRANSACTION_FALSE = Tag.of(TAG_IN_TRANSACTION, "false");

  private static final Tag TAG_SUCCESS_TRUE = Tag.of(TAG_SUCCESS, "true");
  private static final Tag TAG_SUCCESS_FALSE = Tag.of(TAG_SUCCESS, "false");

  private final String databaseName;
  private final Tag dbTag;

  private final IMeterCache meterCache;

  final LoadingCache<String, ParsedQuery> sqlParseResultsCache;

  private final TasParsedQueryRegistry tasParsedQueryRegistry;
  private final SqlParser sqlParser;
  private final EntryPointsProperties entryPointsProperties;
  private final TasQueryParsingListener tasQueryParsingListener;
  private final TasQueryParsingInterceptor tasQueryParsingInterceptor;

  public TableAccessStatisticsSpyqlListener(IMeterCache meterCache, ExecutorService executorService,
      TasParsedQueryRegistry tasParsedQueryRegistry, String databaseName,
      EntryPointsProperties entryPointsProperties, TasQueryParsingListener tasQueryParsingListener,
      TasQueryParsingInterceptor tasQueryParsingInterceptor) {
    this.databaseName = databaseName;
    this.dbTag = Tag.of(EntryPointsMetrics.TAG_DATABASE, databaseName);
    this.meterCache = meterCache;
    this.tasParsedQueryRegistry = tasParsedQueryRegistry;
    this.sqlParser = new SqlParser(executorService);
    this.entryPointsProperties = entryPointsProperties;
    this.tasQueryParsingInterceptor = tasQueryParsingInterceptor;
    this.tasQueryParsingListener = tasQueryParsingListener;

    MeterRegistry meterRegistry = meterCache.getMeterRegistry();
    meterRegistry.config().meterFilter(new TasMeterFilter());

    sqlParseResultsCache = Caffeine.newBuilder().maximumWeight(entryPointsProperties.getTas().getSqlParser().getCacheSizeMib() * MIB).recordStats()
        .executor(executorService)
        .weigher((String k, ParsedQuery v) -> k.length() * 2)
        .build(sql -> parseSql(sql, TwContext.current()));

    new CaffeineCacheMetrics<>(sqlParseResultsCache, "ep-tas-parse-results-" + databaseName, Collections.emptyList()).bindTo(meterRegistry);

    Gauge.builder(GAUGE_SQL_PARSER_RESULT_CACHE_SIZE, sqlParseResultsCache::estimatedSize).tag("database", databaseName)
        .register(meterRegistry);
    Gauge.builder(GAUGE_SQL_PARSER_RESULT_CACHE_HIT_RATIO, () -> sqlParseResultsCache.stats().hitRate()).tag("database", databaseName)
        .register(meterRegistry);
    Gauge.builder(GAUGE_SQL_PARSER_RESULT_CACHE_HIT_COUNT, () -> sqlParseResultsCache.stats().hitCount()).tag("database", databaseName)
        .register(meterRegistry);
    Gauge.builder(GAUGE_SQL_PARSER_RESULT_CACHE_MISS_COUNT, () -> sqlParseResultsCache.stats().missCount()).tag("database", databaseName)
        .register(meterRegistry);
    Gauge.builder(GAUGE_SQL_PARSER_RESULT_CACHE_EVICT_COUNT, () -> sqlParseResultsCache.stats().evictionCount()).tag("database", databaseName)
        .register(meterRegistry);
  }

  @Override
  public SpyqlConnectionListener onGetConnection(GetConnectionEvent event) {
    return new ConnectionListener();
  }

  protected ParsedQuery parseSql(String sql, TwContext context) {
    var interceptResult = tasQueryParsingInterceptor.intercept(sql);
    if (interceptResult.getDecision() == Decision.CUSTOM_PARSED_QUERY) {
      return interceptResult.getParsedQuery();
    } else if (interceptResult.getDecision() == Decision.SKIP) {
      return new ParsedQuery();
    }

    ParsedQuery result = new ParsedQuery();
    long startTimeMs = System.currentTimeMillis();
    try {
      var stmts = sqlParser.parse(sql, entryPointsProperties.getTas().getSqlParser().getTimeout());

      for (Statement stmt : stmts.getStatements()) {
        // Intern() makes later equal checks much faster.
        String opName = getOperationName(stmt).intern();
        CustomTablesNamesFinder tablesNamesFinder = new CustomTablesNamesFinder();
        List<String> tableNames = null;
        try {
          tableNames = tablesNamesFinder.getTableList(stmt);
        } catch (UnsupportedOperationException e) {
          // Some type of statements do not support finding table names.
          // For example a statement 'SHOW FULL TABLES IN ...'.
          log.debug("Unsupported query '{}'.", sql, e);
        }

        ParsedQuery.SqlOperation sqlOp = result
            .getOperations()
            .computeIfAbsent(opName, k -> new ParsedQuery.SqlOperation());

        if (tableNames != null) {
          for (String tableName : tableNames) {
            // Intern() makes later equal checks much faster.
            sqlOp.getTableNames().add(tableName.intern());
          }
        }
      }

      meterCache.counter(COUNTER_PARSES, TagsSet.of(
          EntryPointsMetrics.TAG_DATABASE, databaseName,
          TwContextMetricsTemplate.TAG_EP_GROUP, context.getGroup(),
          TwContextMetricsTemplate.TAG_EP_NAME, context.getName(),
          TwContextMetricsTemplate.TAG_EP_OWNER, context.getOwner()
      )).increment();

      tasQueryParsingListener.parsingDone(sql, result, Duration.of(System.currentTimeMillis() - startTimeMs, ChronoUnit.MILLIS));
    } catch (Throwable t) {
      meterCache.counter(COUNTER_FAILED_PARSES, TagsSet.of(
          EntryPointsMetrics.TAG_DATABASE, databaseName,
          TwContextMetricsTemplate.TAG_EP_GROUP, context.getGroup(),
          TwContextMetricsTemplate.TAG_EP_NAME, context.getName(),
          TwContextMetricsTemplate.TAG_EP_OWNER, context.getOwner()
      )).increment();
      tasQueryParsingListener.parsingFailed(sql, Duration.of(System.currentTimeMillis() - startTimeMs, ChronoUnit.MILLIS), t);
    } finally {
      long durationMs = System.currentTimeMillis() - startTimeMs;
      if (durationMs > entryPointsProperties.getTas().getSqlParser().getParseDurationWarnThreshold().toMillis()) {
        meterCache.counter(COUNTER_SLOW_PARSES, TagsSet.of(
            EntryPointsMetrics.TAG_DATABASE, databaseName,
            TwContextMetricsTemplate.TAG_EP_GROUP, context.getGroup(),
            TwContextMetricsTemplate.TAG_EP_NAME, context.getName(),
            TwContextMetricsTemplate.TAG_EP_OWNER, context.getOwner()
        )).increment();
      }
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
      final Tag inTransactionTag = isInTransaction ? TAG_IN_TRANSACTION_TRUE : TAG_IN_TRANSACTION_FALSE;
      final Tag successTag = succeeded ? TAG_SUCCESS_TRUE : TAG_SUCCESS_FALSE;

      ParsedQuery parsedQuery = tasParsedQueryRegistry.get(sql);

      if (parsedQuery == null) {
        if (TasUtils.isQueryParsingEnabled(TwContext.current())) {
          parsedQuery = sqlParseResultsCache.get(sql, sqlForCache -> parseSql(sqlForCache, context));
        } else {
          var interceptResult = tasQueryParsingInterceptor.intercept(sql);
          if (interceptResult.getDecision() == Decision.CUSTOM_PARSED_QUERY) {
            parsedQuery = interceptResult.getParsedQuery();
          }
        }
      }

      if (parsedQuery == null || parsedQuery.getOperations().isEmpty()) {
        meterCache.counter(COUNTER_UNCOUNTED_QUERIES, TagsSet.of(
            EntryPointsMetrics.TAG_DATABASE, databaseName,
            TwContextMetricsTemplate.TAG_EP_GROUP, context.getGroup(),
            TwContextMetricsTemplate.TAG_EP_NAME, context.getName(),
            TwContextMetricsTemplate.TAG_EP_OWNER, context.getOwner()
        )).increment();

        return;
      }

      for (Entry<String, SqlOperation> entry : parsedQuery.getOperations().entrySet()) {
        String opName = entry.getKey();
        SqlOperation op = entry.getValue();
        if (op.getTableNames() != null) {
          String firstTableName = null;
          for (String tableName : op.getTableNames()) {
            TagsSet tagsSet = TagsSet.of(
                dbTag.getKey(), dbTag.getValue(),
                TwContextMetricsTemplate.TAG_EP_GROUP, context.getGroup(),
                TwContextMetricsTemplate.TAG_EP_NAME, context.getName(),
                TwContextMetricsTemplate.TAG_EP_OWNER, context.getOwner(),
                inTransactionTag.getKey(), inTransactionTag.getValue(),
                TAG_OPERATION, opName,
                successTag.getKey(), successTag.getValue(),
                TAG_TABLE, tableName);

            if (firstTableName == null) {
              firstTableName = tableName;
              meterCache.timer(TIMER_FIRST_TABLE_ACCESS, tagsSet).record(executionTimeNs, TimeUnit.NANOSECONDS);
            }
            meterCache.counter(COUNTER_TABLE_ACCESS, tagsSet).increment();
          }
        }
      }
    }
  }
}
