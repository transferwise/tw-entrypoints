package com.transferwise.common.entrypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.transferwise.common.entrypoints.test.BaseIntTest;
import org.junit.jupiter.api.Test;

class LibraryMetricsIntTest extends BaseIntTest {

  @Test
  void libraryVersionIsProvided() {
    var gauge = meterRegistry.find("tw.library.info").tags("library", "tw-entrypoints").gauge();
    assertEquals(1, gauge.value());
    assertNotNull(gauge.getId().getTag("version"));
  }
}
