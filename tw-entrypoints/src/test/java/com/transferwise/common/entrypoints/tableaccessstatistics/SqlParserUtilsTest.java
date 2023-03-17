package com.transferwise.common.entrypoints.tableaccessstatistics;

import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import net.sf.jsqlparser.parser.ParseException;
import org.junit.jupiter.api.Test;

public class SqlParserUtilsTest {

  @Test
  @SneakyThrows
  void testJSqlParser() {
    List<String> sqls = new ArrayList<>();

    // DATABASE is a keyword for sql parser.
    sqls.add("select DATABASE()");

    sqls.add("delete tl from tag_links tl, tags t where t.name=? and tl.type=? and tl.tag_ref=? and tl.tag_id = t.id");
    sqls.add("select table_rows from information_schema.tables where table_schema=DATABASE() and table_name = 'tw_task'");

    for (String sql : sqls) {
      try {
        SqlParserUtils.parseToStatements(sql);
      } catch (ParseException e) {
        throw new RuntimeException("Failed to parse sql '" + sql + "'.", e);
      }
    }
  }

}
