package com.transferwise.common.entrypoints.tableaccessstatistics;

import com.transferwise.common.baseutils.ExceptionUtils;
import com.transferwise.common.entrypoints.EntryPoints;
import com.transferwise.common.entrypoints.EntryPointsRegistry;
import com.transferwise.common.spyql.SpyqlDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

public class TableAccessStatisticsBeanPostProcessor implements BeanPostProcessor {
    @Value("${spring.application.name:generic-service}")
    private String appName;
    @Value("${tw-entrypoints.tas.sql-parser.cache-size-mib:50}")
    private int sqlParserCacheSizeMib;

    private final BeanFactory beanFactory;

    public TableAccessStatisticsBeanPostProcessor(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return ExceptionUtils.doUnchecked(() -> {
            if (bean instanceof DataSource) {
                DataSource dataSource = (DataSource) bean;
                if (!dataSource.isWrapperFor(SpyqlDataSource.class)) {
                    return bean;
                }
                SpyqlDataSource spyqlDataSource = dataSource.unwrap(SpyqlDataSource.class);
                boolean isAlreadyAttached = spyqlDataSource.getDataSourceListeners().stream().anyMatch(
                    (l) -> l instanceof TableAccessStatisticsSpyqlListener);

                if (isAlreadyAttached) {
                    return bean;
                }
                String databaseName = spyqlDataSource.getDatabaseName();
                if (databaseName == null) {
                    databaseName = appName.replaceAll("-service", "");
                }
                spyqlDataSource.addListener(
                    new TableAccessStatisticsSpyqlListener(beanFactory.getBean(EntryPoints.class),
                                                           beanFactory.getBean(EntryPointsRegistry.class),
                                                           beanFactory.getBean(MeterRegistry.class),
                                                           databaseName, sqlParserCacheSizeMib));
            }

            return bean;
        });
    }
}
