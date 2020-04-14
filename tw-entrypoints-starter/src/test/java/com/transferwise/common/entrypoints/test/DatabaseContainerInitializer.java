package com.transferwise.common.entrypoints.test;

import java.time.Duration;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.output.OutputFrame;

@Slf4j
public class DatabaseContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    if (applicationContext.getEnvironment().getProperty("embedded.mysql.port") != null) {
      return;
    }
    MariaDBContainer<? extends MariaDBContainer> mySqlContainer = startMariaDbTestContainer(applicationContext.getEnvironment());
    Integer mysqlPort = mySqlContainer.getMappedPort(3306);
    log.info("{} running on port {}", mySqlContainer.getDockerImageName(), mysqlPort);
    TestPropertySourceUtils
        .addInlinedPropertiesToEnvironment(applicationContext, "embedded.mysql.port=" + mysqlPort);
  }

  private MariaDBContainer startMariaDbTestContainer(ConfigurableEnvironment configurableEnvironment) {
    MariaDBContainer<? extends MariaDBContainer> mySqlContainer = new MariaDBContainer<>()
        .withDatabaseName("mydb")
        .withPassword("q1w2e3r4");

    mySqlContainer.withCommand("mysqld",
        "--skip-host-cache",
        "--skip-name-resolve",
        "--innodb-stats-traditional=0",
        "--use-stat-tables=preferably",
        "--innodb_flush_neighbors=0",
        "--ssl=0",
        "--innodb_buffer_pool_size=256m",
        "--character-set-server=utf8mb4",
        "--collation-server=utf8mb4_general_ci",
        "--max_allowed_packet=32M",
        "--innodb_flush_log_at_trx_commit=2",
        "--transaction-isolation=READ-COMMITTED"
    );
    mySqlContainer.withStartupTimeout(Duration.ofMinutes(2));
    mySqlContainer.withLogConsumer((Consumer<OutputFrame>) outputFrame -> log.debug(outputFrame.getUtf8String()));
    mySqlContainer.start();

    return mySqlContainer;
  }
}
