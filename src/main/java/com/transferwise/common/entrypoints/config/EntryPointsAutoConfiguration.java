package com.transferwise.common.entrypoints.config;

import com.transferwise.common.entrypoints.EntryPointInterceptor;
import com.transferwise.common.entrypoints.EntryPointNamingServletFilter;
import com.transferwise.common.entrypoints.EntryPointServletFilter;
import com.transferwise.common.entrypoints.EntryPoints;
import com.transferwise.common.entrypoints.databaseaccessstatistics.DatabaseAccessStatisticsBeanPostProcessor;
import com.transferwise.common.entrypoints.databaseaccessstatistics.DatabaseAccessStatisticsEntryPointInterceptor;
import com.transferwise.common.entrypoints.twtasks.EntryPointsTwTasksConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.List;

@Configuration
public class EntryPointsAutoConfiguration {
    @Bean
    public EntryPoints entryPoints(List<EntryPointInterceptor> entryPointInterceptors) {
        return new EntryPoints(entryPointInterceptors);
    }

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

    @Bean
    public DatabaseAccessStatisticsEntryPointInterceptor databaseAccessStatisticsEntryPointInterceptor(MeterRegistry meterRegistry) {
        return new DatabaseAccessStatisticsEntryPointInterceptor(meterRegistry);
    }

    @Bean
    public DatabaseAccessStatisticsBeanPostProcessor databaseAccessStatisticsBeanPostProcessor(BeanFactory beanFactory) {
        return new DatabaseAccessStatisticsBeanPostProcessor(beanFactory);
    }

    @Bean
    @ConditionalOnClass(name = "com.transferwise.tasks.processing.ITaskProcessingInterceptor")
    public EntryPointsTwTasksConfiguration entryPointsTwTasksConfiguration() {
        return new EntryPointsTwTasksConfiguration();
    }
}