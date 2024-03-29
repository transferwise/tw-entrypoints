package com.transferwise.common.entrypoints.transactionstatistics;

import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.entrypoints.BaseEntryPointsBeanProcessor;
import com.transferwise.common.spyql.SpyqlDataSource;
import org.springframework.beans.factory.BeanFactory;

public class TransactionStatisticsBeanPostProcessor extends BaseEntryPointsBeanProcessor {

  private final BeanFactory beanFactory;

  public TransactionStatisticsBeanPostProcessor(BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  protected void instrument(SpyqlDataSource spyqlDataSource, String databaseName) {
    boolean isAlreadyAttached = spyqlDataSource.getDataSourceListeners().stream().anyMatch(
        l -> l instanceof TransactionsStatisticsSpyqlListener);

    if (isAlreadyAttached) {
      return;
    }

    IMeterCache meterCache = beanFactory.getBean(IMeterCache.class);
    TransactionsStatisticsSpyqlListener listener = new TransactionsStatisticsSpyqlListener(meterCache, databaseName);
    spyqlDataSource.addListener(listener);
  }
}
