package com.transferwise.common.entrypoints

import com.github.benmanes.caffeine.cache.LoadingCache
import com.transferwise.common.entrypoints.tableaccessstatistics.TableAccessStatisticsSpyqlListener
import com.transferwise.common.entrypoints.test.BaseIntSpec
import com.transferwise.common.spyql.SpyqlDataSource
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

import javax.sql.DataSource

class TableAccessStatisticsIntSpec extends BaseIntSpec {
    @Autowired
    private EntryPoints entryPoints;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private MeterRegistry meterRegistry;

    private JdbcTemplate jdbcTemplate

    def setup() {
        jdbcTemplate = new JdbcTemplate(dataSource)

        meterRegistry.getMeters().findAll { it.id.name == "EntryPoints.Tas.TableAccess" }.forEach {
            meterRegistry.remove(it)
        }

        getParserCache().invalidateAll()
    }

    def "select to not existing table gets correctly registered"() {
        when:
            entryPoints.in("Test", "myEntryPoint", {
                try {
                    jdbcTemplate.queryForObject("select id from not_existing_table", Long.class)
                }
                catch (Exception ignored) {
                }
            })

            List<Meter> meters = meterRegistry.getMeters().findAll { it.id.name == "EntryPoints.Tas.TableAccess" }
        then:
            meters.size() == 1
            meters[0].id.getTag("success") == "false"
            meters[0].id.getTag("db") == "mydb"
            meters[0].id.getTag("inTransaction") == "false"
            meters[0].id.getTag("operation") == "select"
            meters[0].id.getTag("table") == "not_existing_table"
            meters[0].id.getTag("entryPointName") == "myEntryPoint"
            ((Counter) meters[0]).count() == 1
    }

    def "working update sql get correctly registered"() {
        when:
            entryPoints.in("Test", "myEntryPoint", {
                jdbcTemplate.update("update table_a set version=2")
            })

            List<Meter> meters = meterRegistry.getMeters().findAll { it.id.name == "EntryPoints.Tas.TableAccess" }
        then:
            meters.size() == 1
            meters[0].id.getTag("success") == "true"
            meters[0].id.getTag("db") == "mydb"
            meters[0].id.getTag("inTransaction") == "false"
            meters[0].id.getTag("operation") == "update"
            meters[0].id.getTag("table") == "table_a"
            meters[0].id.getTag("entryPointName") == "myEntryPoint"
            ((Counter) meters[0]).count() == 1
    }

    def "update sql done outside of entry point context gets also registered"() {
        when:
            jdbcTemplate.update("update table_a set version=2")
            List<Meter> meters = meterRegistry.getMeters().findAll { it.id.name == "EntryPoints.Tas.TableAccess" }
        then:
            meters.size() == 1
            meters[0].id.getTag("success") == "true"
            meters[0].id.getTag("db") == "mydb"
            meters[0].id.getTag("inTransaction") == "false"
            meters[0].id.getTag("operation") == "update"
            meters[0].id.getTag("table") == "table_a"
            meters[0].id.getTag("entryPointName") == "unknown"
            ((Counter) meters[0]).count() == 1
    }

    def "sql parser cache is used for same sqls"() {
        when:
            jdbcTemplate.update("update table_a set version=2")
            jdbcTemplate.update("update table_a set version=2")
            jdbcTemplate.update("update table_a set version=2")

            List<Meter> meters = meterRegistry.getMeters().findAll { it.id.name == "EntryPoints.Tas.TableAccess" }
        then:
            meters.size() == 1
            meters[0].id.getTag("success") == "true"
            meters[0].id.getTag("db") == "mydb"
            meters[0].id.getTag("inTransaction") == "false"
            meters[0].id.getTag("operation") == "update"
            meters[0].id.getTag("table") == "table_a"
            meters[0].id.getTag("entryPointName") == "unknown"
            ((Counter) meters[0]).count() == 3
        and:
            getParserCache().estimatedSize() == 1
    }

    private LoadingCache getParserCache() {
        SpyqlDataSource spyqlDataSource = dataSource.unwrap(SpyqlDataSource.class);
        TableAccessStatisticsSpyqlListener listener = spyqlDataSource.getDataSourceListeners().find { it instanceof TableAccessStatisticsSpyqlListener }

        return listener.sqlParseResultsCache
    }
}
