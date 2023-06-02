# Tw-EntryPoints documentation

## Table of Contents

* [Introduction](#intro)
* [Setup](#setup)
* [Integration tests](#integration-tests)
* [License](#license)
* [Contribution Guide](contributing.md)

## Intro

Provides various metrics for service's databases usage.

Is built on top of

- https://github.com/transferwise/tw-context
- https://github.com/transferwise/tw-spyql

Integrates nicely with Transferwise Entrypoints system.

Example dashboards:

- [EntryPoints Database Access V3](https://dashboards.tw.ee/d/f6l4lrUWz/entrypoints-database-access-v3?orgId=1)
- [EntryPoints Table Access V2](https://dashboards.tw.ee/d/dyp0u9UZz/entrypoints-table-access-v2?orgId=1)

## Setup

Assuming, your data sources are using [HikariCP](https://github.com/brettwooldridge/HikariCP), only thing you need to do, is to add a dependency.

```groovy
runtimeOnly "com.transferwise.common:tw-entrypoints-starter"
```

For best results, it is also recommended to integrate with [TW Service Comms](https://github.com/transferwise/tw-service-comms).

## Integration tests

You can also use Database Access Statistics in test code, for example to verify that your hibernate magic code does not have any N+1 problem:

```groovy
unitOfWorkManager.createEntryPoint("test", "test").toContext().execute({
  mvcResult = getMockMvc()
      .perform(post("/v1/batchPayouts/getBatchList")
          .contentType(MediaType.APPLICATION_JSON)
          .content(jsonConverter.fromObject(request)))
      .andReturn()
  dbStats = DatabaseAccessStatistics.get("payout")

  assert dbStats.getTransactionalQueriesCount() == 1
  assert dbStats.getNonTransactionalQueriesCount() == 0
})
```

You would need to add a dependency for this as well:

```groovy
testImplementation "com.transferwise.common:tw-entrypoints"
```

## Table access statistics and `JSqlParser` library

We are using [JSqlParser](https://github.com/JSQLParser/JSqlParser) library to parse table names from queries.

The library is pretty good, but some services have few queries, it can not parse. Also, sometimes the parsing can take so long,
that it will create latency spikes or cpu burns.

In those case, you can override/control the parsing via `TasQueryParsingInterceptor` and `TasParsedQueryRegistry`.

Example for `TasQueryParsingInterceptor`.

<!-- @formatter:off -->
```java
public class MyTasQueryParsingInterceptor extends DefaultTasQueryParsingInterceptor {

  @Override
  public InterceptResult intercept(String sql) {
    if (StringUtils.startsWithIgnoreCase(sql, "SET fancy_variable TO")) {
      return InterceptResult.doSkip();
    }

    else if (sql.equals(knownUnParseableSql)){
      return InterceptResult.returnParsedQuery(new ParsedQuery()
          .addOperation("insert",new SqlOperation()
              // Main table should always be first, as we register "first-table" metrics by that.
              .addTable("transfer")
              .addTable("payout")));
    }

    return super.intercept(sql);
  }
}
```
<!-- @formatter:on -->

Example for `TasParsedQueryRegistry`.

<!-- @formatter:off -->
```java
@Autowired
private TasParsedQueryRegistry registry;

public void registerBadSqls(){
    registry.register(knownUnParseableSql,new ParsedQuery()
      .addOperation("insert",new SqlOperation()
         .addTable("transfer")
         .addTable("payout")));
}
```
<!-- @formatter:on -->

In case where failed parsing will create too much logs noise, you have an option to override `TasQueryParsingListener`.

For example:

```java
public class MyTasQueryParsingListener extends DefaultTasQueryParsingListener {

  @Override
  public void parsingFailed(String sql, Duration timeTaken, Throwable t) {
    if (sql.equals(knownProblematicQuery)) {
      // ignore
    } else {
      super.parsingFailed(sql, timeTaken, t);
    }
  }
}
```

## License

Copyright 2021 TransferWise Ltd.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy
of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under
the License.