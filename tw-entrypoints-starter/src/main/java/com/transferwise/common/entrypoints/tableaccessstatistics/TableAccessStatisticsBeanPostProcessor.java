package com.transferwise.common.entrypoints.tableaccessstatistics;

import com.transferwise.common.baseutils.concurrency.IExecutorServicesProvider;
import com.transferwise.common.baseutils.concurrency.ThreadNamingExecutorServiceWrapper;
import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.entrypoints.SpyqlInstrumentingDataSourceBeanProcessor;
import com.transferwise.common.spyql.SpyqlDataSource;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;

public class TableAccessStatisticsBeanPostProcessor extends SpyqlInstrumentingDataSourceBeanProcessor {

  @Value("${tw-entrypoints.tas.sql-parser.cache-size-mib:50}")
  private int sqlParserCacheSizeMib;

  private final BeanFactory beanFactory;

  public TableAccessStatisticsBeanPostProcessor(BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  protected void instrument(SpyqlDataSource spyqlDataSource, String databaseName) {
    boolean isAlreadyAttached = spyqlDataSource.getDataSourceListeners().stream().anyMatch(
        l -> l instanceof TableAccessStatisticsSpyqlListener);

    if (isAlreadyAttached) {
      return;
    }

    IMeterCache meterCache = beanFactory.getBean(IMeterCache.class);
    ExecutorService executorService = new ThreadNamingExecutorServiceWrapper("eptas", beanFactory
        .getBean(IExecutorServicesProvider.class).getGlobalExecutorService());

    spyqlDataSource.addListener(
        new TableAccessStatisticsSpyqlListener(meterCache, executorService, databaseName, sqlParserCacheSizeMib));
  }
}
