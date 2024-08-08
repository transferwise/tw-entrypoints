package com.transferwise.common.entrypoints.executionstatistics;

import com.transferwise.common.baseutils.clock.ClockHolder;
import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import com.transferwise.common.baseutils.meters.cache.TagsSet;
import com.transferwise.common.context.TwContext;
import com.transferwise.common.context.TwContextExecutionInterceptor;
import com.transferwise.common.context.TwContextMetricsTemplate;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ExecutionStatisticsEntryPointInterceptor implements TwContextExecutionInterceptor {

  public static final String METRIC_PREFIX_ENTRYPOINTS_ES = "EntryPoints_Es_";
  public static final String TIMER_TIME_TAKEN = "EntryPoints_Es_timeTaken";

  private final IMeterCache meterCache;

  public ExecutionStatisticsEntryPointInterceptor(IMeterCache meterCache) {
    this.meterCache = meterCache;
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

      meterCache.timer(TIMER_TIME_TAKEN, tagsSet).record(ClockHolder.getClock().millis() - startTimeMs, TimeUnit.MILLISECONDS);
    }
  }
}
