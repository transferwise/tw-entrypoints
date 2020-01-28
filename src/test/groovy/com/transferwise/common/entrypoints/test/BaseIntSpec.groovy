package com.transferwise.common.entrypoints.test

import com.transferwise.common.baseutils.context.TwContext
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.junit.Rule
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootContextLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@ActiveProfiles(profiles = ["integration"])
@SpringBootTest(classes = [TestApplication])
@ContextConfiguration(loader = SpringBootContextLoader, initializers = DatabaseContainerInitializer)
abstract class BaseIntSpec extends Specification {
    @Autowired
    protected MeterRegistry meterRegistry;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule()

    def setup() {
        TwContext.newSubContext().asEntryPoint("Test", "myEntryPoint").execute {
            // Resetting unknown context counters
        }
        meterRegistry.getMeters().findAll { it.id.name.startsWith("EntryPoints") && !(it instanceof Gauge) }.forEach {
            meterRegistry.remove(it)
        }
    }
}
