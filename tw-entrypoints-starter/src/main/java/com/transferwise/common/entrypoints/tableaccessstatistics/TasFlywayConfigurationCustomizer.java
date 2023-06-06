package com.transferwise.common.entrypoints.tableaccessstatistics;

import com.transferwise.common.context.TwContext;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;

/**
 * Turns off query parsing during Flyway migration.
 *
 * <p>JSQLParser library we use, is not able to parse DDL queries.
 */
@Slf4j
public class TasFlywayConfigurationCustomizer implements FlywayConfigurationCustomizer {

  boolean queryParsingWasDisabled;
  boolean queryParsingWasEnabled;

  @Override
  public void customize(FluentConfiguration configuration) {
    configuration.callbacks(new Callback() {
      @Override
      public boolean supports(Event event, Context context) {
        return true;
      }

      @Override
      public boolean canHandleInTransaction(Event event, Context context) {
        return true;
      }

      @Override
      public void handle(Event event, Context context) {
        if (event == Event.BEFORE_MIGRATE) {
          log.info("Disabling TAS query parsing before Flyway migration.");

          var twContext = TwContext.current().createSubContext().asEntryPoint("Flyway", "Flyway");
          twContext.attach();

          TasUtils.disableQueryParsing(twContext);

          queryParsingWasDisabled = true;
        } else if (event == Event.AFTER_MIGRATE) {
          log.debug("Enabling TAS query parsing after Flyway migration.");
          TwContext.current().detach(TwContext.current().getParent());

          queryParsingWasEnabled = true;
        }
      }

      @Override
      public String getCallbackName() {
        return "tw-entrypoints-tas";
      }
    });
  }
}
