package com.transferwise.common.entrypoints.tableaccessstatistics;

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public class DefaultTasQueryParsingInterceptor implements TasQueryParsingInterceptor {

  private static final Pattern PG_SET_PATTERN = Pattern.compile("(?is)SET\\s+[a-z_]*\\s+TO\\s+.*|EXPLAIN\\s+.*|SHOW DATABASES\\s+.*");

  @Override
  public InterceptResult intercept(String sql) {
    sql = StringUtils.trim(sql);
    if (PG_SET_PATTERN.matcher(sql).matches()) {
      return InterceptResult.doSkip();
    }

    return InterceptResult.doContinue();
  }

}
