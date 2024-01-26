package com.transferwise.common.entrypoints.tableaccessstatistics;

import com.transferwise.common.entrypoints.tableaccessstatistics.TasQueryParsingInterceptor.InterceptResult.Decision;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class SqlParserUtilsTest {

  @Test
  @SneakyThrows
  void testJSqlParser() {
    final List<TestCase> testCases = new ArrayList<>();

    testCases.add(new TestCase("insert into fin_unique_tw_task_key(task_id,key_hash,key) values(?, ?, ?) on conflict (key_hash, key) do nothing",
        List.of("fin_unique_tw_task_key")));

    // DATABASE is a keyword for sql parser.
    testCases.add(new TestCase("select DATABASE()",
        List.of()));

    testCases.add(new TestCase("delete tl from tag_links tl, tags t where t.name=? and tl.type=? and tl.tag_ref=? and tl.tag_id = t.id",
        List.of("tag_links", "tags")));
    testCases.add(new TestCase("select table_rows from information_schema.tables where table_schema=DATABASE() and table_name = 'tw_task'",
        List.of("information_schema.tables")));

    testCases.add(new TestCase("SHOW FULL TABLES IN fx WHERE TABLE_TYPE NOT LIKE 'VIEW'",
        List.of()));

    testCases.add(new TestCase("SET statement_timeout TO '18000'",
        List.of()));
    testCases.add(new TestCase("set idle_in_transaction_session_timeout to '600000'",
        List.of()));

    testCases.add(new TestCase("insert into fin_unique_tw_task_key(task_id,key_hash,key) values(?, ?, ?) on conflict (key_hash, key) do nothing",
        List.of("fin_unique_tw_task_key")));

    testCases.add(new TestCase("UPDATE ninjas_wkp_partition_locks USE INDEX (uqidx1) set deadline=?",
        List.of("ninjas_wkp_partition_locks")));

    testCases.add(new TestCase("truncate table_a; truncate table_b",
        List.of("table_a", "table_b")));

    // Not supported by JSqlParser
    // testCases.add(new TestCase("truncate table_a, table_b",
    //    List.of("table_a", "table_b")));

    var sqlParser = new SqlParser(Executors.newCachedThreadPool());
    var interceptor = new DefaultTasQueryParsingInterceptor();

    for (TestCase testCase : testCases) {
      try {
        var interceptResult = interceptor.intercept(testCase.getSql());
        if (interceptResult.getDecision() == Decision.SKIP) {
          continue;
        }

        var statements = sqlParser.parse(testCase.getSql(), Duration.ofSeconds(5));

        List<String> tables = new ArrayList<>();

        for (var stmt : statements) {
          var tablesNamesFinder = new CustomTablesNamesFinder();
          try {
            tablesNamesFinder.getTables(stmt);
          } catch (UnsupportedOperationException ignored) {
            //ignored
          }
          tables.addAll(tablesNamesFinder.getTables());
        }

        Assertions.assertEquals(testCase.getExpectedTables(), tables);

      } catch (JSQLParserException e) {
        throw new RuntimeException("Failed to parse sql '" + testCase.getSql() + "'.", e);
      }
    }
  }

  @RequiredArgsConstructor
  @Data
  private static class TestCase {

    private final String sql;
    private final List<String> expectedTables;
  }
}
