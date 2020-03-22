package com.transferwise.common.entrypoints;

import com.transferwise.common.baseutils.concurrency.DefaultExecutorServicesProvider;
import com.transferwise.common.baseutils.concurrency.IExecutorServicesProvider;
import com.transferwise.common.context.TwContext;
import com.transferwise.common.entrypoints.databaseaccessstatistics.DatabaseAccessStatisticsBeanPostProcessor;
import com.transferwise.common.entrypoints.databaseaccessstatistics.DatabaseAccessStatisticsEntryPointInterceptor;
import com.transferwise.common.entrypoints.executionstatistics.ExecutionStatisticsEntryPointInterceptor;
import com.transferwise.common.entrypoints.tableaccessstatistics.TableAccessStatisticsBeanPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EntryPointsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public DatabaseAccessStatisticsEntryPointInterceptor databaseAccessStatisticsEntryPointInterceptor(MeterRegistry meterRegistry) {
    DatabaseAccessStatisticsEntryPointInterceptor interceptor = new DatabaseAccessStatisticsEntryPointInterceptor(meterRegistry);
    TwContext.addExecutionInterceptor(interceptor);
    return interceptor;
  }

  @Bean
  @ConditionalOnMissingBean
  public DatabaseAccessStatisticsBeanPostProcessor databaseAccessStatisticsBeanPostProcessor() {
    return new DatabaseAccessStatisticsBeanPostProcessor();
  }

  @Bean
  @ConditionalOnProperty(name = "tw-entrypoints.tas.enabled", havingValue = "true", matchIfMissing = true)
  @ConditionalOnMissingBean
  public TableAccessStatisticsBeanPostProcessor tableAccessStatisticsBeanPostProcessor(BeanFactory beanFactory) {
    return new TableAccessStatisticsBeanPostProcessor(beanFactory);
  }

  @Bean
  public ExecutionStatisticsEntryPointInterceptor executionStatisticsEntryPointInterceptor(MeterRegistry meterRegistry) {
    ExecutionStatisticsEntryPointInterceptor interceptor = new ExecutionStatisticsEntryPointInterceptor(meterRegistry);
    TwContext.addExecutionInterceptor(interceptor);
    return interceptor;
  }

  @Bean
  @ConditionalOnMissingBean(IExecutorServicesProvider.class)
  public DefaultExecutorServicesProvider twDefaultExecutorServicesProvider() {
    return new DefaultExecutorServicesProvider();
  }
}
