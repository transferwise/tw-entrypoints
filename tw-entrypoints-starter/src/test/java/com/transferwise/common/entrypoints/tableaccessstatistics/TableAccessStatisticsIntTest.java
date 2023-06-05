package com.transferwise.common.entrypoints.tableaccessstatistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.transferwise.common.baseutils.ExceptionUtils;
import com.transferwise.common.context.TwContext;
import com.transferwise.common.entrypoints.tableaccessstatistics.ParsedQuery.SqlOperation;
import com.transferwise.common.entrypoints.test.BaseIntTest;
import com.transferwise.common.spyql.SpyqlDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

class TableAccessStatisticsIntTest extends BaseIntTest {

  @Autowired
  private TestTasQueryParsingInterceptor testTasQueryParsingInterceptor;

  @Autowired
  private DataSource dataSource;

  @Autowired
  private DefaultTasParsedQueryRegistry tableAccessStatisticsParsedQueryRegistry;

  @Autowired
  private TasFlywayConfigurationCustomizer tasFlywayConfigurationCustomizer;

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    super.setup();
    jdbcTemplate = new JdbcTemplate(dataSource);
    invalidateParserCache();
    testTasQueryParsingInterceptor.setParsedQuery(null);
  }

  @Test
  void flywayCustomizerWasApplied() {
    Assertions.assertTrue(tasFlywayConfigurationCustomizer.queryParsingWasEnabled);
    Assertions.assertTrue(tasFlywayConfigurationCustomizer.queryParsingWasDisabled);
  }

  @Test
  void selectToNotExistingTableGetsCorrectlyRegistered() {
    TwContext.current().createSubContext().asEntryPoint("Test", "myEntryPoint").execute(() -> {
      try {
        jdbcTemplate.queryForObject("select id from not_existing_table", Long.class);
      } catch (Exception ignored) {
        //ignored
      }
    });

    List<Meter> meters = getTableAccessMeters();

    assertThat(meters.size()).isEqualTo(1);
    var counter = (Counter) meters.get(0);
    assertThat(counter.getId().getTag("success")).isEqualTo("false");
    assertThat(counter.getId().getTag("db")).isEqualTo("mydb");
    assertThat(counter.getId().getTag("inTransaction")).isEqualTo("false");
    assertThat(counter.getId().getTag("operation")).isEqualTo("select");
    assertThat(counter.getId().getTag("table")).isEqualTo("not_existing_table");
    assertThat(counter.getId().getTag("epName")).isEqualTo("myEntryPoint");
    assertThat(counter.getId().getTag("epGroup")).isEqualTo("Test");
    assertThat(counter.count()).isEqualTo(1);

    var firstTableAccessMeter = (Timer) getMeter("EntryPoints_Tas_FirstTableAccess");

    assertThat(firstTableAccessMeter).isNotNull();
    assertThat(firstTableAccessMeter.getId().getTag("success")).isEqualTo("false");
    assertThat(firstTableAccessMeter.getId().getTag("db")).isEqualTo("mydb");
    assertThat(firstTableAccessMeter.getId().getTag("inTransaction")).isEqualTo("false");
    assertThat(firstTableAccessMeter.getId().getTag("operation")).isEqualTo("select");
    assertThat(firstTableAccessMeter.getId().getTag("table")).isEqualTo("not_existing_table");
    assertThat(firstTableAccessMeter.getId().getTag("epName")).isEqualTo("myEntryPoint");
    assertThat(firstTableAccessMeter.getId().getTag("epGroup")).isEqualTo("Test");
    assertThat(firstTableAccessMeter.getId().getTag("epOwner")).isEqualTo("Generic");
    assertThat(firstTableAccessMeter.count()).isEqualTo(1);
    assertThat(firstTableAccessMeter.mean(TimeUnit.NANOSECONDS)).isGreaterThan(0);
  }

  @Test
  void parsedQueryRegistryCanOverrideParsing() {
    String sql = "select id from not_existing_table limit 1234";

    tableAccessStatisticsParsedQueryRegistry.register(sql, new ParsedQuery().addOperation("upsert", new SqlOperation().addTable("my_custom_table")));

    TwContext.current().createSubContext().asEntryPoint("Test", "myEntryPoint").execute(() -> {
      try {
        jdbcTemplate.queryForObject("select id from not_existing_table limit 1234", Long.class);
      } catch (Exception ignored) {
        //ignored
      }
    });

    List<Meter> meters = getTableAccessMeters();

    assertThat(meters.size()).isEqualTo(1);
    var counter = (Counter) meters.get(0);

    assertThat(counter.getId().getTag("operation")).isEqualTo("upsert");
    assertThat(counter.getId().getTag("table")).isEqualTo("my_custom_table");

    var firstTableAccessMeter = (Timer) getMeter("EntryPoints_Tas_FirstTableAccess");

    assertThat(firstTableAccessMeter.getId().getTag("operation")).isEqualTo("upsert");
    assertThat(firstTableAccessMeter.getId().getTag("table")).isEqualTo("my_custom_table");
  }

  @Test
  void interceptorCanProvideItsOwnParsedQuery() {
    testTasQueryParsingInterceptor.setParsedQuery(new ParsedQuery().addOperation("insert",
        new SqlOperation().addTable("my_custom_table123")));

    String sql = "select id from not_existing_table limit 1234";

    TwContext.current().createSubContext().asEntryPoint("Test", "myEntryPoint").execute(() -> {
      try {
        jdbcTemplate.queryForObject(sql, Long.class);
      } catch (Exception ignored) {
        //ignored
      }
    });

    List<Meter> meters = getTableAccessMeters();

    assertThat(meters.size()).isEqualTo(1);
    var counter = (Counter) meters.get(0);

    assertThat(counter.getId().getTag("operation")).isEqualTo("insert");
    assertThat(counter.getId().getTag("table")).isEqualTo("my_custom_table123");
  }

  @Test
  public void workingUpdateSqlGetCorrectlyRegistered() {
    TwContext.current().createSubContext().asEntryPoint("Test", "myEntryPoint").execute(() -> {
      jdbcTemplate.update("update table_a set version=2");
    });

    List<Meter> meters = getTableAccessMeters();
    assertThat(meters.size()).isEqualTo(1);
    assertThat(meters.get(0).getId().getTag("success")).isEqualTo("true");
    assertThat(meters.get(0).getId().getTag("db")).isEqualTo("mydb");
    assertThat(meters.get(0).getId().getTag("inTransaction")).isEqualTo("false");
    assertThat(meters.get(0).getId().getTag("operation")).isEqualTo("update");
    assertThat(meters.get(0).getId().getTag("table")).isEqualTo("table_a");
    assertThat(meters.get(0).getId().getTag("epName")).isEqualTo("myEntryPoint");
    assertThat(meters.get(0).getId().getTag("epGroup")).isEqualTo("Test");
    assertThat(meters.get(0).getId().getTag("epOwner")).isEqualTo("Generic");
    assertThat(((Counter) meters.get(0)).count()).isEqualTo(1);
  }

  @Test
  public void updateSqlDoneOutsideOfEntrypointGetsAlsoRegistered() {
    jdbcTemplate.update("update table_a set version=2");

    List<Meter> meters = getTableAccessMeters();

    assertThat(meters.size()).isEqualTo(1);
    assertThat(meters.get(0).getId().getTag("success")).isEqualTo("true");
    assertThat(meters.get(0).getId().getTag("db")).isEqualTo("mydb");
    assertThat(meters.get(0).getId().getTag("inTransaction")).isEqualTo("false");
    assertThat(meters.get(0).getId().getTag("operation")).isEqualTo("update");
    assertThat(meters.get(0).getId().getTag("table")).isEqualTo("table_a");
    assertThat(meters.get(0).getId().getTag("epName")).isEqualTo("Generic");
    assertThat(meters.get(0).getId().getTag("epGroup")).isEqualTo("Generic");
    assertThat(meters.get(0).getId().getTag("epOwner")).isEqualTo("Generic");
    assertThat(((Counter) meters.get(0)).count()).isEqualTo(1);
  }

  @Test
  public void sqlParserCacheIsUsedForSameSqls() {
    jdbcTemplate.update("update table_a set version=2");
    jdbcTemplate.update("update table_a set version=2");
    jdbcTemplate.update("update table_a set version=2");

    List<Meter> meters = getTableAccessMeters();
    assertThat(meters.size()).isEqualTo(1);
    assertThat(((Counter) meters.get(0)).count()).isEqualTo(3);

    assertThat(((Gauge) getMeter("EntryPoints_Tas_SqlParseResultsCache_size")).value()).isEqualTo(1);
  }

  @Test
  void failedSqlParsesGetRegistered() {
    try {
      jdbcTemplate.update("alter update blah");
    } catch (BadSqlGrammarException ignored) {
      //ignored
    }

    assertThat(getCounter("EntryPoints_Tas_FailedParses").count()).isEqualTo(1);
  }

  @Test
  void queryParsingCanBeDisabled() {
    TwContext.current().createSubContext().execute(() -> {
      TasUtils.disableQueryParsing(TwContext.current());
      try {
        jdbcTemplate.update("alter update create blah");
      } catch (BadSqlGrammarException ignored) {
        // ignored
      }
    });

    assertThat(getCounter("EntryPoints_Tas_FailedParses")).isNull();
  }

  private Counter getCounter(String name) {
    return (Counter) getMeter(name);
  }

  private Meter getMeter(String name) {
    return meterRegistry.getMeters().stream().filter(m -> m.getId().getName().equals(name))
        .findFirst().orElse(null);
  }

  private List<Meter> getTableAccessMeters() {
    return meterRegistry.getMeters().stream().filter(m -> m.getId().getName().equals("EntryPoints_Tas_TableAccess"))
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
