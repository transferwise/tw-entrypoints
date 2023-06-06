package com.transferwise.common.entrypoints.tableaccessstatistics;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
public class TestTasQueryParsingInterceptor extends DefaultTasQueryParsingInterceptor {

  @Getter
  @Setter
  private ParsedQuery parsedQuery;

  @Override
  public InterceptResult intercept(String sql) {
    if (parsedQuery != null) {
      return InterceptResult.returnParsedQuery(parsedQuery);
    }

    return super.intercept(sql);
  }
}
