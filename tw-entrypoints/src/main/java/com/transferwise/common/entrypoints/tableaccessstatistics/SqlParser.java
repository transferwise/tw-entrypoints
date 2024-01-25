package com.transferwise.common.entrypoints.tableaccessstatistics;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.StringProvider;
import net.sf.jsqlparser.statement.Statements;

public class SqlParser {

  private final ExecutorService executorService;

  public SqlParser(ExecutorService executorService) {
    this.executorService = executorService;
  }

  public Statements parse(String sql, Duration timeout) throws JSQLParserException {
    CCJSqlParser parser = newParser(sql).withAllowComplexParsing(true);
    return parseStatementAsync(parser, timeout);
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
