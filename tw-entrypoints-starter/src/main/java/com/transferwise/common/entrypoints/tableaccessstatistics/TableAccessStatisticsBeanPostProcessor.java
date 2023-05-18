package com.transferwise.common.entrypoints.tableaccessstatistics;

import com.transferwise.common.baseutils.concurrency.IExecutorServicesProvider;
import com.transferwise.common.baseutils.concurrency.ThreadNamingExecutorServiceWrapper;
import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.entrypoints.BaseEntryPointsBeanProcessor;
import com.transferwise.common.entrypoints.EntryPointsProperties;
import com.transferwise.common.spyql.SpyqlDataSource;
import java.util.concurrent.ExecutorService;
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

    EntryPointsProperties entryPointsProperties = beanFactory.getBean(EntryPointsProperties.class);
    IMeterCache meterCache = beanFactory.getBean(IMeterCache.class);
    ExecutorService executorService = new ThreadNamingExecutorServiceWrapper("eptas", beanFactory
        .getBean(IExecutorServicesProvider.class).getGlobalExecutorService());

    TableAccessStatisticsParsedQueryRegistry tableAccessStatisticsParsedQueryRegistry = beanFactory.getBean(
        TableAccessStatisticsParsedQueryRegistry.class);

    spyqlDataSource.addListener(
        new TableAccessStatisticsSpyqlListener(meterCache, executorService, tableAccessStatisticsParsedQueryRegistry, databaseName,
            entryPointsProperties));
  }
}
