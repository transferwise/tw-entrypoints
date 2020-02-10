package com.transferwise.common.entrypoints.tableaccessstatistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.transferwise.common.baseutils.ExceptionUtils;
import com.transferwise.common.context.TwContext;
import com.transferwise.common.entrypoints.test.BaseIntTest;
import com.transferwise.common.spyql.SpyqlDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class TableAccessStatisticsIntTest extends BaseIntTest {

  @Autowired
  private DataSource dataSource;

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    super.setup();
    jdbcTemplate = new JdbcTemplate(dataSource);
    invalidateParserCache();
  }

  @Test
  public void selectToNotExistingTableGetsCorrectlyRegistered() {
    TwContext.current().createSubContext().asEntryPoint("Test", "myEntryPoint").execute(() -> {
      try {
        jdbcTemplate.queryForObject("select id from not_existing_table", Long.class);
      } catch (Exception ignored) {
        //ignored
      }
    });

    List<Meter> meters = getMeters();

    assertThat(meters.size()).isEqualTo(1);
    assertThat(meters.get(0).getId().getTag("success")).isEqualTo("false");
    assertThat(meters.get(0).getId().getTag("db")).isEqualTo("mydb");
    assertThat(meters.get(0).getId().getTag("inTransaction")).isEqualTo("false");
    assertThat(meters.get(0).getId().getTag("operation")).isEqualTo("select");
    assertThat(meters.get(0).getId().getTag("table")).isEqualTo("not_existing_table");
    assertThat(meters.get(0).getId().getTag("epName")).isEqualTo("myEntryPoint");
    assertThat(meters.get(0).getId().getTag("epGroup")).isEqualTo("Test");
    assertThat(((Counter) meters.get(0)).count()).isEqualTo(1);
  }

  @Test
  public void workingUpdateSqlGetCorrectlyRegistered() {
    TwContext.current().createSubContext().asEntryPoint("Test", "myEntryPoint").execute(() -> {
      jdbcTemplate.update("update table_a set version=2");
    });

    List<Meter> meters = getMeters();
    assertThat(meters.size()).isEqualTo(1);
    assertThat(meters.get(0).getId().getTag("success")).isEqualTo("true");
    assertThat(meters.get(0).getId().getTag("db")).isEqualTo("mydb");
    assertThat(meters.get(0).getId().getTag("inTransaction")).isEqualTo("false");
    assertThat(meters.get(0).getId().getTag("operation")).isEqualTo("update");
    assertThat(meters.get(0).getId().getTag("table")).isEqualTo("table_a");
    assertThat(meters.get(0).getId().getTag("epName")).isEqualTo("myEntryPoint");
    assertThat(meters.get(0).getId().getTag("epGroup")).isEqualTo("Test");
    assertThat(((Counter) meters.get(0)).count()).isEqualTo(1);
  }

  @Test
  public void updateSqlDoneOutsideOfEntrypointGetsAlsoRegistered() {
    jdbcTemplate.update("update table_a set version=2");

    List<Meter> meters = getMeters();

    assertThat(meters.size()).isEqualTo(1);
    assertThat(meters.get(0).getId().getTag("success")).isEqualTo("true");
    assertThat(meters.get(0).getId().getTag("db")).isEqualTo("mydb");
    assertThat(meters.get(0).getId().getTag("inTransaction")).isEqualTo("false");
    assertThat(meters.get(0).getId().getTag("operation")).isEqualTo("update");
    assertThat(meters.get(0).getId().getTag("table")).isEqualTo("table_a");
    assertThat(meters.get(0).getId().getTag("epName")).isEqualTo("Generic");
    assertThat(meters.get(0).getId().getTag("epGroup")).isEqualTo("Generic");
    assertThat(((Counter) meters.get(0)).count()).isEqualTo(1);
  }

  @Test
  public void sqlParserCacheIsUsedForSameSqls() {
    jdbcTemplate.update("update table_a set version=2");
    jdbcTemplate.update("update table_a set version=2");
    jdbcTemplate.update("update table_a set version=2");

    List<Meter> meters = getMeters();
    assertThat(meters.size()).isEqualTo(1);
    assertThat(((Counter) meters.get(0)).count()).isEqualTo(3);

    assertThat(((Gauge) getMeter("EntryPoints.Tas.SqlParseResultsCache.size")).value()).isEqualTo(1);
  }

  private Meter getMeter(String name) {
    return meterRegistry.getMeters().stream().filter(m -> m.getId().getName().equals(name))
        .findFirst().orElse(null);
  }

  private List<Meter> getMeters() {
    return meterRegistry.getMeters().stream().filter(m -> m.getId().getName().equals("EntryPoints.Tas.TableAccess"))
        .collect(Collectors.toList());
  }

  private void invalidateParserCache() {
    ExceptionUtils.doUnchecked(() -> {
      SpyqlDataSource spyqlDataSource = dataSource.unwrap(SpyqlDataSource.class);
      TableAccessStatisticsSpyqlListener listener = (TableAccessStatisticsSpyqlListener) spyqlDataSource.getDataSourceListeners().stream()
          .filter(l -> l instanceof TableAccessStatisticsSpyqlListener).findFirst()
          .orElseThrow(() -> new IllegalStateException("TableAccessStatisticsSpyqlListener not found"));

      listener.sqlParseResultsCache.invalidateAll();
    });
  }
}