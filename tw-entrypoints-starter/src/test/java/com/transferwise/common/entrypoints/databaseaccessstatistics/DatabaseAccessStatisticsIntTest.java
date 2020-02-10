package com.transferwise.common.entrypoints.databaseaccessstatistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.transferwise.common.context.TwContext;
import com.transferwise.common.entrypoints.test.BaseIntTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseAccessStatisticsIntTest extends BaseIntTest {

  @Autowired
  private DataSource dataSource;

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    super.setup();
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @Test
  public void selectGetsRegisteredInAnEntryPoint() {
    TwContext.current().createSubContext().asEntryPoint("Test", "myEntryPoint").execute(() -> {
      jdbcTemplate.queryForList("select * from table_a", Long.class);
    });

    Map<String, Meter> meters = metersAsMap();

    assertThat(((DistributionSummary) meters.get("Registered.NTQueries")).count()).isEqualTo(1);
    assertThat(((DistributionSummary) meters.get("Registered.NTQueries")).mean()).isEqualTo(1);
    assertThat(meters.get("Registered.NTQueries").getId().getTag("db")).isEqualTo("mydb");
    assertThat(((DistributionSummary) meters.get("Registered.TQueries")).mean()).isEqualTo(0);
    assertThat(((DistributionSummary) meters.get("Registered.MaxConcurrentConnections")).mean()).isEqualTo(1);
    assertThat(((DistributionSummary) meters.get("Registered.RemainingOpenConnections")).mean()).isEqualTo(0);
    assertThat(((Timer) meters.get("Registered.TimeTaken")).mean(TimeUnit.NANOSECONDS)).isGreaterThan(0);
    assertThat(((DistributionSummary) meters.get("Registered.Commits")).mean()).isEqualTo(0);
    assertThat(((DistributionSummary) meters.get("Registered.Rollbacks")).mean()).isEqualTo(0);

    assertThat(((Counter) meters.get("Unknown.Commits")).count()).isEqualTo(0);
    assertThat(((Counter) meters.get("Unknown.NTQueries")).count()).isEqualTo(0);
    assertThat(((Counter) meters.get("Unknown.TQueries")).count()).isEqualTo(0);
  }

  @Test
  public void selectsGetsRegisteredOutsideOfAnEntrypoint() {
    jdbcTemplate.queryForList("select * from table_a", Long.class);

    // Unknown context statistics will be converted to metrics on next entrypoints access.
    TwContext.current().createSubContext().asEntryPoint("group", "name").execute(() -> {
    });

    Map<String, Meter> meters = metersAsMap();
    assertThat(meters.get("Registered.NTQueries")).isNull();
    assertThat(((Counter) meters.get("Unknown.Commits")).count()).isEqualTo(0);
    assertThat(((Counter) meters.get("Unknown.NTQueries")).count()).isEqualTo(1);
    assertThat(((Counter) meters.get("Unknown.TQueries")).count()).isEqualTo(0);
  }

  private Map<String, Meter> metersAsMap() {
    return meterRegistry.getMeters().stream().filter(m -> m.getId().getName().startsWith("EntryPoints.Das")).collect(Collectors
        .toMap(m -> StringUtils.substringAfter(m.getId().getName(), "EntryPoints.Das."), m -> m));
  }
}
