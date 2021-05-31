# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.5.0] - 2021-05-31

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