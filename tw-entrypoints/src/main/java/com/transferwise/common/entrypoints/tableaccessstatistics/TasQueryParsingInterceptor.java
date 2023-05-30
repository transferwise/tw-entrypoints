package com.transferwise.common.entrypoints.tableaccessstatistics;

import lombok.Data;
import lombok.experimental.Accessors;

public interface TasQueryParsingInterceptor {

  InterceptResult intercept(String sql);

  @Data
  @Accessors(chain = true)
  class InterceptResult {

    private Decision decision = Decision.CONTINUE;

    private ParsedQuery parsedQuery;

    public static InterceptResult doContinue() {
      return new InterceptResult().setDecision(Decision.CONTINUE);
    }

    public static InterceptResult doSkip() {
      return new InterceptResult().setDecision(Decision.SKIP);
    }

    public static InterceptResult returnParsedQuery(ParsedQuery parsedQuery) {
      return new InterceptResult().setDecision(Decision.CUSTOM_PARSED_QUERY).setParsedQuery(parsedQuery);
    }

    enum Decision {
      SKIP,
      CUSTOM_PARSED_QUERY,
      CONTINUE
    }
  }
}
