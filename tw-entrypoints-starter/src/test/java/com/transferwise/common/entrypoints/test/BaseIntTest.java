package com.transferwise.common.entrypoints.test;

import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.context.TwContext;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@ActiveProfiles(profiles = {"test", "mysql"}, resolver = SystemPropertyActiveProfilesResolver.class)
@SpringBootTest(classes = TestApplication.class)
@ContextConfiguration(initializers = DatabaseContainerInitializer.class)
@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
public class BaseIntTest {

  @Autowired
  protected MeterRegistry meterRegistry;
  @Autowired
  protected IMeterCache meterCache;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeAll
  public void setupClass() {
    jdbcTemplate.update("delete from table_a");
    jdbcTemplate.update("delete from table_b");
  }

  @BeforeEach
  public void setup() {
    // Resetting counters from Flyway executions.
    TwContext.current().createSubContext().asEntryPoint("A", "B").execute(() -> {
    });
    meterRegistry.getMeters().stream()
        .filter(m -> (m.getId().getName().startsWith("EntryPoints") || m.getId().getName().startsWith("database")) && !(m instanceof Gauge))
        .forEach(m -> {
          log.info("Removing metric: " + m.getId());
          meterRegistry.remove(m);
        });
    meterCache.clear();
  }
}
