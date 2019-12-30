package com.transferwise.common.entrypoints.test;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.output.OutputFrame;

import java.time.Duration;
import java.util.function.Consumer;

@Slf4j
public class DatabaseContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        MariaDBContainer mySQLContainer = startMariaDbTestContainer(applicationContext.getEnvironment());
        Integer mysqlPort = mySQLContainer.getMappedPort(3306);
        log.info("{} running on port {}", mySQLContainer.getDockerImageName(), mysqlPort);
        TestPropertySourceUtils
            .addInlinedPropertiesToEnvironment(applicationContext, "embedded.mysql.port=" + mysqlPort);
    }

    private MariaDBContainer startMariaDbTestContainer(ConfigurableEnvironment configurableEnvironment) {
        MariaDBContainer mySQLContainer = new MariaDBContainer()
            .withDatabaseName("mydb")
            .withPassword("q1w2e3r4");

        mySQLContainer.withCommand("mysqld",
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
        mySQLContainer.withStartupTimeout(Duration.ofMinutes(2));
        mySQLContainer.withLogConsumer((Consumer<OutputFrame>) outputFrame -> log.debug(outputFrame.getUtf8String()));
        mySQLContainer.start();

        return mySQLContainer;
    }
}
