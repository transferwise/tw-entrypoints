package com.transferwise.common.entrypoints.databaseaccessstatistics;

import com.transferwise.common.context.TwContext;
import com.transferwise.common.spyql.event.StatementExecuteEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DatabaseAccessStatistics {

  public static final String TW_CONTEXT_KEY = "DatabaseAccessStatistics";

  public static final Map<String, DatabaseAccessStatistics> unknownContextDbDasMap = new ConcurrentHashMap<>();

  @SuppressWarnings("unused")
  public static DatabaseAccessStatistics get(String databaseName) {
    return get(TwContext.current(), databaseName);
  }

  public static DatabaseAccessStatistics get(@NonNull TwContext twContext, String databaseName) {
    Map<String, DatabaseAccessStatistics> map = twContext.get(TW_CONTEXT_KEY);
    if (map == null) {
      map = unknownContextDbDasMap;
    }
    return map.computeIfAbsent(databaseName, k -> new DatabaseAccessStatistics(databaseName));
  }

  private final AtomicLong commitsCount = new AtomicLong();
  private final AtomicLong rollbacksCount = new AtomicLong();
  private final AtomicLong nonTransactionalQueriesCount = new AtomicLong();
  private final AtomicLong transactionalQueriesCount = new AtomicLong();
  private final AtomicLong timeTakenInDatabaseNs = new AtomicLong();
  private final AtomicLong emptyTransactionsCount = new AtomicLong();
  private final AtomicLong affectedRowsCount = new AtomicLong();
  private final AtomicLong fetchedRowsCount = new AtomicLong();

  @Getter
  private long currentConnectionsCount = 0;
  @Getter
  private long maxConnectionsCount = 0;
  @Getter
  private final String databaseName;
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

  public long getNonTransactionalQueriesCount() {
    return nonTransactionalQueriesCount.get();
  }

  public long getAndResetNonTransactionalQueriesCount() {
    return nonTransactionalQueriesCount.getAndSet(0);
  }

  public void registerQuery(StatementExecuteEvent event) {
    if (event.isInTransaction()) {
      transactionalQueriesCount.incrementAndGet();
    } else {
      nonTransactionalQueriesCount.incrementAndGet();
    }
    timeTakenInDatabaseNs.addAndGet(event.getExecutionTimeNs());
    affectedRowsCount.addAndGet(event.getAffectedRowsCount());
  }

  public long getAffectedRowsCount() {
    return affectedRowsCount.get();
  }

  public long getAndResetAffectedRowsCount() {
    return affectedRowsCount.getAndSet(0);
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

  public void registerRowsFetch(long rowsCount) {
    fetchedRowsCount.addAndGet(rowsCount);
  }

  public long getFetchedRowsCount() {
    return fetchedRowsCount.get();
  }

  public long getAndResetFetchedRowsCount() {
    return fetchedRowsCount.getAndSet(0);
  }
}
