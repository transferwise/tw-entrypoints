package com.transferwise.common.entrypoints.transactionstatistics;

import com.transferwise.common.context.TwContextMetricsTemplate;
import com.transferwise.common.entrypoints.EntryPointsMetrics;
import com.transferwise.common.spyql.event.GetConnectionEvent;
import com.transferwise.common.spyql.event.SpyqlTransaction;
import com.transferwise.common.spyql.event.TransactionBeginEvent;
import com.transferwise.common.spyql.event.TransactionCommitEvent;
import com.transferwise.common.spyql.event.TransactionCommitFailureEvent;
import com.transferwise.common.spyql.event.TransactionRollbackEvent;
import com.transferwise.common.spyql.event.TransactionRollbackFailureEvent;
import com.transferwise.common.spyql.listener.SpyqlConnectionListener;
import com.transferwise.common.spyql.listener.SpyqlDataSourceListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransactionsStatisticsSpyqlListener implements SpyqlDataSourceListener {

  /**
   * How long did the whole transaction take.
   */
  public static final String METRIC_TRANSACTION_COMPLETION = "database.transaction.completion";

  public static final String METRIC_TRANSACTION_START = "database.transaction.start";

  /**
   * How long did only the commit/rollback operation take.
   */
  public static final String METRIC_TRANSACTION_FINALIZATION = "database.transaction.finalization";

  private final MeterRegistry meterRegistry;
  private final String databaseName;

  public TransactionsStatisticsSpyqlListener(MeterRegistry meterRegistry, String databaseName) {
    this.databaseName = databaseName;
    this.meterRegistry = meterRegistry;
    meterRegistry.config().meterFilter(new TsMeterFilter());
  }

  @Override
  public SpyqlConnectionListener onGetConnection(GetConnectionEvent event) {
    return new ConnectionListener();
  }

  class ConnectionListener implements SpyqlConnectionListener {

    @Override
    public void onTransactionBegin(TransactionBeginEvent event) {
      SpyqlTransaction transaction = event.getTransaction();

      Tag dbTag = Tag.of(EntryPointsMetrics.TAG_DATABASE, databaseName);
      Tag entryPointNameTag = Tag.of(TwContextMetricsTemplate.TAG_EP_NAME, nullToUnknown(transaction.getDefinition().getEntryPointName()));
      Tag entryPointGroupTag = Tag.of(TwContextMetricsTemplate.TAG_EP_GROUP, nullToUnknown(transaction.getDefinition().getEntryPointGroup()));
      Tag entryPointOwnerTag = Tag.of(TwContextMetricsTemplate.TAG_EP_OWNER, nullToUnknown(transaction.getDefinition().getEntryPointOwner()));
      Tag nameTag = Tag.of("transactionName", nullToUnknown(transaction.getDefinition().getName()));
      Tag readOnlyTag = Tag.of("readOnly", Boolean.toString(Boolean.TRUE.equals(transaction.getDefinition().getReadOnly())));
      Tag isolationLevelTag = Tag.of("isolationLevel", String.valueOf(transaction.getDefinition().getIsolationLevel()));

      meterRegistry.counter(METRIC_TRANSACTION_START, Tags.of(dbTag, entryPointGroupTag, entryPointNameTag, entryPointOwnerTag, isolationLevelTag,
          nameTag, readOnlyTag)).increment();
    }

    @Override
    public void onTransactionCommit(TransactionCommitEvent event) {
      registerTransactionEnd(event.getTransaction(), true, true, event.getExecutionTimeNs());
    }

    @Override
    public void onTransactionCommitFailure(TransactionCommitFailureEvent event) {
      registerTransactionEnd(event.getTransaction(), false, true, event.getExecutionTimeNs());
    }

    @Override
    public void onTransactionRollback(TransactionRollbackEvent event) {
      registerTransactionEnd(event.getTransaction(), true, false, event.getExecutionTimeNs());
    }

    @Override
    public void onTransactionRollbackFailure(TransactionRollbackFailureEvent event) {
      registerTransactionEnd(event.getTransaction(), false, false, event.getExecutionTimeNs());
    }

    protected void registerTransactionEnd(SpyqlTransaction transaction, boolean success, boolean commit, long finalizationTimeNs) {
      Tag dbTag = Tag.of(EntryPointsMetrics.TAG_DATABASE, databaseName);
      Tag entryPointNameTag = Tag.of(TwContextMetricsTemplate.TAG_EP_NAME, nullToUnknown(transaction.getDefinition().getEntryPointName()));
      Tag entryPointGroupTag = Tag.of(TwContextMetricsTemplate.TAG_EP_GROUP, nullToUnknown(transaction.getDefinition().getEntryPointGroup()));
      Tag entryPointOwnerTag = Tag.of(TwContextMetricsTemplate.TAG_EP_OWNER, nullToUnknown(transaction.getDefinition().getEntryPointOwner()));
      Tag nameTag = Tag.of("transactionName", nullToUnknown(transaction.getDefinition().getName()));
      Tag readOnlyTag = Tag.of("readOnly", Boolean.toString(Boolean.TRUE.equals(transaction.getDefinition().getReadOnly())));
      Tag isolationLevelTag = Tag.of("isolationLevel", String.valueOf(transaction.getDefinition().getIsolationLevel()));
      Tag failureTag = Tag.of("resolutionSuccess", Boolean.toString(success));
      Tag operationTag = Tag.of("resolution", commit ? "commit" : "rollback");

      Tags tags = Tags
          .of(dbTag, entryPointGroupTag, entryPointNameTag, entryPointOwnerTag, failureTag, isolationLevelTag, nameTag, operationTag, readOnlyTag);

      meterRegistry.timer(METRIC_TRANSACTION_COMPLETION, tags).record(Duration.between(transaction.getStartTime(), transaction.getEndTime()));

      meterRegistry.timer(METRIC_TRANSACTION_FINALIZATION, tags).record(finalizationTimeNs, TimeUnit.NANOSECONDS);
    }

    protected String nullToUnknown(String value) {
      return value == null ? "Unknown" : value;
    }
  }

}
