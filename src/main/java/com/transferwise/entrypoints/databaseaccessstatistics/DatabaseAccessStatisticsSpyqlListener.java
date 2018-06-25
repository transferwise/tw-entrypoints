package com.transferwise.entrypoints.databaseaccessstatistics;

import com.transferwise.entrypoints.EntryPoints;
import com.transferwise.spyql.event.*;
import com.transferwise.spyql.listener.SpyqlConnectionListener;
import com.transferwise.spyql.listener.SpyqlDataSourceListener;

public class DatabaseAccessStatisticsSpyqlListener implements SpyqlDataSourceListener {
    private EntryPoints entryPoints;
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
        @Override
        public void onTransactionCommit(TransactionCommitEvent event) {
            currentDas().registerCommit(event.getExecutionTimeNs());
        }

        @Override
        public void onTransactionCommitFailure(TransactionCommitFailureEvent event) {
            currentDas().registerDatabaseAction(event.getExecutionTimeNs());
        }

        @Override
        public void onTransactionRollback(TransactionRollbackEvent event) {
            currentDas().registerRollback(event.getExecutionTimeNs());
        }

        @Override
        public void onTransactionRollbackFailure(TransactionRollbackFailureEvent event) {
            currentDas().registerDatabaseAction(event.getExecutionTimeNs());
        }

        @Override
        public void onStatementExecute(StatementExecuteEvent event) {
            if (event.isInTransaction()) {
                currentDas().registerTransactionalQuery(event.getExecutionTimeNs());
            } else {
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
    }
}
