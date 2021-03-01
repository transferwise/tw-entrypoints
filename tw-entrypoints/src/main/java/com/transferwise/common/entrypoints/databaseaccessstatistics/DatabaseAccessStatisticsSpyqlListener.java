package com.transferwise.common.entrypoints.databaseaccessstatistics;

import static com.transferwise.common.context.TwContext.NAME_KEY;

import com.transferwise.common.context.TwContext;
import com.transferwise.common.spyql.event.ConnectionCloseEvent;
import com.transferwise.common.spyql.event.ConnectionCloseFailureEvent;
import com.transferwise.common.spyql.event.GetConnectionEvent;
import com.transferwise.common.spyql.event.ResultSetNextRowsEvent;
import com.transferwise.common.spyql.event.StatementExecuteEvent;
import com.transferwise.common.spyql.event.StatementExecuteFailureEvent;
import com.transferwise.common.spyql.event.TransactionBeginEvent;
import com.transferwise.common.spyql.event.TransactionCommitEvent;
import com.transferwise.common.spyql.event.TransactionCommitFailureEvent;
import com.transferwise.common.spyql.event.TransactionRollbackEvent;
import com.transferwise.common.spyql.event.TransactionRollbackFailureEvent;
import com.transferwise.common.spyql.listener.SpyqlConnectionListener;
import com.transferwise.common.spyql.listener.SpyqlDataSourceListener;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DatabaseAccessStatisticsSpyqlListener implements SpyqlDataSourceListener {

  private final String databaseName;

  /**
   * When enabled, will log an error, when database queries are done from outside of entrypoints.
   *
   * <p>Useful for validating libraries and improving application quality.
   *
   * <p>Mostly meant to be enabled from test suites.
   */
  @Setter
  private boolean strictMode;

  public DatabaseAccessStatisticsSpyqlListener(String databaseName) {
    this(databaseName, false);
  }

  public DatabaseAccessStatisticsSpyqlListener(String databaseName, boolean strictMode) {
    this.databaseName = databaseName;
    this.strictMode = strictMode;
  }

  @Override
  public SpyqlConnectionListener onGetConnection(GetConnectionEvent event) {
    DatabaseAccessStatistics.get(TwContext.current(), databaseName).registerConnectionOpened();

    return new ConnectionListener();
  }

  class ConnectionListener implements SpyqlConnectionListener {

    private TransactionBeginEvent transactionBeginEvent;

    @Override
    public void onTransactionBegin(TransactionBeginEvent event) {
      this.transactionBeginEvent = event;
    }

    @Override
    public void onTransactionCommit(TransactionCommitEvent event) {
      currentDas().registerCommit(event.getExecutionTimeNs());
      registerEmptyTransaction();
    }

    @Override
    public void onTransactionCommitFailure(TransactionCommitFailureEvent event) {
      currentDas().registerDatabaseAction(event.getExecutionTimeNs());
      registerEmptyTransaction();
    }

    @Override
    public void onTransactionRollback(TransactionRollbackEvent event) {
      currentDas().registerRollback(event.getExecutionTimeNs());
      registerEmptyTransaction();
    }

    @Override
    public void onTransactionRollbackFailure(TransactionRollbackFailureEvent event) {
      currentDas().registerDatabaseAction(event.getExecutionTimeNs());
      registerEmptyTransaction();
    }

    @Override
    public void onStatementExecute(StatementExecuteEvent event) {
      TwContext twContext = TwContext.current();
      if (strictMode && twContext.get(NAME_KEY) == null) {
        RuntimeException e = new RuntimeException("Statement executed outside of an entrypoint.");
        log.error(e.getMessage(), e);
      }

      DatabaseAccessStatistics das = currentDas();
      if (das.isLogSql()) {
        Throwable t = das.isLogSqlStacktrace() ? new RuntimeException("SQL stack") : null;
        if (event.isInTransaction()) {
          log.info("TQ: " + event.getSql(), t);
        } else {
          log.info("NTQ:" + event.getSql(), t);
        }
      }
      das.registerQuery(event);
    }

    @Override
    public void onStatementExecuteFailure(StatementExecuteFailureEvent event) {
      currentDas().registerDatabaseAction(event.getExecutionTimeNs());
    }

    @Override
    public void onConnectionClose(ConnectionCloseEvent event) {
      currentDas().registerConnectionClosed(event.getExecutionTimeNs());
    }

    @Override
    public void onConnectionCloseFailure(ConnectionCloseFailureEvent event) {
      currentDas().registerDatabaseAction(event.getExecutionTimeNs());
    }

    @Override
    public void onResultSetNextRecords(ResultSetNextRowsEvent event) {
      currentDas().registerRowsFetch(event.getRowsCount());
    }

    private DatabaseAccessStatistics currentDas() {
      return currentDas(TwContext.current());
    }

    private DatabaseAccessStatistics currentDas(TwContext twContext) {
      return DatabaseAccessStatistics.get(twContext, databaseName);
    }

    private void registerEmptyTransaction() {
      if (transactionBeginEvent == null || transactionBeginEvent.getTransaction().isEmpty()) {
        currentDas().registerEmptyTransaction();
      }
    }
  }
}
