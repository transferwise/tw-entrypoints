package com.transferwise.common.entrypoints.databaseaccessstatistics;

import com.transferwise.common.baseutils.context.TwContext;
import com.transferwise.common.entrypoints.EntryPointsMetricUtils;
import com.transferwise.common.entrypoints.IEntryPointInterceptor;
import com.transferwise.common.entrypoints.IEntryPointsRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.transferwise.common.entrypoints.EntryPointsMetricUtils.METRIC_PREFIX_ENTRYPOINTS;
import static com.transferwise.common.entrypoints.EntryPointsMetricUtils.summaryWithoutBuckets;
import static com.transferwise.common.entrypoints.EntryPointsMetricUtils.timerWithoutBuckets;

@Slf4j
/**
 * TODO: Add support to read only transactions. Also count how many non transactional selects and updates there were.
 *       This is not important on MySQL 5.6 though.
 */
public class DatabaseAccessStatisticsEntryPointInterceptor implements IEntryPointInterceptor {
    private final MeterRegistry meterRegistry;

    private IEntryPointsRegistry entryPointsRegistry;

    public DatabaseAccessStatisticsEntryPointInterceptor(MeterRegistry meterRegistry, IEntryPointsRegistry entryPointsRegistry) {
        this.meterRegistry = meterRegistry;
        this.entryPointsRegistry = entryPointsRegistry;
    }

    @Override
    public <T> T inEntryPointContext(Supplier<T> supplier) {
        try {
            return supplier.get();
        } finally {
            try {
                registerUnknownCalls(DatabaseAccessStatistics.unknownContext);
                registerCall(TwContext.current());
            } catch (Throwable t) {
                // TODO: Maybe should be throttled.
                log.error(t.getMessage(), t);
            }
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private void registerCall(TwContext context) {
        DatabaseAccessStatistics.getAll(context).forEach((das) -> {
            if (entryPointsRegistry.registerEntryPoint(context)) {
                //TODO: Should be renamed from EntryPoints to something referring to DatabaseAccessStatistics.
                String baseName = METRIC_PREFIX_ENTRYPOINTS + "Das.Registered.";
                Tag dbTag = Tag.of(EntryPointsMetricUtils.TAG_DATABASE, das.getDatabaseName());
                String name = EntryPointsMetricUtils.normalizeNameForMetric(context.getName());
                Tag entryPointNameTag = Tag.of(EntryPointsMetricUtils.TAG_ENTRYPOINT_NAME, name);
                String group = EntryPointsMetricUtils.normalizeNameForMetric(context.getGroup());
                Tag entryPointGroupTag = Tag.of(EntryPointsMetricUtils.TAG_ENTRYPOINT_GROUP, group);

                List<Tag> tags = Arrays.asList(dbTag, entryPointNameTag, entryPointGroupTag);

                long commitsCount = das.getCommitsCount();
                long rollbacksCount = das.getRollbacksCount();
                long nonTransactionalQueriesCount = das.getNonTransactionalQueriesCount();
                long transactionalQueriesCount = das.getTransactionalQueriesCount();

                summaryWithoutBuckets(meterRegistry, baseName + "Commits", tags).record(commitsCount);
                summaryWithoutBuckets(meterRegistry, baseName + "Rollbacks", tags).record(rollbacksCount);
                summaryWithoutBuckets(meterRegistry, baseName + "NTQueries", tags).record(nonTransactionalQueriesCount);
                summaryWithoutBuckets(meterRegistry, baseName + "TQueries", tags).record(transactionalQueriesCount);
                summaryWithoutBuckets(meterRegistry, baseName + "MaxConcurrentConnections", tags)
                    .record(das.getMaxConnectionsCount());
                summaryWithoutBuckets(meterRegistry, baseName + "RemainingOpenConnections", tags)
                    .record(das.getCurrentConnectionsCount());
                summaryWithoutBuckets(meterRegistry, baseName + "EmptyTransactions", tags)
                    .record(das.getEmtpyTransactionsCount());
                timerWithoutBuckets(meterRegistry, baseName + "TimeTaken", tags)
                    .record(das.getTimeTakenInDatabaseNs(), TimeUnit.NANOSECONDS);

                if (log.isDebugEnabled()) {
                    log.debug(
                        "Entry Point '" + name + "': commits=" + commitsCount + "; rollbacks=" + rollbacksCount + "; NT Queries=" + nonTransactionalQueriesCount + "; T Queries=" + transactionalQueriesCount + "; TimeTakenMs=" + (das
                            .getTimeTakenInDatabaseNs() / 1000_000) + "");
                }
            }
        });
    }

    private void registerUnknownCalls(TwContext unknownContext) {
        DatabaseAccessStatistics.getAll(unknownContext).forEach((das) -> {
            Tag dbTag = Tag.of(EntryPointsMetricUtils.TAG_DATABASE, das.getDatabaseName());
            List<Tag> tags = Collections.singletonList(dbTag);

            long commits = das.getAndResetCommitsCount();
            long rollbacks = das.getAndResetRollbacksCount();
            long ntQueries = das.getAndResetNonTransactionalQueriesCount();
            long tQueries = das.getAndResetTransactionalQueriesCount();
            long timeTakenNs = das.getAndResetTimeTakenInDatabaseNs();
            String baseName = METRIC_PREFIX_ENTRYPOINTS + "Das.Unknown.";

            meterRegistry.counter(baseName + "Commits", tags).increment(commits);
            meterRegistry.counter(baseName + "Rollbacks", tags).increment(rollbacks);
            meterRegistry.counter(baseName + "NTQueries", tags).increment(ntQueries);
            meterRegistry.counter(baseName + "TQueries", tags).increment(tQueries);
            meterRegistry.counter(baseName + "TimeTakenNs", tags).increment(timeTakenNs);
            meterRegistry.counter(baseName + "EmptyTransactions", tags)
                         .increment(das.getAndResetEmptyTransactionsCount());
        });
    }
}
