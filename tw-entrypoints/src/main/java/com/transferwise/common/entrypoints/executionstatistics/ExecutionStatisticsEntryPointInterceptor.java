package com.transferwise.common.entrypoints.executionstatistics;

import static com.transferwise.common.entrypoints.EntryPointsMetricUtils.METRIC_PREFIX_ENTRYPOINTS;

import com.transferwise.common.baseutils.clock.ClockHolder;
import com.transferwise.common.context.TwContext;
import com.transferwise.common.context.TwContextExecutionInterceptor;
import com.transferwise.common.context.TwContextMetrics;
import com.transferwise.common.entrypoints.EntryPointsMetricUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ExecutionStatisticsEntryPointInterceptor implements TwContextExecutionInterceptor {

  private final MeterRegistry meterRegistry;

  public ExecutionStatisticsEntryPointInterceptor(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
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
      String name = EntryPointsMetricUtils.normalizeNameForMetric(twContext.getName());
      String group = EntryPointsMetricUtils.normalizeNameForMetric(twContext.getGroup());

      Tags tags = Tags.of(TwContextMetrics.TAG_EP_NAME, name, TwContextMetrics.TAG_EP_GROUP, group);
      EntryPointsMetricUtils
          .timerWithoutBuckets(meterRegistry, METRIC_PREFIX_ENTRYPOINTS + "Es.timeTaken", tags)
          .record(ClockHolder.getClock().millis() - startTimeMs, TimeUnit.MILLISECONDS);
    }
  }
}
