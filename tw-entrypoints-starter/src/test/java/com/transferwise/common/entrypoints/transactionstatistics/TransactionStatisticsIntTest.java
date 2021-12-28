package com.transferwise.common.entrypoints.transactionstatistics;

import static com.transferwise.common.entrypoints.transactionstatistics.TransactionsStatisticsSpyqlListener.METRIC_TRANSACTION_COMPLETION;
import static com.transferwise.common.entrypoints.transactionstatistics.TransactionsStatisticsSpyqlListener.METRIC_TRANSACTION_FINALIZATION;
import static com.transferwise.common.entrypoints.transactionstatistics.TransactionsStatisticsSpyqlListener.METRIC_TRANSACTION_START;
import static org.assertj.core.api.Assertions.assertThat;

import com.transferwise.common.baseutils.transactionsmanagement.ITransactionsHelper;
import com.transferwise.common.entrypoints.test.BaseIntTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class TransactionStatisticsIntTest extends BaseIntTest {

  @Autowired
  private DataSource dataSource;

  @Autowired
  private ITransactionsHelper transactionsHelper;

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    super.setup();
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @Test
  public void successfullTransactionGetsRegisterd() {
    transactionsHelper.withTransaction().call(() -> {
      jdbcTemplate.update("update table_a set version=2");
      return null;
    });

    Counter startCounter = meterRegistry.get(METRIC_TRANSACTION_START).counter();
    assertThat(startCounter.count()).isEqualTo(1);
    assertThat(startCounter.getId().getTag("db")).isEqualTo("mydb");
    assertThat(startCounter.getId().getTag("epName")).isEqualTo("Generic");
    assertThat(startCounter.getId().getTag("epGroup")).isEqualTo("Generic");
    assertThat(startCounter.getId().getTag("epOwner")).isEqualTo("Generic");
    assertThat(startCounter.getId().getTag("readOnly")).isEqualTo("false");
    assertThat(startCounter.getId().getTag("transactionName")).isEqualTo("Unknown");

    var finalizationTimer = meterRegistry.get(METRIC_TRANSACTION_FINALIZATION).timer();
    assertThat(finalizationTimer.count()).isEqualTo(1);
    assertThat(finalizationTimer.getId().getTag("db")).isEqualTo("mydb");
    assertThat(finalizationTimer.getId().getTag("epName")).isEqualTo("Generic");
    assertThat(finalizationTimer.getId().getTag("epGroup")).isEqualTo("Generic");
    assertThat(finalizationTimer.getId().getTag("epOwner")).isEqualTo("Generic");
    assertThat(finalizationTimer.getId().getTag("readOnly")).isEqualTo("false");
    assertThat(finalizationTimer.getId().getTag("transactionName")).isEqualTo("Unknown");
    assertThat(finalizationTimer.getId().getTag("resolution")).isEqualTo("commit");
    assertThat(finalizationTimer.getId().getTag("resolutionSuccess")).isEqualTo("true");

    var finishedTimer = meterRegistry.get(METRIC_TRANSACTION_COMPLETION).timer();
    assertThat(finishedTimer.count()).isEqualTo(1);
    assertThat(finishedTimer.getId().getTag("db")).isEqualTo("mydb");
    assertThat(finishedTimer.getId().getTag("epName")).isEqualTo("Generic");
    assertThat(finishedTimer.getId().getTag("epGroup")).isEqualTo("Generic");
    assertThat(finishedTimer.getId().getTag("epOwner")).isEqualTo("Generic");
    assertThat(finishedTimer.getId().getTag("readOnly")).isEqualTo("false");
    assertThat(finishedTimer.getId().getTag("transactionName")).isEqualTo("Unknown");
    assertThat(finishedTimer.getId().getTag("resolution")).isEqualTo("commit");
    assertThat(finishedTimer.getId().getTag("resolutionSuccess")).isEqualTo("true");

    assertThat(finishedTimer.takeSnapshot().histogramCounts().length).isEqualTo(8);
  }

  @Test
  public void rollbackGetsRegisterd() {
    try {
      transactionsHelper.withTransaction().call(() -> {
        jdbcTemplate.update("update table_a set version=2");
        throw new RuntimeException("Let's roll back.");
      });
    } catch (RuntimeException ignored) {
      // ignored
    }

    Counter startCounter = meterRegistry.get(METRIC_TRANSACTION_START).counter();
    assertThat(startCounter.count()).isEqualTo(1);

    Timer finalizationTimer = meterRegistry.get(METRIC_TRANSACTION_FINALIZATION).timer();
    assertThat(finalizationTimer.getId().getTag("resolution")).isEqualTo("rollback");
    assertThat(finalizationTimer.getId().getTag("resolutionSuccess")).isEqualTo("true");

    Timer finishedTimer = meterRegistry.get(METRIC_TRANSACTION_COMPLETION).timer();
    assertThat(finishedTimer.getId().getTag("resolution")).isEqualTo("rollback");
    assertThat(finishedTimer.getId().getTag("resolutionSuccess")).isEqualTo("true");
  }
}
