package com.transferwise.common.entrypoints.tableaccessstatistics;

import org.apache.commons.lang3.StringUtils;

public class DefaultTasSqlFilter implements TasSqlFilter {

  @Override
  public boolean skip(String sql) {
    if (StringUtils.startsWithIgnoreCase(sql, "SET statement_timeout TO")) {
      return true;
    }
    return false;
  }
}
