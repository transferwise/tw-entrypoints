package com.transferwise.common.entrypoints;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(value = "tw-entrypoints", ignoreUnknownFields = false)
public class EntryPointsProperties {

  private Das das = new Das();
  private Es es = new Es();
  private Tas tas = new Tas();
  private Ts ts = new Ts();

  @Data
  public static class Das {

    private boolean enabled = true;
  }

  @Data
  public static class Es {

    private boolean enabled = true;
  }

  @Data
  public static class Tas {

    private boolean enabled = true;
    private SqlParser sqlParser = new SqlParser();

    @Data
    public static class SqlParser {

      private int cacheSizeMib = 50;
    }
  }

  @Data
  public static class Ts {

    private boolean enabled = true;
  }
}
