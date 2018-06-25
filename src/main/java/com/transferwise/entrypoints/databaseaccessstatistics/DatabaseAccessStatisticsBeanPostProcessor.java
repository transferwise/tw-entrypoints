package com.transferwise.entrypoints.databaseaccessstatistics;

import com.transferwise.common.utils.ExceptionUtils;
import com.transferwise.entrypoints.EntryPoints;
import com.transferwise.spyql.SpyqlDataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

public class DatabaseAccessStatisticsBeanPostProcessor implements BeanPostProcessor {
    private EntryPoints entryPoints;

    public DatabaseAccessStatisticsBeanPostProcessor(EntryPoints entryPoints) {
        this.entryPoints = entryPoints;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return ExceptionUtils.callUnchecked(() -> {
            if (bean instanceof DataSource) {
                DataSource dataSource = (DataSource) bean;
                if (!dataSource.isWrapperFor(SpyqlDataSource.class)) {
                    return bean;
                }
                SpyqlDataSource spyqlDataSource = dataSource.unwrap(SpyqlDataSource.class);
                boolean isAlreadyAttached = spyqlDataSource.getDataSourceListeners().stream().filter((l) -> l instanceof DatabaseAccessStatisticsSpyqlListener)
                    .findFirst().isPresent();

                if (isAlreadyAttached) {
                    return bean;
                }
                spyqlDataSource.addListener(new DatabaseAccessStatisticsSpyqlListener(entryPoints));
            }

            return bean;
        });
    }
}
