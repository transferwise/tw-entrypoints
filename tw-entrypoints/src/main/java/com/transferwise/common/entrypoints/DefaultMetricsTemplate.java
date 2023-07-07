package com.transferwise.common.entrypoints;

import com.transferwise.common.baseutils.meters.cache.IMeterCache;
import io.micrometer.core.instrument.Gauge;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultMetricsTemplate implements MetricsTemplate {

  public static final String GAUGE_LIBRARY_INFO = "tw.library.info";

  private final IMeterCache meterCache;

  @Override
  public void registerLibrary() {
    String version = this.getClass().getPackage().getImplementationVersion();
    if (version == null) {
      version = "Unknown";
    }

    Gauge.builder(GAUGE_LIBRARY_INFO, () -> 1d).tags("version", version, "library", "tw-entrypoints")
        .description("Provides metadata about the library, for example the version.")
        .register(meterCache.getMeterRegistry());

  }
}
