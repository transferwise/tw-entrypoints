package com.transferwise.common.entrypoints.test


import org.junit.Rule
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.springframework.boot.test.context.SpringBootContextLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@ActiveProfiles(profiles = ["integration"])
@SpringBootTest(classes = [TestApplication])
@ContextConfiguration(loader = SpringBootContextLoader, initializers = DatabaseContainerInitializer)
abstract class BaseIntSpec extends Specification {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule()
}
