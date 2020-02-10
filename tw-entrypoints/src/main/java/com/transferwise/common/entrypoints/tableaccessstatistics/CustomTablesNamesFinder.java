package com.transferwise.common.entrypoints.tableaccessstatistics;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.SetStatement;
import net.sf.jsqlparser.statement.ShowColumnsStatement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.execute.Execute;
import net.sf.jsqlparser.util.TablesNamesFinder;

public class CustomTablesNamesFinder extends TablesNamesFinder {

  @Override
  protected String extractTableName(Table table) {
    return table.getName();
  }

  @Override
  public void visit(Drop drop) {
    visit(drop.getName());
  }

  @Override
  public void visit(CreateIndex createIndex) {
    visit(createIndex.getTable());
  }

  @Override
  public void visit(CreateView createView) {
    visit(createView.getView());
  }

  @Override
  public void visit(Alter alter) {
    visit(alter.getTable());
  }

  @Override
  public void visit(Statements stmts) {
  }

  @Override
  public void visit(Execute execute) {
  }

  @Override
  public void visit(SetStatement set) {
  }

  @Override
  public void visit(ShowColumnsStatement set) {
  }
}
