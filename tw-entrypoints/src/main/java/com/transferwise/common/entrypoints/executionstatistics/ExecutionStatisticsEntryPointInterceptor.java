package com.transferwise.common.entrypoints.executionstatistics;

import static com.transferwise.common.entrypoints.EntryPointsMetrics.METRIC_PREFIX_ENTRYPOINTS;

import com.transferwise.common.baseutils.clock.ClockHolder;
import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.baseutils.meters.cache.TagsSet;
import com.transferwise.common.context.TwContext;
import com.transferwise.common.context.TwContextExecutionInterceptor;
import com.transferwise.common.context.TwContextMetricsTemplate;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ExecutionStatisticsEntryPointInterceptor implements TwContextExecutionInterceptor {

  public static final String METRIC_PREFIX_ENTRYPOINTS_ES = METRIC_PREFIX_ENTRYPOINTS + "Es.";
  public static final String METRIC_TIME_TAKEN = METRIC_PREFIX_ENTRYPOINTS_ES + "timeTaken";

  private final IMeterCache meterCache;

  public ExecutionStatisticsEntryPointInterceptor(IMeterCache meterCache) {
    this.meterCache = meterCache;
    meterCache.getMeterRegistry().config().meterFilter(new EsMeterFilter());
  }

  @Override
  public boolean applies(TwContext context) {
    return context.isNewEntryPoint();
  }

  @Override
  public <T> T intercept(TwContext context, Supplier<T> supplier) {
    long startTimeMs = ClockHolder.getClock().millis();
    try {
      return supplier.get();
    } finally {
      TwContext twContext = TwContext.current();
      TagsSet tagsSet = TagsSet.of(TwContextMetricsTemplate.TAG_EP_GROUP, twContext.getGroup(),
          TwContextMetricsTemplate.TAG_EP_NAME, twContext.getName(),
          TwContextMetricsTemplate.TAG_EP_OWNER, twContext.getOwner());

      meterCache.timer(METRIC_TIME_TAKEN, tagsSet).record(ClockHolder.getClock().millis() - startTimeMs, TimeUnit.MILLISECONDS);
    }
  }
}
