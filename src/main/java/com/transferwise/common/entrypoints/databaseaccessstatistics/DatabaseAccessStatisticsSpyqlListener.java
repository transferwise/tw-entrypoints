package com.transferwise.common.entrypoints.databaseaccessstatistics;

import com.transferwise.common.entrypoints.EntryPoints;
import com.transferwise.common.spyql.event.*;
import com.transferwise.common.spyql.listener.SpyqlConnectionListener;
import com.transferwise.common.spyql.listener.SpyqlDataSourceListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DatabaseAccessStatisticsSpyqlListener implements SpyqlDataSourceListener {
    private final EntryPoints entryPoints;
    private String databaseName = "generic";

    public DatabaseAccessStatisticsSpyqlListener(EntryPoints entryPoints) {
        this.entryPoints = entryPoints;
    }

    public DatabaseAccessStatisticsSpyqlListener(EntryPoints entryPoints, String databaseName) {
        this(entryPoints);
        this.databaseName = databaseName;
    }

    @Override
    public SpyqlConnectionListener onGetConnection(GetConnectionEvent event) {
        DatabaseAccessStatistics databaseAccessStatistics = DatabaseAccessStatistics.get(entryPoints.currentContext(), databaseName);
        if (databaseAccessStatistics != null) {
            databaseAccessStatistics.registerConnectionOpened();
        }

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
            if (event.isInTransaction()) {
                if (currentDas().isLogSql()) {
                    log.info("TQ: " + event.getSql());
                }
                currentDas().registerTransactionalQuery(event.getExecutionTimeNs());
            } else {
                if (currentDas().isLogSql()) {
                    log.info("NTQ:" + event.getSql());
                }
                currentDas().registerNonTransactionalQuery(event.getExecutionTimeNs());
            }
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

        private DatabaseAccessStatistics currentDas() {
            return DatabaseAccessStatistics.get(entryPoints.currentContextOrUnknown(), databaseName);
        }

        private void registerEmptyTransaction() {
            if (transactionBeginEvent == null || transactionBeginEvent.isEmptyTransaction()) {
                currentDas().registerEmptyTransaction();
            }
        }
    }
}
