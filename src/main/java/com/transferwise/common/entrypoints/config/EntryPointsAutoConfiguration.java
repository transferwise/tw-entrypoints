package com.transferwise.common.entrypoints.config;

import com.transferwise.common.baseutils.concurrency.DefaultExecutorServicesProvider;
import com.transferwise.common.baseutils.concurrency.IExecutorServicesProvider;
import com.transferwise.common.entrypoints.EntryPointNamingServletFilter;
import com.transferwise.common.entrypoints.EntryPointServletFilter;
import com.transferwise.common.entrypoints.EntryPoints;
import com.transferwise.common.entrypoints.EntryPointsRegistry;
import com.transferwise.common.entrypoints.IEntryPointInterceptor;
import com.transferwise.common.entrypoints.IEntryPointsRegistry;
import com.transferwise.common.entrypoints.databaseaccessstatistics.DatabaseAccessStatisticsBeanPostProcessor;
import com.transferwise.common.entrypoints.databaseaccessstatistics.DatabaseAccessStatisticsEntryPointInterceptor;
import com.transferwise.common.entrypoints.executionstatistics.ExecutionStatisticsEntryPointInterceptor;
import com.transferwise.common.entrypoints.tableaccessstatistics.TableAccessStatisticsBeanPostProcessor;
import com.transferwise.common.entrypoints.twtasks.EntryPointsTwTasksConfiguration;
import com.transferwise.tasks.processing.ITaskProcessingInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import javax.servlet.Servlet;
import java.util.List;

@Configuration
public class EntryPointsAutoConfiguration {
    @Configuration
    @ConditionalOnClass(ITaskProcessingInterceptor.class)
    @Import(EntryPointsTwTasksConfiguration.class)
    protected static class EntryPointsTwTasksConfigurationToggle {
    }

    @Configuration
    @ConditionalOnClass(Servlet.class)
    protected static class EntryPointsServletFilterConfiguration {
        @Bean
        public EntryPointServletFilter entryPointServletFilter(EntryPoints entryPoints) {
            return new EntryPointServletFilter(entryPoints);
        }

        @Bean
        public FilterRegistrationBean entryPointServletFilterRegistration(EntryPointServletFilter entryPointServletFilter) {
            FilterRegistrationBean registrationBean = new FilterRegistrationBean();
            registrationBean.setFilter(entryPointServletFilter);
            registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

            return registrationBean;
        }

        @Bean
        public EntryPointNamingServletFilter entryPointNamingServletFilter(EntryPoints entryPoints) {
            return new EntryPointNamingServletFilter(entryPoints);
        }

        @Bean
        public FilterRegistrationBean entryPointNamingServletFilterRegistration(EntryPointNamingServletFilter entryPointNamingServletFilter) {
            FilterRegistrationBean registrationBean = new FilterRegistrationBean();
            registrationBean.setFilter(entryPointNamingServletFilter);
            registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE);

            return registrationBean;
        }
    }

    @Bean
    public EntryPoints entryPoints(List<IEntryPointInterceptor> entryPointInterceptors) {
        return new EntryPoints(entryPointInterceptors);
    }

    @Bean
    public DatabaseAccessStatisticsEntryPointInterceptor databaseAccessStatisticsEntryPointInterceptor(MeterRegistry meterRegistry,
                                                                                                       IEntryPointsRegistry entryPointsRegistry) {
        return new DatabaseAccessStatisticsEntryPointInterceptor(meterRegistry, entryPointsRegistry);
    }

    @Bean
    public DatabaseAccessStatisticsBeanPostProcessor databaseAccessStatisticsBeanPostProcessor(BeanFactory beanFactory) {
        return new DatabaseAccessStatisticsBeanPostProcessor(beanFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "tw-entrypoints.tas.enabled", havingValue = "true", matchIfMissing = true)
    public TableAccessStatisticsBeanPostProcessor tableAccessStatisticsBeanPostProcessor(BeanFactory beanFactory) {
        return new TableAccessStatisticsBeanPostProcessor(beanFactory);
    }

    @Bean
    public EntryPointsRegistry entryPointsRegistry(MeterRegistry meterRegistry) {
        return new EntryPointsRegistry(meterRegistry);
    }

    @Bean
    public ExecutionStatisticsEntryPointInterceptor executionStatisticsEntryPointInterceptor(MeterRegistry meterRegistry, IEntryPointsRegistry entryPointsRegistry) {
        return new ExecutionStatisticsEntryPointInterceptor(meterRegistry, entryPointsRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(IExecutorServicesProvider.class)
    public DefaultExecutorServicesProvider twDefaultExecutorServicesProvider() {
        return new DefaultExecutorServicesProvider();
    }
}
