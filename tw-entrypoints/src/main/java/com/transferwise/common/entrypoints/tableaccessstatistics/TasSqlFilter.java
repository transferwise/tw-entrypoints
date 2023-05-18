package com.transferwise.common.entrypoints.tableaccessstatistics;

public interface TasSqlFilter {

  boolean skip(String sql);
}
