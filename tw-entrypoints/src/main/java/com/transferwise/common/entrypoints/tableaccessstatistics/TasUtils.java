package com.transferwise.common.entrypoints.tableaccessstatistics;

import com.transferwise.common.context.TwContext;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TasUtils {

  public static final String TAS_QUERY_PARSING_DISABLED = "TW_TAS_QUERY_PARSING_DISABLED";

  public void disableQueryParsing(TwContext context) {
    context.put(TAS_QUERY_PARSING_DISABLED, Boolean.TRUE);
  }

  public void enableQueryParsing(TwContext context) {
    context.put(TAS_QUERY_PARSING_DISABLED, null);
  }

  public boolean isQueryParsingEnabled(TwContext context) {
    Boolean contextValue = context.get(TAS_QUERY_PARSING_DISABLED);
    return !Boolean.TRUE.equals(contextValue);
  }
}
