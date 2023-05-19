package com.transferwise.common.entrypoints.tableaccessstatistics;

/**
 * Allows to register queries which are not parseable or very slow to parse.
 */
public interface TasParsedQueryRegistry {

  void register(String sql, ParsedQuery parsedQuery);

  ParsedQuery get(String sql);
}
