package com.transferwise.common.entrypoints.tableaccessstatistics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.util.TablesNamesFinder;

public class CustomTablesNamesFinder extends TablesNamesFinder {

  @Getter
  private final List<String> tables = new ArrayList<>();
  private final Set<String> uniqueTables = new HashSet<>();

  /*
    The super class loses the order of tables visited, as its tables list is based on Set.

    We are providing here the option to get the ordered tables list.
   */
  @Override
  public void visit(Table tableName) {
    String tableWholeName = extractTableName(tableName);

    if (!uniqueTables.contains(tableWholeName)) {
      uniqueTables.add(tableWholeName);
      tables.add(tableWholeName);
    }

    super.visit(tableName);
  }
}
