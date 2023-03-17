package com.transferwise.common.entrypoints;

import com.transferwise.common.baseutils.ExceptionUtils;
import com.transferwise.common.spyql.SpyqlDataSource;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

@Slf4j
public abstract class BaseEntryPointsBeanProcessor implements BeanPostProcessor, Ordered {

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
    return bean;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (!(bean instanceof DataSource)) {
      return bean;
    }

    ExceptionUtils.doUnchecked(() -> {
      var dataSource = (DataSource) bean;

      if (!dataSource.isWrapperFor(SpyqlDataSource.class)) {
        throw new IllegalStateException("DataSource has no wrapper for 'SpyqlDataSource'. Is the `tw-spyql-starter` dependency present?");
      }

      var spyqlDataSource = dataSource.unwrap(SpyqlDataSource.class);
      instrument(spyqlDataSource, spyqlDataSource.getDatabaseName());
    });

    return bean;
  }

  protected abstract void instrument(SpyqlDataSource spyqlDataSource, String databaseName);

  /**
   * Should run after Spyql bean processors.
   */
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 600;
  }
}
