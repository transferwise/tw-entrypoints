package com.transferwise.common.entrypoints;

import com.transferwise.common.baseutils.concurrency.DefaultExecutorServicesProvider;
import com.transferwise.common.baseutils.concurrency.IExecutorServicesProvider;
import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.baseutils.meters.cache.MeterCache;
import com.transferwise.common.context.TwContext;
import com.transferwise.common.entrypoints.databaseaccessstatistics.DasUnknownCallsCollector;
import com.transferwise.common.entrypoints.databaseaccessstatistics.DatabaseAccessStatisticsBeanPostProcessor;
import com.transferwise.common.entrypoints.databaseaccessstatistics.DatabaseAccessStatisticsEntryPointInterceptor;
import com.transferwise.common.entrypoints.executionstatistics.ExecutionStatisticsEntryPointInterceptor;
import com.transferwise.common.entrypoints.tableaccessstatistics.DefaultTasParsedQueryRegistry;
import com.transferwise.common.entrypoints.tableaccessstatistics.DefaultTasQueryParsingInterceptor;
import com.transferwise.common.entrypoints.tableaccessstatistics.DefaultTasQueryParsingListener;
import com.transferwise.common.entrypoints.tableaccessstatistics.TableAccessStatisticsBeanPostProcessor;
import com.transferwise.common.entrypoints.tableaccessstatistics.TasFlywayConfigurationCustomizer;
import com.transferwise.common.entrypoints.tableaccessstatistics.TasMeterFilter;
import com.transferwise.common.entrypoints.tableaccessstatistics.TasParsedQueryRegistry;
import com.transferwise.common.entrypoints.tableaccessstatistics.TasQueryParsingInterceptor;
import com.transferwise.common.entrypoints.tableaccessstatistics.TasQueryParsingListener;
import com.transferwise.common.entrypoints.transactionstatistics.TransactionStatisticsBeanPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EntryPointsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(MetricsTemplate.class)
  public DefaultMetricsTemplate twEntryPointsMetricsTemplate(IMeterCache meterCache) {
    var template = new DefaultMetricsTemplate(meterCache);
    template.registerLibrary();
    return template;
  }

  @Bean
  @ConfigurationProperties(value = "tw-entrypoints", ignoreUnknownFields = false)
  public EntryPointsProperties twEntryPointsProperties() {
    return new EntryPointsProperties();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "tw-entrypoints.das.enabled", havingValue = "true", matchIfMissing = true)
  public DatabaseAccessStatisticsEntryPointInterceptor twEntryPointsDatabaseAccessStatisticsEntryPointInterceptor(IMeterCache meterCache) {
    DatabaseAccessStatisticsEntryPointInterceptor interceptor = new DatabaseAccessStatisticsEntryPointInterceptor(meterCache);
    TwContext.addExecutionInterceptor(interceptor);
    return interceptor;
  }

  @Bean
  @ConditionalOnProperty(name = "tw-entrypoints.das.enabled", havingValue = "true", matchIfMissing = true)
  @ConditionalOnMissingBean
  public static DatabaseAccessStatisticsBeanPostProcessor twEntryPointsDatabaseAccessStatisticsBeanPostProcessor() {
    return new DatabaseAccessStatisticsBeanPostProcessor();
  }

  @Bean
  @ConditionalOnProperty(name = "tw-entrypoints.tas.enabled", havingValue = "true", matchIfMissing = true)
  @ConditionalOnMissingBean
  public static TableAccessStatisticsBeanPostProcessor twEntryPointsTableAccessStatisticsBeanPostProcessor(BeanFactory beanFactory) {
    return new TableAccessStatisticsBeanPostProcessor(beanFactory);
  }

  @Bean
  @ConditionalOnProperty(name = "tw-entrypoints.tas.enabled", havingValue = "true", matchIfMissing = true)
  public static MeterFilter twEntryPointsTableAccessStatisticsMeterFilter() {
    return new TasMeterFilter();
  }

  @Bean
  @ConditionalOnProperty(name = "tw-entrypoints.tas.enabled", havingValue = "true", matchIfMissing = true)
  @ConditionalOnMissingBean(TasParsedQueryRegistry.class)
  public DefaultTasParsedQueryRegistry twEntryPointsTableAccessStatisticsParsedQueryRegistry() {
    return new DefaultTasParsedQueryRegistry();
  }


  @Bean
  @ConditionalOnProperty(name = "tw-entrypoints.tas.enabled", havingValue = "true", matchIfMissing = true)
  @ConditionalOnMissingBean(TasQueryParsingInterceptor.class)
  public DefaultTasQueryParsingInterceptor twEntryPointsTableAccessStatisticsQueryParsingInterceptor() {
    return new DefaultTasQueryParsingInterceptor();
  }

  @Bean
  @ConditionalOnProperty(name = "tw-entrypoints.tas.enabled", havingValue = "true", matchIfMissing = true)
  @ConditionalOnMissingBean(TasQueryParsingListener.class)
  public DefaultTasQueryParsingListener twEntryPointsTableAccessStatisticsQueryParsingListener(EntryPointsProperties entryPointsProperties) {
    return new DefaultTasQueryParsingListener(entryPointsProperties);
  }

  @Configuration
  @ConditionalOnProperty(name = "tw-entrypoints.tas.enabled", havingValue = "true", matchIfMissing = true)
  @ConditionalOnClass(name = {"org.flywaydb.core.api.configuration.FluentConfiguration",
      "org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer"})
  protected static class FlywayInterceptionConfiguration {

    @Bean
    @ConditionalOnProperty(name = "tw-entrypoints.tas.flyway-integration.enabled", havingValue = "true", matchIfMissing = true)
    public TasFlywayConfigurationCustomizer tasFlywayConfigurationCustomizer() {
      return new TasFlywayConfigurationCustomizer();
    }

  }

  @Bean
  @ConditionalOnProperty(name = "tw-entrypoints.ts.enabled", havingValue = "true", matchIfMissing = true)
  @ConditionalOnMissingBean
  public static TransactionStatisticsBeanPostProcessor twEntryPointsTransactionStatisticsBeanPostProcessor(BeanFactory beanFactory) {
    return new TransactionStatisticsBeanPostProcessor(beanFactory);
  }

  @Bean
  @ConditionalOnProperty(name = "tw-entrypoints.ts.enabled", havingValue = "true", matchIfMissing = true)
  public static MeterFilter twEntryPointsTransactionStatisticsMetricsFilter() {
    return new TasMeterFilter();
  }

  @Bean
  @ConditionalOnProperty(name = "tw-entrypoints.es.enabled", havingValue = "true", matchIfMissing = true)
  @ConditionalOnMissingBean
  public ExecutionStatisticsEntryPointInterceptor twEntryPointsExecutionStatisticsEntryPointInterceptor(IMeterCache meterCache) {
    ExecutionStatisticsEntryPointInterceptor interceptor = new ExecutionStatisticsEntryPointInterceptor(meterCache);
    TwContext.addExecutionInterceptor(interceptor);
    return interceptor;
  }

  @Bean
  @ConditionalOnMissingBean(IExecutorServicesProvider.class)
  public DefaultExecutorServicesProvider twDefaultExecutorServicesProvider() {
    return new DefaultExecutorServicesProvider();
  }

  @Bean
  @ConditionalOnMissingBean(IMeterCache.class)
  public IMeterCache twDefaultMeterCache(MeterRegistry meterRegistry) {
    return new MeterCache(meterRegistry);
  }

  @Bean
  @ConditionalOnMissingBean(DasUnknownCallsCollector.class)
  public DasUnknownCallsCollector unknownCallsCollector(IExecutorServicesProvider executorServicesProvider, IMeterCache meterCache) {
    return new DasUnknownCallsCollector(executorServicesProvider, meterCache);
  }
}
