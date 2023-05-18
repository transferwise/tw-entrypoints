package com.transferwise.common.entrypoints.tableaccessstatistics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultTableAccessStatisticsParsedQueryRegistry implements TableAccessStatisticsParsedQueryRegistry {

  private final Map<String, ParsedQuery> sqlParseResults = new ConcurrentHashMap<>();

  @Override
  public void register(String sql, ParsedQuery parsedQuery) {
    sqlParseResults.put(sql, parsedQuery);
  }

  @Override
  public ParsedQuery get(String sql) {
    return sqlParseResults.get(sql);
  }
}
