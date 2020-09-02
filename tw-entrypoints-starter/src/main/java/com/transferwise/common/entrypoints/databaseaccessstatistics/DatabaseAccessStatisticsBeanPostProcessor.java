package com.transferwise.common.entrypoints.databaseaccessstatistics;

import com.transferwise.common.entrypoints.SpyqlInstrumentingDataSourceBeanProcessor;
import com.transferwise.common.spyql.SpyqlDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public class DatabaseAccessStatisticsBeanPostProcessor extends SpyqlInstrumentingDataSourceBeanProcessor {

  @Value("${tw-entrypoints.das.strictMode:false}")
  private boolean strictMode;

  @Override
  protected void instrument(SpyqlDataSource spyqlDataSource, String databaseName) {
    boolean isAlreadyAttached = spyqlDataSource.getDataSourceListeners().stream().anyMatch(
        (l) -> l instanceof DatabaseAccessStatisticsSpyqlListener);

    if (isAlreadyAttached) {
      return;
    }
    spyqlDataSource.addListener(
        new DatabaseAccessStatisticsSpyqlListener(databaseName, strictMode));
  }
}
