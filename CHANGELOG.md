# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
