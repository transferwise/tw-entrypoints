package com.transferwise.common.entrypoints.tableaccessstatistics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import org.junit.jupiter.api.Test;

class SqlParserUtilsTest {

  @Test
  @SneakyThrows
  void testJSqlParser() {
    List<String> sqls = new ArrayList<>();

    sqls.add("insert into fin_unique_tw_task_key(task_id,key_hash,key) values(?, ?, ?) on conflict (key_hash, key) do nothing");

    // DATABASE is a keyword for sql parser.
    sqls.add("select DATABASE()");

    sqls.add("delete tl from tag_links tl, tags t where t.name=? and tl.type=? and tl.tag_ref=? and tl.tag_id = t.id");
    sqls.add("select table_rows from information_schema.tables where table_schema=DATABASE() and table_name = 'tw_task'");

    sqls.add("SHOW FULL TABLES IN fx WHERE TABLE_TYPE NOT LIKE 'VIEW'");

    // Skipped by SqlFilter, because sqlparser can not handle this.
    // sqls.add("SET statement_timeout TO '18000'");

    sqls.add("insert into fin_unique_tw_task_key(task_id,key_hash,key) values(?, ?, ?) on conflict (key_hash, key) do nothing");

    var sqlParser = new SqlParser(Executors.newCachedThreadPool());

    for (String sql : sqls) {
      try {
        var statements = sqlParser.parse(sql, Duration.ofSeconds(5));

        statements.getStatements();
      } catch (JSQLParserException e) {
        throw new RuntimeException("Failed to parse sql '" + sql + "'.", e);
      }
    }
  }

}
