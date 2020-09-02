package com.transferwise.common.entrypoints;

import com.transferwise.common.baseutils.ExceptionUtils;
import com.transferwise.common.spyql.SpyqlDataSource;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class SpyqlInstrumentingDataSourceBeanProcessor implements BeanPostProcessor {

  @Value("${spring.application.name:generic-service}")
  protected String appName;

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
    return bean;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (!(bean instanceof DataSource)) {
      return bean;
    }

    DataSource dataSourceWithSpyql = ensureSpyqlIntegration((DataSource) bean);
    if (dataSourceWithSpyql == null) {
      return bean;
    }

    return ExceptionUtils.doUnchecked(() -> {
      SpyqlDataSource spyqlDataSource = dataSourceWithSpyql.unwrap(SpyqlDataSource.class);

      String databaseName = spyqlDataSource.getDatabaseName();
      if (databaseName == null) {
        databaseName = appName.replaceAll("-service", "");
      }

      instrument(spyqlDataSource, databaseName);

      return dataSourceWithSpyql;
    });
  }

  protected abstract void instrument(SpyqlDataSource spyqlDataSource, String databaseName);

  private DataSource ensureSpyqlIntegration(DataSource dataSource) {
    return ExceptionUtils.doUnchecked(() -> {
      if (dataSource.isWrapperFor(SpyqlDataSource.class)) {
        return dataSource;
      }
      if (dataSource.isWrapperFor(HikariDataSource.class)) {
        String databaseName = dataSource.unwrap(HikariDataSource.class).getPoolName();
        if (StringUtils.isEmpty(databaseName)) {
          throw new IllegalStateException("Can not determine database name for a Hikari data source.");
        }

        log.info("Adding tw-spyql integration for '" + databaseName + "'.");
        return new SpyqlDataSource(dataSource, databaseName);
      }

      log.warn("DataSource '" + dataSource + "' has no tw-spyql nor Hikari integration. Ignoring it.");
      return null;
    });
  }
}
