package com.transferwise.common.entrypoints.tableaccessstatistics;

import org.apache.commons.lang3.StringUtils;

public class DefaultTasQueryParsingInterceptor implements TasQueryParsingInterceptor {

  @Override
  public InterceptResult intercept(String sql) {
    if (StringUtils.startsWithIgnoreCase(sql, "SET statement_timeout TO")) {
      return InterceptResult.doSkip();
    }

    return InterceptResult.doContinue();
  }
}
