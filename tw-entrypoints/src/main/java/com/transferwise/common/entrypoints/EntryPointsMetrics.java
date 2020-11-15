package com.transferwise.common.entrypoints;

public final class EntryPointsMetrics {

  private EntryPointsMetrics() {
    throw new AssertionError();
  }

  public static final String TAG_DATABASE = "db";
  public static final String TAG_TABLE = "table";
  public static final String TAG_OPERATION = "operation";
  public static final String TAG_IN_TRANSACTION = "inTransaction";
  public static final String TAG_SUCCESS = "success";
  public static final String TAG_TRANSACTION_NAME = "transactionName";
  public static final String TAG_READ_ONLY = "readOnly";
  public static final String TAG_ISOLATION_LEVEL = "isolationLevel";
  public static final String TAG_RESOLUTION = "resolution";
  public static final String TAG_RESOLUTION_SUCCESS = "resolutionSuccess";

  public static final String METRIC_PREFIX_ENTRYPOINTS = "EntryPoints.";

  public static final long MS_TO_NS = 1_000_000L;
}
