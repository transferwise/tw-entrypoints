package com.transferwise.common.entrypoints.databaseaccessstatistics;

import com.transferwise.common.baseutils.ExceptionUtils;
import com.transferwise.common.spyql.SpyqlDataSource;
import javax.sql.DataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;

public class DatabaseAccessStatisticsBeanPostProcessor implements BeanPostProcessor {

  @Value("${spring.application.name:generic-service}")
  private String appName;

  @Value("${tw-entrypoints.das.strictMode:false}")
  private boolean strictMode;

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
            (l) -> l instanceof DatabaseAccessStatisticsSpyqlListener);

        if (isAlreadyAttached) {
          return bean;
        }
        String databaseName = spyqlDataSource.getDatabaseName();
        if (databaseName == null) {
          databaseName = appName.replaceAll("-service", "");
        }
        spyqlDataSource.addListener(
            new DatabaseAccessStatisticsSpyqlListener(databaseName, strictMode));
      }

      return bean;
    });
  }
}
