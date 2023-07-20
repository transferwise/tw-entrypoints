package com.transferwise.common.entrypoints.tableaccessstatistics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ParsedQuery {

  private Map<String, SqlOperation> operations = new HashMap<>();

  public ParsedQuery addOperation(String operationName, SqlOperation operation) {
    operations.put(operationName, operation);
    return this;
  }

  @Data
  @Accessors(chain = true)
  public static class SqlOperation {

    private Set<String> tableNames = new HashSet<>();

    public SqlOperation addTable(String table) {
      tableNames.add(table);
      return this;
    }
  }
}
