package com.omnixys.transaction.util;

import com.omnixys.transaction.models.dto.ServiceValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(Constants.class);

  public static final String PROBLEM_PATH = "/problem";
  public static final String TRANSACTION_PATH = "/transaction";
  public static final String AUTH_PATH = "/auth";
  public static final String GRAPHQL_ENDPOINT = "/graphql";

  public static final String ID_PATTERN = "[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}";
  public static final String VERSION_NUMBER_MISSING = "Versionsnummer fehlt";

  private static final String ACCOUNT_SCHEMA_ENV = System.getenv("ACCOUNT_SERVICE_SCHEMA");
  private static final String ACCOUNT_HOST_ENV = System.getenv("ACCOUNT_SERVICE_HOST");
  private static final String ACCOUNT_PORT_ENV = System.getenv("ACCOUNT_SERVICE_PORT");

    private static final String ACCOUNT_SCHEMA = ACCOUNT_SCHEMA_ENV == null ? "http" : ACCOUNT_SCHEMA_ENV;
  private static final String ACCOUNT_HOST = ACCOUNT_HOST_ENV == null ? "localhost" : ACCOUNT_HOST_ENV;

  /**
   * Liefert ein `ServiceValue`-Objekt für einen angegebenen Service.
   * @param service Name des Service (z.B. "account")
   * @return ServiceValue mit `schema`, `host` und `port`
   */
  public static ServiceValue getServiceValue(final String service) {
    return switch (service) {
      case "account" -> {
        int port;
          int defaultPort = 9001;
          try {
          port = ACCOUNT_PORT_ENV == null ? defaultPort  : Integer.parseInt(ACCOUNT_PORT_ENV);
        } catch (NumberFormatException e) {
          LOGGER.warn("ACCOUNT_SERVICE_PORT ist ungültig: '{}'. Fallback auf {}.", ACCOUNT_PORT_ENV, defaultPort );
          port = defaultPort ;
        }
       yield new ServiceValue(
            ACCOUNT_SCHEMA,
            ACCOUNT_HOST,
            port
        );
      }
      default -> throw new IllegalStateException("Unbekannter Service: " + service);
    };
  }

  // Verhindert, dass diese Klasse instanziiert wird
  private Constants() {
    throw new UnsupportedOperationException("Diese Klasse darf nicht instanziiert werden.");
  }
}
