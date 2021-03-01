package com.transferwise.common.entrypoints.transactionstatistics;

import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.entrypoints.SpyqlInstrumentingDataSourceBeanProcessor;
import com.transferwise.common.spyql.SpyqlDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.BeanFactory;

public class TransactionStatisticsBeanPostProcessor extends SpyqlInstrumentingDataSourceBeanProcessor {

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

    MeterRegistry meterRegistry = beanFactory.getBean(MeterRegistry.class);
    IMeterCache meterCache = beanFactory.getBean(IMeterCache.class);
    TransactionsStatisticsSpyqlListener listener = new TransactionsStatisticsSpyqlListener(meterRegistry, meterCache, databaseName);
    spyqlDataSource.addListener(listener);
  }
}
