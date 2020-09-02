package com.transferwise.common.entrypoints.test;

import com.transferwise.common.baseutils.transactionsmanagement.TransactionsHelper;
import com.transferwise.common.spyql.SpyqlDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class TestApplication {

  @Autowired
  private ConfigurableEnvironment env;

  @Bean
  public DataSource dataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(
        "jdbc:mariadb://localhost:" + env.getProperty("embedded.mysql.port") + "/mydb");
    config.setUsername("root");
    config.setPassword("q1w2e3r4");
    config.setPoolName("mydb");
    config.setConnectionTimeout(5000);
    config.setValidationTimeout(5000);

    HikariDataSource hikariDataSource = new HikariDataSource(config);
    return new SpyqlDataSource(hikariDataSource, config.getPoolName());
  }

  @Bean
  public TransactionsHelper transactionsHelper() {
    return new TransactionsHelper();
  }
}
