package com.transferwise.common.entrypoints.databaseaccessstatistics;

import com.transferwise.common.baseutils.context.TwContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class DatabaseAccessStatistics {
    public static final String ATTRIBUTE_KEY = "DatabaseAccessStatistics";

    public static TwContext unknownContext = new TwContext(null);

    public static TwContext currentTwContextOrUnknown() {
        TwContext twContext = TwContext.current();

        if (twContext == null || twContext.getName() == null) {
            return unknownContext;
        }
        return twContext;
    }

    public static DatabaseAccessStatistics get(TwContext twContext, String databaseName) {
        if (twContext == null) {
            return null;
        }
        String key = (ATTRIBUTE_KEY + "_" + databaseName);
        DatabaseAccessStatistics das = twContext.get(key);
        if (das == null) {
            twContext.set(key, das = new DatabaseAccessStatistics(databaseName));
        }

        return das;
    }

    public static List<DatabaseAccessStatistics> getAll(TwContext twContext) {
        List<DatabaseAccessStatistics> result = new ArrayList<>();
        if (twContext == null) {
            return result;
        }

        twContext.getAttributes().forEach((k, v) -> {
            if (v instanceof DatabaseAccessStatistics) {
                result.add((DatabaseAccessStatistics) v);
            }
        });

        return result;
    }

    private final AtomicLong commitsCount = new AtomicLong();
    private final AtomicLong rollbacksCount = new AtomicLong();
    private final AtomicLong nonTransactionalQueriesCount = new AtomicLong();
    private final AtomicLong transactionalQueriesCount = new AtomicLong();
    private final AtomicLong timeTakenInDatabaseNs = new AtomicLong();
    private final AtomicLong emptyTransactionsCount = new AtomicLong();

    @Getter
    private long currentConnectionsCount = 0;
    @Getter
    private long maxConnectionsCount = 0;
    @Getter
    private String databaseName;
    @Setter
    @Getter
    private boolean logSql;
    @Setter
    @Getter
    private boolean logSqlStacktrace;

    public DatabaseAccessStatistics(String databaseName) {
        this.databaseName = databaseName;
    }

    public void registerCommit(long timeTakenNs) {
        commitsCount.incrementAndGet();
        timeTakenInDatabaseNs.addAndGet(timeTakenNs);
    }

    public void registerDatabaseAction(long timeTakenNs) {
        timeTakenInDatabaseNs.addAndGet(timeTakenNs);
    }

    public long getCommitsCount() {
        return commitsCount.get();
    }

    public long getAndResetCommitsCount() {
        return commitsCount.getAndSet(0);
    }

    public void registerRollback(long timeTakenNs) {
        rollbacksCount.incrementAndGet();
        timeTakenInDatabaseNs.addAndGet(timeTakenNs);
    }

    public long getRollbacksCount() {
        return rollbacksCount.get();
    }

    public long getAndResetRollbacksCount() {
        return rollbacksCount.getAndSet(0);
    }

    public void registerNonTransactionalQuery(long timeTakenNs) {
        nonTransactionalQueriesCount.incrementAndGet();
        timeTakenInDatabaseNs.addAndGet(timeTakenNs);
    }

    public long getNonTransactionalQueriesCount() {
        return nonTransactionalQueriesCount.get();
    }

    public long getAndResetNonTransactionalQueriesCount() {
        return nonTransactionalQueriesCount.getAndSet(0);
    }

    public void registerTransactionalQuery(long timeTakenNs) {
        transactionalQueriesCount.incrementAndGet();
        timeTakenInDatabaseNs.addAndGet(timeTakenNs);
    }

    public long getTransactionalQueriesCount() {
        return transactionalQueriesCount.get();
    }

    public long getAndResetTransactionalQueriesCount() {
        return transactionalQueriesCount.getAndSet(0);
    }

    public void registerConnectionOpened() {
        currentConnectionsCount++;
        if (currentConnectionsCount > maxConnectionsCount) {
            maxConnectionsCount = currentConnectionsCount;
        }
    }

    public void registerConnectionClosed(long timeTakenNs) {
        currentConnectionsCount--;
        timeTakenInDatabaseNs.addAndGet(timeTakenNs);
    }

    public long getTimeTakenInDatabaseNs() {
        return timeTakenInDatabaseNs.get();
    }

    public long getAndResetTimeTakenInDatabaseNs() {
        return timeTakenInDatabaseNs.getAndSet(0);
    }

    public long getEmtpyTransactionsCount() {
        return emptyTransactionsCount.get();
    }

    public long getAndResetEmptyTransactionsCount() {
        return emptyTransactionsCount.getAndSet(0);
    }

    public void registerEmptyTransaction() {
        emptyTransactionsCount.incrementAndGet();
    }
}
