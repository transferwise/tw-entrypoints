package com.transferwise.common.entrypoints

import com.transferwise.common.baseutils.context.TwContext
import com.transferwise.common.entrypoints.test.BaseIntSpec
import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class ExecutionStatisticsIntSpec extends BaseIntSpec {
	@Autowired
	private MeterRegistry meterRegistry;

	def "execution statistics are gathered"() {
		when:
			TwContext.newSubContext().asEntryPoint("Test", "myEntryPoint").execute {
				log.info("I'm inside an entrypoint!")
			}

			List<Meter> meters = meterRegistry.getMeters().findAll { it.id.name == "EntryPoints.Es.timeTaken" }
		then:
			meters.size() == 1
			meters[0].id.getTag("entryPointName") == "myEntryPoint"
			meters[0].id.getTag("entryPointGroup") == "Test"
			((Timer) meters[0]).count() == 1
	}

	def "execution statistics are gathered even on exceptions"() {
		when:
			TwContext.newSubContext().asEntryPoint("Test", "myEntryPoint").execute {
				throw new Throwable("Something went wrong.")
			}
		then:
			Throwable t = thrown(Throwable)
		when:
			List<Meter> meters = meterRegistry.getMeters().findAll { it.id.name == "EntryPoints.Es.timeTaken" }
		then:
			meters.size() == 1
			meters[0].id.getTag("entryPointName") == "myEntryPoint"
			meters[0].id.getTag("entryPointGroup") == "Test"
			((Timer) meters[0]).count() == 1
	}
}
