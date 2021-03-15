package com.transferwise.common.entrypoints.databaseaccessstatistics;

import static com.transferwise.common.entrypoints.EntryPointsMetrics.METRIC_PREFIX_ENTRYPOINTS;

import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.baseutils.meters.cache.TagsSet;
import com.transferwise.common.context.TwContext;
import com.transferwise.common.context.TwContextExecutionInterceptor;
import com.transferwise.common.context.TwContextMetricsTemplate;
import com.transferwise.common.entrypoints.EntryPointsMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
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

  private final IMeterCache meterCache;

  public DatabaseAccessStatisticsEntryPointInterceptor(IMeterCache meterCache) {
    this.meterCache = meterCache;
    meterCache.getMeterRegistry().config().meterFilter(new DasMeterFilter());
  }

  @Override
  public <T> T intercept(TwContext context, Supplier<T> supplier) {
    Map<String, DatabaseAccessStatistics> dbDasMap = new HashMap<>();
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
    for (DatabaseAccessStatistics das : dbDasMap.values()) {
      TagsSet tagsSet = TagsSet.of(
          EntryPointsMetrics.TAG_DATABASE, das.getDatabaseName(),
          TwContextMetricsTemplate.TAG_EP_GROUP, context.getGroup(),
          TwContextMetricsTemplate.TAG_EP_NAME, context.getName(),
          TwContextMetricsTemplate.TAG_EP_OWNER, context.getOwner());

      final long commitsCount = das.getCommitsCount();
      final long rollbacksCount = das.getRollbacksCount();
      final long nonTransactionalQueriesCount = das.getNonTransactionalQueriesCount();
      final long transactionalQueriesCount = das.getTransactionalQueriesCount();
      final long affectedRowsCount = das.getAffectedRowsCount();
      final long timeTakenInDatabaseNs = das.getTimeTakenInDatabaseNs();
      final long fetchedRowsCount = das.getFetchedRowsCount();

      CallMeters meters = meterCache.metersContainer(METRIC_PREFIX_DAS + "callMetrics", tagsSet, () -> {
        CallMeters result = new CallMeters();
        result.commits = meterCache.summary(METRIC_REGISTERED_COMMITS, tagsSet);
        result.rollbacks = meterCache.summary(METRIC_REGISTERED_ROLLBACKS, tagsSet);
        result.nonTransactionalQueries = meterCache.summary(METRIC_REGISTERED_NT_QUERIES, tagsSet);
        result.transactionalQueries = meterCache.summary(METRIC_REGISTERED_T_QUERIES, tagsSet);
        result.maxConcurrentConnections = meterCache.summary(METRIC_REGISTERED_MAX_CONCURRENT_CONNECTIONS, tagsSet);
        result.remainingOpenConnections = meterCache.summary(METRIC_REGISTERED_REMAINING_OPEN_CONNECTIONS, tagsSet);
        result.emptyTransactions = meterCache.summary(METRIC_REGISTERED_EMPTY_TRANSACTIONS, tagsSet);
        result.affectedRows = meterCache.summary(METRIC_REGISTERED_AFFECTED_ROWS, tagsSet);
        result.fetchedRows = meterCache.summary(METRIC_REGISTERED_FETCHED_ROWS, tagsSet);
        result.timeTakenNs = meterCache.timer(METRIC_REGISTERED_TIME_TAKEN, tagsSet);
        return result;
      });

      meters.commits.record(commitsCount);
      meters.rollbacks.record(rollbacksCount);
      meters.nonTransactionalQueries.record(nonTransactionalQueriesCount);
      meters.transactionalQueries.record(transactionalQueriesCount);
      meters.maxConcurrentConnections.record(das.getMaxConnectionsCount());
      meters.remainingOpenConnections.record(das.getCurrentConnectionsCount());
      meters.emptyTransactions.record(das.getEmtpyTransactionsCount());
      meters.affectedRows.record(das.getAffectedRowsCount());
      meters.fetchedRows.record(fetchedRowsCount);
      meters.timeTakenNs.record(timeTakenInDatabaseNs, TimeUnit.NANOSECONDS);

      if (log.isDebugEnabled()) {
        log.debug(
            "Entry Point '" + context.getName() + "': commits=" + commitsCount + "; rollbacks=" + rollbacksCount + "; NT Queries="
                + nonTransactionalQueriesCount
                + "; T Queries=" + transactionalQueriesCount + "; TimeTakenMs=" + (timeTakenInDatabaseNs / 1000_000) + "; affectedRows="
                + affectedRowsCount + "; fetchedRows=" + fetchedRowsCount);
      }
    }
  }

  private static class CallMeters {

    private DistributionSummary commits;
    private DistributionSummary rollbacks;
    private DistributionSummary nonTransactionalQueries;
    private DistributionSummary transactionalQueries;
    private DistributionSummary maxConcurrentConnections;
    private DistributionSummary remainingOpenConnections;
    private DistributionSummary emptyTransactions;
    private DistributionSummary affectedRows;
    private DistributionSummary fetchedRows;
    private Timer timeTakenNs;
  }

  private void registerUnknownCalls() {
    for (DatabaseAccessStatistics das : DatabaseAccessStatistics.unknownContextDbDasMap.values()) {
      final long commits = das.getAndResetCommitsCount();
      final long rollbacks = das.getAndResetRollbacksCount();
      final long nonTransactionalQueries = das.getAndResetNonTransactionalQueriesCount();
      final long transactionalQueries = das.getAndResetTransactionalQueriesCount();
      final long timeTakenNs = das.getAndResetTimeTakenInDatabaseNs();
      final long affectedRows = das.getAndResetAffectedRowsCount();
      final long emptyTransactions = das.getAndResetEmptyTransactionsCount();
      final long fetchedRows = das.getAndResetFetchedRowsCount();

      TagsSet tagsSet = das.getTagsSet();
      UnknownCallMeters meters = meterCache.metersContainer(METRIC_PREFIX_DAS + "unknownCallMetrics", tagsSet, () -> {
        UnknownCallMeters result = new UnknownCallMeters();
        result.commits = meterCache.counter(METRIC_UNREGISTERED_COMMITS, tagsSet);
        result.rollbacks = meterCache.counter(METRIC_UNREGISTERED_ROLLBACKS, tagsSet);
        result.nonTransactionalQueries = meterCache.counter(METRIC_UNREGISTERED_NT_QUERIES, tagsSet);
        result.transactionalQueries = meterCache.counter(METRIC_UNREGISTERED_T_QUERIES, tagsSet);
        result.timeTakenNs = meterCache.counter(METRIC_UNREGISTERED_TIME_TAKEN_NS, tagsSet);
        result.emptyTransactions = meterCache.counter(METRIC_UNREGISTERED_EMPTY_TRANSACTIONS, tagsSet);
        result.affectedRows = meterCache.counter(METRIC_UNREGISTERED_AFFECTED_ROWS, tagsSet);
        result.fetchedRows = meterCache.counter(METRIC_UNREGISTERED_FETCHED_ROWS, tagsSet);
        return result;
      });

      meters.commits.increment(commits);
      meters.rollbacks.increment(rollbacks);
      meters.nonTransactionalQueries.increment(nonTransactionalQueries);
      meters.transactionalQueries.increment(transactionalQueries);
      meters.timeTakenNs.increment(timeTakenNs);
      meters.emptyTransactions.increment(emptyTransactions);
      meters.affectedRows.increment(affectedRows);
      meters.fetchedRows.increment(fetchedRows);
    }
  }

  private static class UnknownCallMeters {

    private Counter commits;
    private Counter rollbacks;
    private Counter nonTransactionalQueries;
    private Counter transactionalQueries;
    private Counter timeTakenNs;
    private Counter emptyTransactions;
    private Counter affectedRows;
    private Counter fetchedRows;
  }

  @Override
  public boolean applies(TwContext context) {
    return context.isNewEntryPoint();
  }
}
