package uk.qe.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * One place for every setting the tests need.
 *
 * The environment is chosen with -Denv=name (or TEST_ENV), and its settings are read
 * from src/test/resources/environments/name.properties. The default is "demo".
 *
 * Each value is looked up in this order:
 *   1. a JVM system property, e.g. mvn test -Dheadless=false
 *   2. an environment variable, e.g. ORANGEHRM_PASSWORD (how CI passes secrets)
 *   3. the environment's properties file
 *
 * There are no URLs or credentials in this class. If a setting is missing everywhere,
 * the run stops with a message saying which setting and how to supply it.
 */
public final class TestConfig {

    private static final String DEFAULT_ENVIRONMENT = "demo";

    private static final String ENVIRONMENT = chooseEnvironment();

    private static final Properties FILE_SETTINGS = loadEnvironmentFile(ENVIRONMENT);

    private TestConfig() {
    }

    public static String environment() {
        return ENVIRONMENT;
    }

    public static String petstoreBaseUrl() {
        return required("petstore.baseUrl", "PETSTORE_BASE_URL");
    }

    public static String orangeHrmBaseUrl() {
        return required("orangehrm.baseUrl", "ORANGEHRM_BASE_URL");
    }

    public static String orangeHrmUsername() {
        return required("orangehrm.username", "ORANGEHRM_USERNAME");
    }

    public static String orangeHrmPassword() {
        return required("orangehrm.password", "ORANGEHRM_PASSWORD");
    }

    public static boolean headless() {
        String value = optional("headless", "HEADLESS", "true");
        return Boolean.parseBoolean(value);
    }

    public static String browser() {
        return optional("browser", "BROWSER", "chromium");
    }

    private static String chooseEnvironment() {
        String fromProperty = System.getProperty("env");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }

        String fromEnvironment = System.getenv("TEST_ENV");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }

        return DEFAULT_ENVIRONMENT;
    }

    private static Properties loadEnvironmentFile(String environmentName) {
        String path = "/environments/" + environmentName + ".properties";
        Properties settings = new Properties();

        try (InputStream file = TestConfig.class.getResourceAsStream(path)) {
            if (file == null) {
                throw new IllegalStateException("No settings file for environment '" + environmentName
                        + "'. Expected src/test/resources" + path);
            }
            settings.load(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read src/test/resources" + path, e);
        }

        return settings;
    }

    private static String required(String propertyName, String envName) {
        String value = lookUp(propertyName, envName);
        if (value == null) {
            throw new IllegalStateException("Setting '" + propertyName + "' is missing for environment '"
                    + ENVIRONMENT + "'. Set the " + envName + " environment variable, pass -D"
                    + propertyName + "=..., or add it to environments/" + ENVIRONMENT + ".properties.");
        }
        return value;
    }

    private static String optional(String propertyName, String envName, String fallback) {
        String value = lookUp(propertyName, envName);
        if (value == null) {
            return fallback;
        }
        return value;
    }

    private static String lookUp(String propertyName, String envName) {
        String fromProperty = System.getProperty(propertyName);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }

        String fromEnvironment = System.getenv(envName);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }

        String fromFile = FILE_SETTINGS.getProperty(propertyName);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }

        return null;
    }
}
