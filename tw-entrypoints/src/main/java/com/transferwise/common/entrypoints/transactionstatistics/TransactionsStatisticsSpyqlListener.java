package com.transferwise.common.entrypoints.transactionstatistics;

import static com.transferwise.common.entrypoints.EntryPointsMetrics.TAG_ISOLATION_LEVEL;
import static com.transferwise.common.entrypoints.EntryPointsMetrics.TAG_READ_ONLY;
import static com.transferwise.common.entrypoints.EntryPointsMetrics.TAG_RESOLUTION;
import static com.transferwise.common.entrypoints.EntryPointsMetrics.TAG_RESOLUTION_SUCCESS;
import static com.transferwise.common.entrypoints.EntryPointsMetrics.TAG_TRANSACTION_NAME;

import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.baseutils.meters.cache.TagsSet;
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
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.sql.Connection;
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

  public static final String METRIC_COLLECTION_TRANSACTION_END = "database.transaction.end";

  private static final Tag TAG_READ_ONLY_TRUE = Tag.of(TAG_READ_ONLY, "true");
  private static final Tag TAG_READ_ONLY_FALSE = Tag.of(TAG_READ_ONLY, "false");

  private static final Tag TAG_RESOLUTION_SUCCESS_TRUE = Tag.of(TAG_RESOLUTION_SUCCESS, "true");
  private static final Tag TAG_RESOLUTION_SUCCESS_FALSE = Tag.of(TAG_RESOLUTION_SUCCESS, "false");

  private static final Tag TAG_RESOLUTION_COMMIT = Tag.of(TAG_RESOLUTION, "commit");
  private static final Tag TAG_RESOLUTION_ROLLBACK = Tag.of(TAG_RESOLUTION, "rollback");

  private static final Tag[] TAGS_ISOLATION = new Tag[10];

  static {
    for (int i = 0; i < 10; i++) {
      TAGS_ISOLATION[i] = Tag.of(TAG_ISOLATION_LEVEL, String.valueOf(i));
    }
  }

  private final IMeterCache meterCache;
  private final Tag dbTag;

  public TransactionsStatisticsSpyqlListener(IMeterCache meterCache, String databaseName) {
    this.dbTag = Tag.of(EntryPointsMetrics.TAG_DATABASE, databaseName);
    this.meterCache = meterCache;
    meterCache.getMeterRegistry().config().meterFilter(new TsMeterFilter());
  }

  @Override
  public SpyqlConnectionListener onGetConnection(GetConnectionEvent event) {
    return new ConnectionListener();
  }

  class ConnectionListener implements SpyqlConnectionListener {

    @Override
    public void onTransactionBegin(TransactionBeginEvent event) {
      SpyqlTransaction transaction = event.getTransaction();

      Tag entryPointNameTag = Tag.of(TwContextMetricsTemplate.TAG_EP_NAME, nullToUnknown(transaction.getDefinition().getEntryPointName()));
      Tag entryPointGroupTag = Tag.of(TwContextMetricsTemplate.TAG_EP_GROUP, nullToUnknown(transaction.getDefinition().getEntryPointGroup()));
      Tag entryPointOwnerTag = Tag.of(TwContextMetricsTemplate.TAG_EP_OWNER, nullToUnknown(transaction.getDefinition().getEntryPointOwner()));
      Tag nameTag = Tag.of(TAG_TRANSACTION_NAME, nullToUnknown(transaction.getDefinition().getName()));
      Tag readOnlyTag = Boolean.TRUE.equals(transaction.getDefinition().getReadOnly()) ? TAG_READ_ONLY_TRUE : TAG_READ_ONLY_FALSE;
      Tag isolationLevelTag = isolationLevelTag(transaction.getDefinition().getIsolationLevel());

      TagsSet tagsSet = TagsSet.of(dbTag, entryPointGroupTag, entryPointNameTag, entryPointOwnerTag, isolationLevelTag, nameTag, readOnlyTag);
      meterCache.counter(METRIC_TRANSACTION_START, tagsSet).increment();
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
      Tag entryPointNameTag = Tag.of(TwContextMetricsTemplate.TAG_EP_NAME, nullToUnknown(transaction.getDefinition().getEntryPointName()));
      Tag entryPointGroupTag = Tag.of(TwContextMetricsTemplate.TAG_EP_GROUP, nullToUnknown(transaction.getDefinition().getEntryPointGroup()));
      Tag entryPointOwnerTag = Tag.of(TwContextMetricsTemplate.TAG_EP_OWNER, nullToUnknown(transaction.getDefinition().getEntryPointOwner()));
      Tag nameTag = Tag.of(TAG_TRANSACTION_NAME, nullToUnknown(transaction.getDefinition().getName()));
      Tag isolationLevelTag = isolationLevelTag(transaction.getDefinition().getIsolationLevel());
      Tag readOnlyTag = Boolean.TRUE.equals(transaction.getDefinition().getReadOnly()) ? TAG_READ_ONLY_TRUE : TAG_READ_ONLY_FALSE;
      Tag failureTag = success ? TAG_RESOLUTION_SUCCESS_TRUE : TAG_RESOLUTION_SUCCESS_FALSE;
      Tag operationTag = commit ? TAG_RESOLUTION_COMMIT : TAG_RESOLUTION_ROLLBACK;

      TagsSet tagsSet = TagsSet
          .of(dbTag, entryPointGroupTag, entryPointNameTag, entryPointOwnerTag, failureTag, isolationLevelTag, nameTag, operationTag, readOnlyTag);

      TransactionMetrics metrics = meterCache.metersContainer(METRIC_COLLECTION_TRANSACTION_END, tagsSet, () -> {
        TransactionMetrics result = new TransactionMetrics();
        result.completion = meterCache.timer(METRIC_TRANSACTION_COMPLETION, tagsSet);
        result.finalization = meterCache.timer(METRIC_TRANSACTION_FINALIZATION, tagsSet);
        return result;
      });

      metrics.completion.record(Duration.between(transaction.getStartTime(), transaction.getEndTime()));
      metrics.finalization.record(finalizationTimeNs, TimeUnit.NANOSECONDS);
    }

    protected String nullToUnknown(String value) {
      return value == null ? "Unknown" : value;
    }

    protected Tag isolationLevelTag(Integer isolationLevel) {
      if (isolationLevel == null || isolationLevel < 0 || isolationLevel > 9) {
        return TAGS_ISOLATION[Connection.TRANSACTION_NONE];
      }
      return TAGS_ISOLATION[isolationLevel];
    }
  }

  private static class TransactionMetrics {

    private Timer completion;
    private Timer finalization;
  }

}
