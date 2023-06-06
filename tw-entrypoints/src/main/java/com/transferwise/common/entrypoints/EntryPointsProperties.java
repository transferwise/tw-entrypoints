package com.transferwise.common.entrypoints;

import java.time.Duration;
import lombok.Data;

@Data
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
    private FlywayIntegration flywayIntegration = new FlywayIntegration();

    @Data
    public static class SqlParser {

      private int cacheSizeMib = 50;
      private Duration timeout = Duration.ofSeconds(5);
      /**
       * If parsing takes longer than that, the service owner would want to know about it.
       */
      private Duration parseDurationWarnThreshold = Duration.ofSeconds(1);
      private boolean warnAboutFailedParses = true;
    }

    @Data
    public static class FlywayIntegration {

      private boolean enabled = true;
    }
  }

  @Data
  public static class Ts {

    private boolean enabled = true;
  }
}
