package com.transferwise.common.entrypoints.tableaccessstatistics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.ParseException;
import net.sf.jsqlparser.statement.Statements;

@UtilityClass
public class SqlParserUtils {

  /*
    DATABASE is reserved keyword in sql parser, so having DATABASE() in sql will just throw error.
    DATABASE() is however used by some of our internal libraries for MariaDb.
   */
  private static final Pattern FUNCTION_REPLACEMENT_PATTERN = Pattern.compile("DATABASE()",
      Pattern.LITERAL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  public Statements parseToStatements(String sql) throws ParseException {
    sql = FUNCTION_REPLACEMENT_PATTERN.matcher(sql).replaceAll(Matcher.quoteReplacement("UNSUPPORTED()"));

    CCJSqlParser parser = CCJSqlParserUtil.newParser(sql).withAllowComplexParsing(false);

    return parser.Statements();
  }
}
