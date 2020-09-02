package com.transferwise.common.entrypoints.databaseaccessstatistics;

import static com.transferwise.common.entrypoints.EntryPointsMetricUtils.METRIC_PREFIX_ENTRYPOINTS;
import static com.transferwise.common.entrypoints.EntryPointsMetricUtils.summaryWithoutBuckets;
import static com.transferwise.common.entrypoints.EntryPointsMetricUtils.timerWithoutBuckets;

import com.transferwise.common.context.TwContext;
import com.transferwise.common.context.TwContextExecutionInterceptor;
import com.transferwise.common.context.TwContextMetricsTemplate;
import com.transferwise.common.entrypoints.EntryPointsMetricUtils;
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

  private final MeterRegistry meterRegistry;

  public DatabaseAccessStatisticsEntryPointInterceptor(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
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

  @SuppressWarnings("checkstyle:MagicNumber")
  private void registerCall(TwContext context, Map<String, DatabaseAccessStatistics> dbDasMap) {
    dbDasMap.forEach((db, das) -> {
      String baseName = METRIC_PREFIX_ENTRYPOINTS + "Das.Registered.";
      Tag dbTag = Tag.of(EntryPointsMetricUtils.TAG_DATABASE, das.getDatabaseName());
      Tag entryPointNameTag = Tag.of(TwContextMetricsTemplate.TAG_EP_NAME, context.getName());
      Tag entryPointGroupTag = Tag.of(TwContextMetricsTemplate.TAG_EP_GROUP, context.getGroup());
      Tag entryPointOwnerTag = Tag.of(TwContextMetricsTemplate.TAG_EP_GROUP, context.getGroup());

      Tags tags = Tags.of(dbTag, entryPointNameTag, entryPointGroupTag, entryPointOwnerTag);

      long commitsCount = das.getCommitsCount();
      long rollbacksCount = das.getRollbacksCount();
      long nonTransactionalQueriesCount = das.getNonTransactionalQueriesCount();
      long transactionalQueriesCount = das.getTransactionalQueriesCount();
      long affectedRowsCount = das.getAffectedRowsCount();
      long timeTakenInDatabaseNs = das.getTimeTakenInDatabaseNs();
      long fetchedRowsCount = das.getFetchedRowsCount();

      summaryWithoutBuckets(meterRegistry, baseName + "Commits", tags).record(commitsCount);
      summaryWithoutBuckets(meterRegistry, baseName + "Rollbacks", tags).record(rollbacksCount);
      summaryWithoutBuckets(meterRegistry, baseName + "NTQueries", tags).record(nonTransactionalQueriesCount);
      summaryWithoutBuckets(meterRegistry, baseName + "TQueries", tags).record(transactionalQueriesCount);
      summaryWithoutBuckets(meterRegistry, baseName + "MaxConcurrentConnections", tags).record(das.getMaxConnectionsCount());
      summaryWithoutBuckets(meterRegistry, baseName + "RemainingOpenConnections", tags).record(das.getCurrentConnectionsCount());
      summaryWithoutBuckets(meterRegistry, baseName + "EmptyTransactions", tags).record(das.getEmtpyTransactionsCount());
      summaryWithoutBuckets(meterRegistry, baseName + "AffectedRows", tags).record(das.getAffectedRowsCount());
      summaryWithoutBuckets(meterRegistry, baseName + "FetchedRows", tags).record(fetchedRowsCount);
      timerWithoutBuckets(meterRegistry, baseName + "TimeTaken", tags).record(timeTakenInDatabaseNs, TimeUnit.NANOSECONDS);

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
      Tag dbTag = Tag.of(EntryPointsMetricUtils.TAG_DATABASE, das.getDatabaseName());
      List<Tag> tags = Collections.singletonList(dbTag);

      long commits = das.getAndResetCommitsCount();
      long rollbacks = das.getAndResetRollbacksCount();
      long nonTransactionalQueries = das.getAndResetNonTransactionalQueriesCount();
      long transactionalQueries = das.getAndResetTransactionalQueriesCount();
      long timeTakenNs = das.getAndResetTimeTakenInDatabaseNs();
      long affectedRows = das.getAndResetAffectedRowsCount();
      long emptyTransactions = das.getAndResetEmptyTransactionsCount();
      long fetchedRows = das.getAndResetFetchedRowsCount();

      String baseName = METRIC_PREFIX_ENTRYPOINTS + "Das.Unknown.";

      meterRegistry.counter(baseName + "Commits", tags).increment(commits);
      meterRegistry.counter(baseName + "Rollbacks", tags).increment(rollbacks);
      meterRegistry.counter(baseName + "NTQueries", tags).increment(nonTransactionalQueries);
      meterRegistry.counter(baseName + "TQueries", tags).increment(transactionalQueries);
      meterRegistry.counter(baseName + "TimeTakenNs", tags).increment(timeTakenNs);
      meterRegistry.counter(baseName + "EmptyTransactions", tags).increment(emptyTransactions);
      meterRegistry.counter(baseName + "AffectedRows", tags).increment(affectedRows);
      meterRegistry.counter(baseName + "FetchedRows", tags).increment(fetchedRows);
    });
  }

  @Override
  public boolean applies(TwContext context) {
    return context.isNewEntryPoint();
  }
}
