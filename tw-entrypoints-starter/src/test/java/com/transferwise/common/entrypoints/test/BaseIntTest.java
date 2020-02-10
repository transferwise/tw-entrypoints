package com.transferwise.common.entrypoints.test;

import com.transferwise.common.context.TwContext;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = TestApplication.class)
@ContextConfiguration(initializers = DatabaseContainerInitializer.class)
@Slf4j
public class BaseIntTest {

  @Autowired
  protected MeterRegistry meterRegistry;

  @BeforeEach
  public void setup() {
    // Resetting counters from Flyway executions.
    TwContext.current().createSubContext().asEntryPoint("A", "B").execute(() -> {
    });
    meterRegistry.getMeters().stream().filter(m -> m.getId().getName().startsWith("EntryPoints") && !(m instanceof Gauge))
        .forEach(m -> {
          log.info("Removing metric: " + m.getId());
          meterRegistry.remove(m);
        });
  }
}
