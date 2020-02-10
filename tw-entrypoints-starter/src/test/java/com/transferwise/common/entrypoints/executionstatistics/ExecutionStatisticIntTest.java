package com.transferwise.common.entrypoints.executionstatistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.transferwise.common.context.TwContext;
import com.transferwise.common.entrypoints.test.BaseIntTest;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
public class ExecutionStatisticIntTest extends BaseIntTest {

  @Test
  void executionStatisticsAreGathered() {
    TwContext.current().createSubContext().asEntryPoint("Test", "myEntryPoint").execute(() -> log.info("I'm inside an entrypoint!"));

    List<Meter> meters = meterRegistry.getMeters().stream().filter(m -> m.getId().getName().equals("EntryPoints.Es.timeTaken"))
        .collect(Collectors.toList());

    assertThat(meters.size()).isEqualTo(1);
    assertThat(meters.get(0).getId().getTag("epName")).isEqualTo("myEntryPoint");
    assertThat(meters.get(0).getId().getTag("epGroup")).isEqualTo("Test");
    assertThat(((Timer) meters.get(0)).count()).isEqualTo(1);
  }

  @Test
  void executionStatisticsAreGatheredEvenOnExceptions() {
    Assertions.assertThatThrownBy(() -> TwContext.current().createSubContext().asEntryPoint("Test", "myEntryPoint").execute(() -> {
      throw new RuntimeException("Something went wrong.");
    }));

    List<Meter> meters = meterRegistry.getMeters().stream().filter(m -> m.getId().getName().equals("EntryPoints.Es.timeTaken"))
        .collect(Collectors.toList());

    assertThat(meters.size()).isEqualTo(1);
    assertThat(meters.get(0).getId().getTag("epName")).isEqualTo("myEntryPoint");
    assertThat(meters.get(0).getId().getTag("epGroup")).isEqualTo("Test");
    assertThat(((Timer) meters.get(0)).count()).isEqualTo(1);
  }
}
