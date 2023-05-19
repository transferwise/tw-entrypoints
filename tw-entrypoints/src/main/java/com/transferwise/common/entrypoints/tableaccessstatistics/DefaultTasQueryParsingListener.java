package com.transferwise.common.entrypoints.tableaccessstatistics;

import com.transferwise.common.entrypoints.EntryPointsProperties;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultTasQueryParsingListener implements TasQueryParsingListener {

  public static final String FAILED_PARSING_SUGGESTION = " You may want to use `TableAccessStatisticsParsedQueryRegistry` "
      + "or provide `DefaultTasQueryParsingInterceptor` implementation to provide the "
      + "query parse result manually.";
  public static final String FAILED_PARSING_LOG_MESSAGE = "Parsing statement '{}' failed." + FAILED_PARSING_SUGGESTION;
  public static final String SLOW_PARSING_LOG_MESSAGE = "Statement '{}' parsing took {} ms." + FAILED_PARSING_LOG_MESSAGE;


  private EntryPointsProperties entryPointsProperties;

  public DefaultTasQueryParsingListener(EntryPointsProperties entryPointsProperties) {
    this.entryPointsProperties = entryPointsProperties;
  }

  @Override
  public void parsingDone(String sql, ParsedQuery parsedQuery, Duration timeTaken) {
    handleParsingDuration(sql, timeTaken);
  }


  @Override
  public void parsingFailed(String sql, Duration timeTaken, Throwable t) {
    if (entryPointsProperties.getTas().getSqlParser().isWarnAboutFailedParses()) {
      log.warn(FAILED_PARSING_LOG_MESSAGE, sql, t);
    } else {
      log.debug(FAILED_PARSING_LOG_MESSAGE, sql, t);
    }

    handleParsingDuration(sql, timeTaken);
  }

  protected void handleParsingDuration(String sql, Duration timeTaken) {
    if (timeTaken.toMillis() > entryPointsProperties.getTas().getSqlParser().getParseDurationWarnThreshold().toMillis()) {
      log.warn(SLOW_PARSING_LOG_MESSAGE, sql, timeTaken.toMillis());
    }
  }
}
