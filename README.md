# Tw EntryPoints

![Apache 2](https://img.shields.io/hexpm/l/plug.svg)
![Java 11](https://img.shields.io/badge/Java-11-blue.svg)
![Maven Central](https://badgen.net/maven/v/maven-central/com.transferwise.common/tw-entrypoints)
[![Owners](https://img.shields.io/badge/team-AppEng-blueviolet.svg?logo=wise)](https://transferwise.atlassian.net/wiki/spaces/EKB/pages/2520812116/Application+Engineering+Team) [![Slack](https://img.shields.io/badge/slack-sre--guild-blue.svg?logo=slack)](https://app.slack.com/client/T026FB76G/CLR1U8SNS)
> Use the `@application-engineering-on-call` handle on Slack for help.
---

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

## License

Copyright 2021 TransferWise Ltd.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy
of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under
the License.