package com.transferwise.common.entrypoints.tableaccessstatistics;

import java.time.Duration;

public interface TasQueryParsingListener {

  void parsingDone(String sql, ParsedQuery parsedQuery, Duration timeTaken);

  void parsingFailed(String sql, Duration timeTaken, Throwable t);
}
