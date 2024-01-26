package com.transferwise.common.entrypoints.databaseaccessstatistics;

import static com.transferwise.common.entrypoints.databaseaccessstatistics.DatabaseAccessStatisticsEntryPointInterceptor.METRIC_PREFIX_DAS;

import com.transferwise.common.baseutils.concurrency.IExecutorServicesProvider;
import com.transferwise.common.baseutils.concurrency.ScheduledTaskExecutor.TaskHandle;
import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.baseutils.meters.cache.TagsSet;
import com.transferwise.common.gracefulshutdown.GracefulShutdownStrategy;
import io.micrometer.core.instrument.Counter;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DasUnknownCallsCollector implements GracefulShutdownStrategy {

  public static final String COUNTER_UNREGISTERED_FETCHED_ROWS = "EntryPoints_Das_Unknown_FetchedRows";
  public static final String COUNTER_UNREGISTERED_AFFECTED_ROWS = "EntryPoints_Das_Unknown_AffectedRows";
  public static final String COUNTER_UNREGISTERED_EMPTY_TRANSACTIONS = "EntryPoints_Das_Unknown_EmptyTransactions";
  public static final String COUNTER_UNREGISTERED_TIME_TAKEN_NS = "EntryPoints_Das_Unknown_TimeTakenNs";
  public static final String COUNTER_UNREGISTERED_T_QUERIES = "EntryPoints_Das_Unknown_TQueries";
  public static final String COUNTER_UNREGISTERED_NT_QUERIES = "EntryPoints_Das_Unknown_NTQueries";
  public static final String COUNTER_UNREGISTERED_ROLLBACKS = "EntryPoints_Das_Unknown_Rollbacks";
  public static final String COUNTER_UNREGISTERED_COMMITS = "EntryPoints_Das_Unknown_Commits";

  private IMeterCache meterCache;
  private TaskHandle taskHandle;

  private long iterationsCount;

  public DasUnknownCallsCollector(IExecutorServicesProvider executorServicesProvider, IMeterCache meterCache) {
    this.meterCache = meterCache;

    taskHandle = executorServicesProvider.getGlobalScheduledTaskExecutor()
        .scheduleAtFixedInterval(() -> registerUnknownCalls(), Duration.ofSeconds(1), Duration.ofSeconds(1));

    log.info("Starting to collect DAS metrics for unknown calls.");
  }

  protected void registerUnknownCalls() {
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
      UnknownCallMeters meters = meterCache.metersContainer(METRIC_PREFIX_DAS + "unknownCallMetrics", tagsSet, (name, tags) -> {
        UnknownCallMeters result = new UnknownCallMeters();
        result.commits = meterCache.counter(COUNTER_UNREGISTERED_COMMITS, tags);
        result.rollbacks = meterCache.counter(COUNTER_UNREGISTERED_ROLLBACKS, tags);
        result.nonTransactionalQueries = meterCache.counter(COUNTER_UNREGISTERED_NT_QUERIES, tags);
        result.transactionalQueries = meterCache.counter(COUNTER_UNREGISTERED_T_QUERIES, tags);
        result.timeTakenNs = meterCache.counter(COUNTER_UNREGISTERED_TIME_TAKEN_NS, tags);
        result.emptyTransactions = meterCache.counter(COUNTER_UNREGISTERED_EMPTY_TRANSACTIONS, tags);
        result.affectedRows = meterCache.counter(COUNTER_UNREGISTERED_AFFECTED_ROWS, tagsSet);
        result.fetchedRows = meterCache.counter(COUNTER_UNREGISTERED_FETCHED_ROWS, tagsSet);
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

    iterationsCount++;
  }

  protected long getIterationsCount() {
    return iterationsCount;
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
  public boolean canShutdown() {
    return true;
  }

  @Override
  public void applicationTerminating() {
    log.info("Stopping collecting of DAS metrics for unknown calls.");
    taskHandle.stop();
  }
}
