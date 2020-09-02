# Tw EntryPoints

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
