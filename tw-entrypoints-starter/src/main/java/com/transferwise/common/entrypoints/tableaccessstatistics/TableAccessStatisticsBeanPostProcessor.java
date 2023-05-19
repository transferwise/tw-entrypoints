package com.transferwise.common.entrypoints.tableaccessstatistics;

import com.transferwise.common.baseutils.concurrency.IExecutorServicesProvider;
import com.transferwise.common.baseutils.concurrency.ThreadNamingExecutorServiceWrapper;
import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.entrypoints.BaseEntryPointsBeanProcessor;
import com.transferwise.common.entrypoints.EntryPointsProperties;
import com.transferwise.common.spyql.SpyqlDataSource;
import org.springframework.beans.factory.BeanFactory;

public class TableAccessStatisticsBeanPostProcessor extends BaseEntryPointsBeanProcessor {

  private final BeanFactory beanFactory;

  public TableAccessStatisticsBeanPostProcessor(BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  protected void instrument(SpyqlDataSource spyqlDataSource, String databaseName) {
    boolean isAlreadyAttached = spyqlDataSource.getDataSourceListeners().stream().anyMatch(
        TableAccessStatisticsSpyqlListener.class::isInstance);

    if (isAlreadyAttached) {
      return;
    }

    var entryPointsProperties = beanFactory.getBean(EntryPointsProperties.class);
    var meterCache = beanFactory.getBean(IMeterCache.class);
    var executorService = new ThreadNamingExecutorServiceWrapper("eptas", beanFactory
        .getBean(IExecutorServicesProvider.class).getGlobalExecutorService());

    var tableAccessStatisticsParsedQueryRegistry = beanFactory.getBean(
        TasParsedQueryRegistry.class);

    var tasQueryParsingInterceptor = beanFactory.getBean(TasQueryParsingInterceptor.class);
    var tasQueryParsingListener = beanFactory.getBean(TasQueryParsingListener.class);

    spyqlDataSource.addListener(
        new TableAccessStatisticsSpyqlListener(meterCache, executorService, tableAccessStatisticsParsedQueryRegistry, databaseName,
            entryPointsProperties, tasQueryParsingListener, tasQueryParsingInterceptor));
  }
}
