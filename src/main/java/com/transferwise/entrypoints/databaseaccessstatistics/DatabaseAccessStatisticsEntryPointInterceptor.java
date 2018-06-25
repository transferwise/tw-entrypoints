package com.transferwise.entrypoints.databaseaccessstatistics;

import com.transferwise.entrypoints.EntryPointContext;
import com.transferwise.entrypoints.EntryPointInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class DatabaseAccessStatisticsEntryPointInterceptor implements EntryPointInterceptor {
    private static Map<String, Boolean> registeredNames = new ConcurrentHashMap<>();
    private MeterRegistry meterRegistry;
    private int maxDistinctEntryPointsCount = 1000;
    private AtomicInteger registeredNamesCount;


    public DatabaseAccessStatisticsEntryPointInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        registeredNamesCount = meterRegistry.gauge("EntryPoints.RegistrationsCount", new AtomicInteger());
    }

    @Override
    public <T> T inEntryPointContext(EntryPointContext context, EntryPointContext unknownContext, Callable<T> callable) throws Exception {
        try {
            return callable.call();
        } finally {
            try {
                registerUnknownCalls(unknownContext);
                registerCall(context);
            } catch (Throwable t) {
                log.error(t.getMessage(), t);
            }
        }
    }

    private void registerCall(EntryPointContext context) {
        DatabaseAccessStatistics.getAll(context).forEach((das) -> {
            String name = normalizeName(context.getName());
            if (!registeredNames.containsKey(name)) {
                if (!registeredNames.containsKey(name)) {
                    // Safeguard to protect graphite
                    synchronized (registeredNames) {
                        if (registeredNames.size() >= maxDistinctEntryPointsCount) {
                            return;
                        }
                        registeredNames.put(name, Boolean.TRUE);
                        registeredNamesCount.set(registeredNames.size());
                        if (registeredNames.size() >= maxDistinctEntryPointsCount) {
                            log.error("Too many entry points detected, check for parameterized urls in: ");
                            registeredNames.forEach((k, v) -> {
                                log.info("Registered Entry Point: `" + k + "`");
                            });
                        }
                    }
                }
            }

            String baseName = "EntryPoints.Registered.";
            Tag dbTag = Tag.of("db", das.getDatabaseName());
            Tag entryPointNameTag = Tag.of("entryPointName", name);
            List<Tag> tags = Arrays.asList(dbTag, entryPointNameTag);

            long commitsCount = das.getCommitsCount();
            long rollbacksCount = das.getRollbacksCount();
            long nonTransactionalQueriesCount = das.getNonTransactionalQueriesCount();
            long transactionalQueriesCount = das.getTransactionalQueriesCount();

            meterRegistry.summary(baseName + "Commits", tags).record(commitsCount);
            meterRegistry.summary(baseName + "Rollbacks", tags).record(rollbacksCount);
            meterRegistry.summary(baseName + "NTQueries", tags).record(nonTransactionalQueriesCount);
            meterRegistry.summary(baseName + "TQueries", tags).record(transactionalQueriesCount);
            meterRegistry.summary(baseName + "MaxConcurrentConnections", tags).record(das.getMaxConnectionsCount());
            meterRegistry.summary(baseName + "RemainingOpenConnections", tags).record(das.getCurrentConnectionsCount());
            meterRegistry.timer(baseName + "TimeTaken", tags).record(das.getTimeTakenInDatabaseNs(), TimeUnit.NANOSECONDS);

            if (log.isDebugEnabled()) {
                log.debug("Entry Point '" + name + "': commits=" + commitsCount + "; rollbacks=" +
                    rollbacksCount + "; NT Queries=" + nonTransactionalQueriesCount + "; T Queries=" + transactionalQueriesCount + ".");
            }
        });
    }

    private void registerUnknownCalls(EntryPointContext unknownContext) {
        DatabaseAccessStatistics.getAll(unknownContext).forEach((das) -> {
            Tag dbTag = Tag.of("db", das.getDatabaseName());
            List<Tag> tags = Arrays.asList(dbTag);

            long commits = das.getAndResetCommitsCount();
            long rollbacks = das.getAndResetRollbacksCount();
            long ntQueries = das.getAndResetNonTransactionalQueriesCount();
            long tQueries = das.getAndResetTransactionalQueriesCount();
            long timeTakenNs = das.getAndResetTimeTakenInDatabaseNs();
            String baseName = "EntryPoints.Unknown.";

            meterRegistry.counter(baseName + "Commits", tags).increment(commits);
            meterRegistry.counter(baseName + "Rollbacks", tags).increment(rollbacks);
            meterRegistry.counter(baseName + "NTQueries", tags).increment(ntQueries);
            meterRegistry.counter(baseName + "TQueries", tags).increment(tQueries);
            meterRegistry.counter(baseName + "TimeTakenNs", tags).increment(timeTakenNs);
        });
    }

    private String normalizeName(String name) {
        return StringUtils.replaceChars(name, '.', '_');
    }
}
