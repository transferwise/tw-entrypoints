package com.transferwise.common.entrypoints.tableaccessstatistics;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.StringProvider;
import net.sf.jsqlparser.statement.Statements;

public class SqlParser {

  /*
    DATABASE is reserved keyword in sql parser, so having DATABASE() in sql will just throw error.
    DATABASE() is however used by some of our internal libraries for MariaDb.
   */
  private static final Pattern FUNCTION_REPLACEMENT_PATTERN = Pattern.compile("DATABASE()",
      Pattern.LITERAL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern ON_CONFLICT_REPLACEMENT_PATTERN = Pattern.compile("on\\s*conflict\\s*\\(.*?\\)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
  private final ExecutorService executorService;

  public SqlParser(ExecutorService executorService) {
    this.executorService = executorService;
  }

  public Statements parse(String sql, Duration timeout) throws JSQLParserException {
    sql = replaceFunctions(sql);
    sql = replaceOnConflicts(sql);

    CCJSqlParser parser = newParser(sql).withAllowComplexParsing(true);
    return parseStatementAsync(parser, timeout);
  }

  // Sqlparser 4.6 does not support "on conflict" clause with multiple parameters.
  // As a workaround, we change the query to a single parameter clause.
  protected String replaceOnConflicts(String sql) {
    var matcher = ON_CONFLICT_REPLACEMENT_PATTERN.matcher(sql);

    // 99.99% of sqls don't have it, so let's avoid new string creation for those.
    if (matcher.find()) {
      matcher.reset();
      return matcher.replaceAll(Matcher.quoteReplacement("on conflict (blah)"));
    }

    return sql;
  }

  protected String replaceFunctions(String sql) {
    var matcher = FUNCTION_REPLACEMENT_PATTERN.matcher(sql);

    // 99.99% of sqls don't have it, so let's avoid new string creation for those.
    if (matcher.find()) {
      matcher.reset();
      return matcher.replaceAll(Matcher.quoteReplacement("UNSUPPORTED()"));
    }

    return sql;
  }

  protected Statements parseStatementAsync(CCJSqlParser parser, Duration timeout) throws JSQLParserException {
    try {
      var future = executorService.submit(parser::Statements);
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      parser.interrupted = true;
      throw new JSQLParserException("Time out occurred.", ex);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JSQLParserException(e);
    } catch (Exception ex) {
      throw new JSQLParserException(ex);
    }
  }

  protected CCJSqlParser newParser(String sql) {
    return new CCJSqlParser(new StringProvider(sql));
  }

}
