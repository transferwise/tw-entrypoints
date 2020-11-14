package com.transferwise.common.entrypoints.executionstatistics;

import static com.transferwise.common.entrypoints.EntryPointsMetrics.METRIC_PREFIX_ENTRYPOINTS;

import com.transferwise.common.baseutils.clock.ClockHolder;
import com.transferwise.common.context.TwContext;
import com.transferwise.common.context.TwContextExecutionInterceptor;
import com.transferwise.common.context.TwContextMetricsTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ExecutionStatisticsEntryPointInterceptor implements TwContextExecutionInterceptor {

  public static final String METRIC_PREFIX_ENTRYPOINTS_ES = METRIC_PREFIX_ENTRYPOINTS + "Es.";

  private final MeterRegistry meterRegistry;

  public ExecutionStatisticsEntryPointInterceptor(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    meterRegistry.config().meterFilter(new EsMeterFilter());
  }

  @Override
  public boolean applies(TwContext context) {
    return context.getNew(TwContext.NAME_KEY) != null;
  }

  @Override
  public <T> T intercept(TwContext context, Supplier<T> supplier) {
    long startTimeMs = ClockHolder.getClock().millis();
    try {
      return supplier.get();
    } finally {
      TwContext twContext = TwContext.current();

      Tags tags = Tags.of(TwContextMetricsTemplate.TAG_EP_GROUP, twContext.getGroup(), TwContextMetricsTemplate.TAG_EP_NAME, twContext.getName(),
          TwContextMetricsTemplate.TAG_EP_OWNER, twContext.getOwner());

      meterRegistry.timer(METRIC_PREFIX_ENTRYPOINTS_ES + "timeTaken", tags)
          .record(ClockHolder.getClock().millis() - startTimeMs, TimeUnit.MILLISECONDS);
    }
  }
}
