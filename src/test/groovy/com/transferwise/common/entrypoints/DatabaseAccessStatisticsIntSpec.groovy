package com.transferwise.common.entrypoints

import com.transferwise.common.baseutils.context.TwContext
import com.transferwise.common.entrypoints.test.BaseIntSpec
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Timer
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

import javax.sql.DataSource
import java.util.concurrent.TimeUnit

class DatabaseAccessStatisticsIntSpec extends BaseIntSpec {
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate

    def setup() {
        jdbcTemplate = new JdbcTemplate(dataSource)
    }

    def "select gets registered in an entrypoint"() {
        when:
            TwContext.newSubContext().asEntryPoint("Test", "myEntryPoint").execute {
                jdbcTemplate.queryForList("select * from table_a", Long.class)
            }

            def meters = metersAsMap()
        then:
            ((DistributionSummary) meters["Registered.NTQueries"]).count() == 1
            ((DistributionSummary) meters["Registered.NTQueries"]).mean() == 1
            meters["Registered.NTQueries"].id.getTag("db") == "mydb"
            ((DistributionSummary) meters["Registered.TQueries"]).mean() == 0
            ((DistributionSummary) meters["Registered.MaxConcurrentConnections"]).mean() == 1
            ((DistributionSummary) meters["Registered.RemainingOpenConnections"]).mean() == 0
            ((Timer) meters["Registered.TimeTaken"]).mean(TimeUnit.NANOSECONDS) > 0
            ((DistributionSummary) meters["Registered.Commits"]).mean() == 0
            ((DistributionSummary) meters["Registered.Rollbacks"]).mean() == 0
        and:
            ((Counter) meters["Unknown.Commits"]).count() == 0
            ((Counter) meters["Unknown.NTQueries"]).count() == 0
            ((Counter) meters["Unknown.TQueries"]).count() == 0
    }

    def "select gets registered outside of an entrypoint"() {
        when:
            jdbcTemplate.queryForList("select * from table_a", Long.class)

            // Unknown context statistics will be converted to metrics on next entrypoints access.
            TwContext.newSubContext().asEntryPoint("group", "name").execute({});

            def meters = metersAsMap()
        then:
            meters["Registered.NTQueries"] == null
        and:
            ((Counter) meters["Unknown.Commits"]).count() == 0
            ((Counter) meters["Unknown.NTQueries"]).count() == 1
            ((Counter) meters["Unknown.TQueries"]).count() == 0
    }

    private Map<String, Meter> metersAsMap() {
        return meterRegistry.getMeters().findAll {
            it.id.name.startsWith("EntryPoints.Das")
        }.collectEntries {
            [StringUtils.substringAfter(it.id.name, "EntryPoints.Das."), it]
        }
    }
}
