package com.transferwise.common.entrypoints.databaseaccessstatistics;

import static com.transferwise.common.entrypoints.EntryPointsMetrics.METRIC_PREFIX_ENTRYPOINTS;

import com.transferwise.common.context.TwContext;
import com.transferwise.common.context.TwContextExecutionInterceptor;
import com.transferwise.common.context.TwContextMetricsTemplate;
import com.transferwise.common.entrypoints.EntryPointsMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

//TODO: Add support to read only transactions. This is not important on MySQL 5.6 though.
@Slf4j
public class DatabaseAccessStatisticsEntryPointInterceptor implements TwContextExecutionInterceptor {

  public static final String METRIC_PREFIX_DAS = METRIC_PREFIX_ENTRYPOINTS + "Das.";

  public static final String METRIC_PREFIX_DAS_REGISTERED = METRIC_PREFIX_DAS + "Registered.";
  public static final String METRIC_REGISTERED_TIME_TAKEN = METRIC_PREFIX_DAS_REGISTERED + "TimeTaken";
  public static final String METRIC_REGISTERED_FETCHED_ROWS = METRIC_PREFIX_DAS_REGISTERED + "FetchedRows";
  public static final String METRIC_REGISTERED_AFFECTED_ROWS = METRIC_PREFIX_DAS_REGISTERED + "AffectedRows";
  public static final String METRIC_REGISTERED_EMPTY_TRANSACTIONS = METRIC_PREFIX_DAS_REGISTERED + "EmptyTransactions";
  public static final String METRIC_REGISTERED_REMAINING_OPEN_CONNECTIONS = METRIC_PREFIX_DAS_REGISTERED + "RemainingOpenConnections";
  public static final String METRIC_REGISTERED_MAX_CONCURRENT_CONNECTIONS = METRIC_PREFIX_DAS_REGISTERED + "MaxConcurrentConnections";
  public static final String METRIC_REGISTERED_T_QUERIES = METRIC_PREFIX_DAS_REGISTERED + "TQueries";
  public static final String METRIC_REGISTERED_NT_QUERIES = METRIC_PREFIX_DAS_REGISTERED + "NTQueries";
  public static final String METRIC_REGISTERED_ROLLBACKS = METRIC_PREFIX_DAS_REGISTERED + "Rollbacks";
  public static final String METRIC_REGISTERED_COMMITS = METRIC_PREFIX_DAS_REGISTERED + "Commits";

  public static final String METRIC_PREFIX_DAS_UNREGISTERED = METRIC_PREFIX_DAS + "Unknown.";
  public static final String METRIC_UNREGISTERED_FETCHED_ROWS = METRIC_PREFIX_DAS_UNREGISTERED + "FetchedRows";
  public static final String METRIC_UNREGISTERED_AFFECTED_ROWS = METRIC_PREFIX_DAS_UNREGISTERED + "AffectedRows";
  public static final String METRIC_UNREGISTERED_EMPTY_TRANSACTIONS = METRIC_PREFIX_DAS_UNREGISTERED + "EmptyTransactions";
  public static final String METRIC_UNREGISTERED_TIME_TAKEN_NS = METRIC_PREFIX_DAS_UNREGISTERED + "TimeTakenNs";
  public static final String METRIC_UNREGISTERED_T_QUERIES = METRIC_PREFIX_DAS_UNREGISTERED + "TQueries";
  public static final String METRIC_UNREGISTERED_NT_QUERIES = METRIC_PREFIX_DAS_UNREGISTERED + "NTQueries";
  public static final String METRIC_UNREGISTERED_ROLLBACKS = METRIC_PREFIX_DAS_UNREGISTERED + "Rollbacks";
  public static final String METRIC_UNREGISTERED_COMMITS = METRIC_PREFIX_DAS_UNREGISTERED + "Commits";

  private final MeterRegistry meterRegistry;

  public DatabaseAccessStatisticsEntryPointInterceptor(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    meterRegistry.config().meterFilter(new DasMeterFilter());
  }

  @Override
  public <T> T intercept(TwContext context, Supplier<T> supplier) {
    Map<String, DatabaseAccessStatistics> dbDasMap = new ConcurrentHashMap<>();
    try {
      context.put(DatabaseAccessStatistics.TW_CONTEXT_KEY, dbDasMap);

      return supplier.get();
    } finally {
      try {
        registerUnknownCalls();
        registerCall(context, dbDasMap);
      } catch (Throwable t) {
        // TODO: Maybe should be throttled.
        log.error(t.getMessage(), t);
      }
    }
  }

  private void registerCall(TwContext context, Map<String, DatabaseAccessStatistics> dbDasMap) {
    dbDasMap.forEach((db, das) -> {
      Tag dbTag = Tag.of(EntryPointsMetrics.TAG_DATABASE, das.getDatabaseName());
      Tag entryPointNameTag = Tag.of(TwContextMetricsTemplate.TAG_EP_NAME, context.getName());
      Tag entryPointGroupTag = Tag.of(TwContextMetricsTemplate.TAG_EP_GROUP, context.getGroup());
      Tag entryPointOwnerTag = Tag.of(TwContextMetricsTemplate.TAG_EP_GROUP, context.getOwner());

      Tags tags = Tags.of(dbTag, entryPointGroupTag, entryPointNameTag, entryPointOwnerTag);

      long commitsCount = das.getCommitsCount();
      long rollbacksCount = das.getRollbacksCount();
      long nonTransactionalQueriesCount = das.getNonTransactionalQueriesCount();
      long transactionalQueriesCount = das.getTransactionalQueriesCount();
      long affectedRowsCount = das.getAffectedRowsCount();
      long timeTakenInDatabaseNs = das.getTimeTakenInDatabaseNs();
      long fetchedRowsCount = das.getFetchedRowsCount();

      meterRegistry.summary(METRIC_REGISTERED_COMMITS, tags).record(commitsCount);
      meterRegistry.summary(METRIC_REGISTERED_ROLLBACKS, tags).record(rollbacksCount);
      meterRegistry.summary(METRIC_REGISTERED_NT_QUERIES, tags).record(nonTransactionalQueriesCount);
      meterRegistry.summary(METRIC_REGISTERED_T_QUERIES, tags).record(transactionalQueriesCount);
      meterRegistry.summary(METRIC_REGISTERED_MAX_CONCURRENT_CONNECTIONS, tags).record(das.getMaxConnectionsCount());
      meterRegistry.summary(METRIC_REGISTERED_REMAINING_OPEN_CONNECTIONS, tags).record(das.getCurrentConnectionsCount());
      meterRegistry.summary(METRIC_REGISTERED_EMPTY_TRANSACTIONS, tags).record(das.getEmtpyTransactionsCount());
      meterRegistry.summary(METRIC_REGISTERED_AFFECTED_ROWS, tags).record(das.getAffectedRowsCount());
      meterRegistry.summary(METRIC_REGISTERED_FETCHED_ROWS, tags).record(fetchedRowsCount);
      meterRegistry.timer(METRIC_REGISTERED_TIME_TAKEN, tags).record(timeTakenInDatabaseNs, TimeUnit.NANOSECONDS);

      if (log.isDebugEnabled()) {
        log.debug(
            "Entry Point '" + context.getName() + "': commits=" + commitsCount + "; rollbacks=" + rollbacksCount + "; NT Queries="
                + nonTransactionalQueriesCount
                + "; T Queries=" + transactionalQueriesCount + "; TimeTakenMs=" + (timeTakenInDatabaseNs / 1000_000) + "; affectedRows="
                + affectedRowsCount + "; fetchedRows=" + fetchedRowsCount);
      }
    });
  }

  private void registerUnknownCalls() {
    DatabaseAccessStatistics.unknownContextDbDasMap.forEach((db, das) -> {
      Tag dbTag = Tag.of(EntryPointsMetrics.TAG_DATABASE, das.getDatabaseName());
      List<Tag> tags = Collections.singletonList(dbTag);

      long commits = das.getAndResetCommitsCount();
      long rollbacks = das.getAndResetRollbacksCount();
      long nonTransactionalQueries = das.getAndResetNonTransactionalQueriesCount();
      long transactionalQueries = das.getAndResetTransactionalQueriesCount();
      long timeTakenNs = das.getAndResetTimeTakenInDatabaseNs();
      long affectedRows = das.getAndResetAffectedRowsCount();
      long emptyTransactions = das.getAndResetEmptyTransactionsCount();
      long fetchedRows = das.getAndResetFetchedRowsCount();

      meterRegistry.counter(METRIC_UNREGISTERED_COMMITS, tags).increment(commits);
      meterRegistry.counter(METRIC_UNREGISTERED_ROLLBACKS, tags).increment(rollbacks);
      meterRegistry.counter(METRIC_UNREGISTERED_NT_QUERIES, tags).increment(nonTransactionalQueries);
      meterRegistry.counter(METRIC_UNREGISTERED_T_QUERIES, tags).increment(transactionalQueries);
      meterRegistry.counter(METRIC_UNREGISTERED_TIME_TAKEN_NS, tags).increment(timeTakenNs);
      meterRegistry.counter(METRIC_UNREGISTERED_EMPTY_TRANSACTIONS, tags).increment(emptyTransactions);
      meterRegistry.counter(METRIC_UNREGISTERED_AFFECTED_ROWS, tags).increment(affectedRows);
      meterRegistry.counter(METRIC_UNREGISTERED_FETCHED_ROWS, tags).increment(fetchedRows);
    });
  }

  @Override
  public boolean applies(TwContext context) {
    return context.isNewEntryPoint();
  }
}
