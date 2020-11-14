package com.transferwise.common.entrypoints;

public final class EntryPointsMetrics {

  private EntryPointsMetrics() {
    throw new AssertionError();
  }

  public static final String TAG_DATABASE = "db";
  public static final String METRIC_PREFIX_ENTRYPOINTS = "EntryPoints.";

  public static final long MS_TO_NS = 1_000_000L;
}
