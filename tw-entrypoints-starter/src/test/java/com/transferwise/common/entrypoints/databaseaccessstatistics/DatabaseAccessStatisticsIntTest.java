package com.transferwise.common.entrypoints.databaseaccessstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.transferwise.common.context.TwContext;
import com.transferwise.common.entrypoints.test.BaseIntTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseAccessStatisticsIntTest extends BaseIntTest {

  @Autowired
  private DataSource dataSource;

  @Autowired
  private DasUnknownCallsCollector dasUnknownCallsCollector;

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    // Resetting counters from Flyway executions.
    dasUnknownCallsCollector.registerUnknownCalls();

    super.setup();
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @Test
  void selectGetsRegisteredInAnEntryPoint() {
    TwContext.current().createSubContext().asEntryPoint("Test", "myEntryPoint").execute(() -> {
      jdbcTemplate.queryForList("select id from table_a", Long.class);
    });

    var dasUnknownCallsCollectorIterations = dasUnknownCallsCollector.getIterationsCount();
    await().until(() -> dasUnknownCallsCollectorIterations < dasUnknownCallsCollector.getIterationsCount());

    Map<String, Meter> meters = metersAsMap();

    assertThat(((DistributionSummary) meters.get("Registered_NTQueries")).count()).isEqualTo(1);
    assertThat(((DistributionSummary) meters.get("Registered_NTQueries")).mean()).isEqualTo(1);
    assertThat(meters.get("Registered_NTQueries").getId().getTag("db")).isEqualTo("mydb");
    assertThat(((DistributionSummary) meters.get("Registered_TQueries")).mean()).isEqualTo(0);
    assertThat(((DistributionSummary) meters.get("Registered_MaxConcurrentConnections")).mean()).isEqualTo(1);
    assertThat(((DistributionSummary) meters.get("Registered_RemainingOpenConnections")).mean()).isEqualTo(0);
    assertThat(((Timer) meters.get("Registered_TimeTaken")).mean(TimeUnit.NANOSECONDS)).isGreaterThan(0);
    assertThat(((DistributionSummary) meters.get("Registered_Commits")).mean()).isEqualTo(0);
    assertThat(((DistributionSummary) meters.get("Registered_Rollbacks")).mean()).isEqualTo(0);
    assertThat(((DistributionSummary) meters.get("Registered_AffectedRows")).count()).isEqualTo(1);
    assertThat(((DistributionSummary) meters.get("Registered_FetchedRows")).count()).isEqualTo(1);

    assertThat(((Counter) meters.get("Unknown_Commits")).count()).isEqualTo(0);
    assertThat(((Counter) meters.get("Unknown_NTQueries")).count()).isEqualTo(0);
    assertThat(((Counter) meters.get("Unknown_TQueries")).count()).isEqualTo(0);
  }

  @Test
  void selectsGetsRegisteredOutsideOfAnEntrypoint() {
    jdbcTemplate.queryForList("select id from table_a", Long.class);

    // Unknown context statistics will be converted to metrics on next entrypoints access.
    TwContext.current().createSubContext().asEntryPoint("group", "name").execute(() -> {
    });

    var dasUnknownCallsCollectorIterations = dasUnknownCallsCollector.getIterationsCount();
    await().until(() -> dasUnknownCallsCollectorIterations < dasUnknownCallsCollector.getIterationsCount());

    Map<String, Meter> meters = metersAsMap();
    assertThat(meters.get("Registered_NTQueries")).isNull();

    assertThat(((Counter) meters.get("Unknown_Commits")).count()).isEqualTo(0);
    assertThat(((Counter) meters.get("Unknown_NTQueries")).count()).isEqualTo(1);
    assertThat(((Counter) meters.get("Unknown_TQueries")).count()).isEqualTo(0);
    assertThat(((Counter) meters.get("Unknown_AffectedRows")).count()).isEqualTo(0);
    assertThat(((Counter) meters.get("Unknown_FetchedRows")).count()).isEqualTo(0);
  }

  @Test
  void rowsStatisticsAreGathered() {
    TwContext.current().createSubContext().asEntryPoint("Test", "myEntryPoint").execute(() -> {
      for (int i = 0; i < 31; i++) {
        jdbcTemplate.update("insert into table_a (id, version) values (?,?)", i, 0);
      }

      jdbcTemplate.update("update table_a set version=1 where id<7");

      jdbcTemplate.update("delete from table_a where id >= 26");

      jdbcTemplate.queryForList("select version from table_a", Integer.class);
    });

    Map<String, Meter> meters = metersAsMap();

    assertThat(((DistributionSummary) meters.get("Registered_AffectedRows")).count()).isEqualTo(1);
    assertThat(((DistributionSummary) meters.get("Registered_FetchedRows")).count()).isEqualTo(1);

    assertThat(((DistributionSummary) meters.get("Registered_AffectedRows")).totalAmount()).isEqualTo(31 + 7 + 5);
    assertThat(((DistributionSummary) meters.get("Registered_FetchedRows")).totalAmount()).isEqualTo(31 - 5);

  }

  // This test doesn't test so much the potential concurrent modification exceptions, but it's more about documenting that it's an expected behaviour
  // and how it should work.
  @Test
  void multipleThreadsAreAbleToGatherStatisticsWithinOneEntryPointConcurrently() {
    TwContext entryPointContext = TwContext.current().createSubContext().asEntryPoint("Test", "myConcurrentEntryPoint");
    List<Callable<Integer>> queries = Arrays.asList(
        createQueryCallable(entryPointContext),
        createQueryCallable(entryPointContext),
        createQueryCallable(entryPointContext),
        createQueryCallable(entryPointContext)
    );

    ExecutorService executorService = Executors.newFixedThreadPool(2);
    try {
      entryPointContext.execute(() -> {
        try {
          List<Future<Integer>> futures = executorService.invokeAll(queries);
          for (Future<Integer> future : futures) {
            future.get(1000, TimeUnit.MILLISECONDS);
          }
        } catch (Throwable e) {
          throw new RuntimeException(e);
        }
      });
    } finally {
      executorService.shutdown();
    }

    DatabaseAccessStatistics stats = (DatabaseAccessStatistics) ((Map) entryPointContext.get(DatabaseAccessStatistics.TW_CONTEXT_KEY)).get("mydb");
    assertThat(stats.getNonTransactionalQueriesCount()).isEqualTo(4);
  }

  private Callable<Integer> createQueryCallable(TwContext twContext) {
    return () -> twContext.createSubContext().execute(() -> jdbcTemplate.queryForObject("select count(*) from table_a", Integer.class));
  }

  private Map<String, Meter> metersAsMap() {
    return meterRegistry.getMeters().stream().filter(m -> m.getId().getName().startsWith("EntryPoints_Das")).collect(Collectors
        .toMap(m -> StringUtils.substringAfter(m.getId().getName(), "EntryPoints_Das_"), m -> m));
  }
}
