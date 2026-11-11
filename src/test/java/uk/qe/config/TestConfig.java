package uk.qe.config;

/**
 * One place for every setting the tests need.
 *
 * Each value is looked up in this order:
 *   1. a JVM system property, e.g. mvn test -Dheadless=false
 *   2. an environment variable, e.g. ORANGEHRM_PASSWORD=...
 *   3. the default below, which points at the public demo sites
 *
 * The OrangeHRM defaults are the demo credentials shown on its own login page.
 * Anything real would come from the environment or a CI secret, never from the code.
 */
public final class TestConfig {

    private TestConfig() {
    }

    public static String petstoreBaseUrl() {
        return read("petstore.baseUrl", "PETSTORE_BASE_URL", "https://petstore.swagger.io/v2/");
    }

    public static String orangeHrmBaseUrl() {
        return read("orangehrm.baseUrl", "ORANGEHRM_BASE_URL", "https://opensource-demo.orangehrmlive.com");
    }

    public static String orangeHrmUsername() {
        return read("orangehrm.username", "ORANGEHRM_USERNAME", "Admin");
    }

    public static String orangeHrmPassword() {
        return read("orangehrm.password", "ORANGEHRM_PASSWORD", "admin123");
    }

    public static boolean headless() {
        String value = read("headless", "HEADLESS", "true");
        return Boolean.parseBoolean(value);
    }

    public static String browser() {
        return read("browser", "BROWSER", "chromium");
    }

    private static String read(String propertyName, String envName, String defaultValue) {
        String fromProperty = System.getProperty(propertyName);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }

        String fromEnvironment = System.getenv(envName);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }

        return defaultValue;
    }
}
