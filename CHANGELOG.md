# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.13.3] - 2023-07-19

### Fixed

* Visibility of `ParsedQuery.SqlOperation` is changed from package protected to public

## [2.13.2] - 2023-07-10

### Fixed

* Runtime dependencies are correctly added into POM, for `tw-entrypoints` module.

## [2.13.1] - 2023-07-07

### Added

* `tw.library.info` metric to provide the version.

## [2.13.0] - 2023-07-04

### Changed

* Shadowing and inlining jsqlparser library until spring-data allows to disable `JSqlParserQueryEnhancer` by other means.
  https://github.com/spring-projects/spring-data-jpa/issues/2989

## [2.12.1] - 2023-06-21

### Changed

* Removing quotes around table names provided by JSqlParser.
  When table name is quoted in sql, it will be returned by JSqlParser also as quoted. But this will create double metrics from
  sql not having quotes around table names.

## [2.12.0] - 2023-06-05

### Changed

* Handling more sql-s the JSqlParser can not parse.

### Added

* A mechanism to disable query parsing through `TwContext` - `TasUtils`.

* Flyway integration for TAS.
  As JSqlParser is not able to parse DDL queries, we will disable the query parsing for Flyway.

## [2.11.0] - 2023-05-18

### Changed

* Implemented a timeout and interruption for TAS SQL parsing.
  Complex queries in one of our services created long duration heavy CPU burn.

* Query parsing will use `JSQLParser` complex parsing immediately.
  Before, our implementation was using simple parsing. And `JSQLParser` implementation tried by default `simple` first
  and then `complex`, if `simple` failed.
  Performance tests showed `simple` parsing for simple queries, is not noticeably faster for simple queries.

* Created a mechanism for a service to provide parsed query information itself and thus skip the query parsing.
  It can be used for complex queries where parsing is slow or for queries which jsqlparser can not handle.
  The mechanism can be used via `TableAccessStatisticsParsedQueryRegistry` interface.

* Added more flexibility around query parsing via `TasQueryParsingListener` and `TasQueryParsingInterceptor`.

* Supporting parsing queries with `on conflict (...)` clause with multiple parameters.
  We can remove our own solution, when next `JSQLParser` version would support it.

## [2.10.0] - 2023-05-09

### Added

* Support for Spring Boot 3.0.

### Removed

* Support for Spring Boot 2.5.

## [2.9.0] - 2023-05-03

### Added

* Counter `EntryPoints_Tas_Parses`, to count per entrypoint, how many parses have been done.
  It allows to identify, which service components are generating most distinct queries and thus bloating the cache or just burning CPU for parses.

### Changed

* DAS for database access done outside of entrypoints are now collected in a separate thread.
  This would allow to collect those metrics even when the service does not have any entrypoints or they are
  infrequently accessed.

## [2.8.3] - 2023-03-16

### Fixed

* A bug where bean postprocessor was returning a wrong bean - unwrapped spyql datasource.

## [2.8.2] - 2023-03-16

### Added

* Counter `EntryPoints_Tas_UncountedQueries`, to count how many queries we were not able to parse table information from.

* Table access statistics can parse tables on queries containing `DATABASE()` function.

### Fixed

* JSqlParser is utilized in a more optimal way, by not executing the parsing in separate threads.

## [2.8.1] - 2023-03-16

### Fixed

* Hardcoding versions coming from BOMs into POM.
  That way older dependency resolvers can work without issues as well.
  Overall this is the most correct way to generate the POM.

### Changed

* When SQL parsing fails, we will also add entrypoint dimension into the failure metric, to make it clearer where a
  problematic SQL may come from.

* Refactored metrics' constants to a form easily copyable to Grafana.

## [2.8.0] - 2023-02-14

### Changed

* Specifying an order for bean post processor, so it will run after Spyql bean post processor.

### Removed

* `tw-entrypoints-starter` does not add Spyql data source wrapper anymore.
  This is now delegated to `tw-spyql-starter`.

### Added

* Spring Boot matrix tests.

## [2.7.1] - 2022-02-01

* Stop enforcing springboot platform

## [2.7.0] - 2021-06-11

* Migrating CI to Github Actions.
* Some dependency upgrades.

## [2.6.0] - 2021-06-11

### Changed

* Upgraded tw-spyql

## [2.5.0] - 2021-05-31

### Changed

* JDK 11+ is required
* Facelift for open-source.

## [2.4.1] - 2021-03-14

### Changed

* Small optimizations around tags memory allocations.

## [2.4.0] - 2021-03-01

### Changed

* Major optimization around metrics handling, added MeterCache around every metric.

## [2.3.0] - 2020-11-15

### Fixed

Optimized metrics registration speed.

Added query latency histogram for first table in the query.
