package uk.qe.ui.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.qe.config.TestConfig;
import uk.qe.ui.pages.LoginPage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Starts the browser once per test class and gives every test a fresh, already
 * logged-in browser context.
 *
 * Logging in through the UI for every test is slow and tests the login page over and
 * over. Instead we log in once, save the session cookies to a file and load that file
 * into each new context. Login itself gets its own tests.
 *
 * Every test is recorded with Playwright tracing. The trace is only kept if the test
 * fails (see TraceOnFailure). Drop the zip from target/traces onto
 * https://trace.playwright.dev to replay the failure step by step.
 */
public abstract class UiTestBase {

    private static final Path LOGGED_IN_STATE = Paths.get("target", "auth", "admin.json");

    protected static Playwright playwright;
    protected static Browser browser;

    protected BrowserContext context;
    protected Page page;

    @RegisterExtension
    TraceOnFailure traceOnFailure = new TraceOnFailure(() -> context);

    @BeforeAll
    static void startBrowserAndLogIn() throws Exception {
        playwright = Playwright.create();
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(TestConfig.headless());
        browser = pickBrowser(playwright).launch(launchOptions);

        saveLoggedInSession();
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openLoggedInPage(TestInfo testInfo) {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setBaseURL(TestConfig.orangeHrmBaseUrl())
                .setStorageStatePath(LOGGED_IN_STATE)
                .setViewportSize(1440, 900);
        context = browser.newContext(options);
        // The public demo can be slow, so allow a bit more than the 30s default for navigation
        context.setDefaultNavigationTimeout(45_000);
        context.setDefaultTimeout(15_000);

        context.tracing().start(new Tracing.StartOptions()
                .setName(testInfo.getDisplayName())
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));

        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        // TraceOnFailure has already saved or thrown away the trace by this point
        if (context != null) {
            context.close();
        }
    }

    private static void saveLoggedInSession() throws Exception {
        Files.createDirectories(LOGGED_IN_STATE.getParent());

        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setBaseURL(TestConfig.orangeHrmBaseUrl());
        BrowserContext loginContext = browser.newContext(options);
        loginContext.setDefaultNavigationTimeout(45_000);
        Page loginPage = loginContext.newPage();

        LoginPage login = new LoginPage(loginPage);
        login.open();
        login.logInAs(TestConfig.orangeHrmUsername(), TestConfig.orangeHrmPassword());
        login.waitUntilLoggedIn();

        loginContext.storageState(new BrowserContext.StorageStateOptions().setPath(LOGGED_IN_STATE));
        loginContext.close();
    }

    private static BrowserType pickBrowser(Playwright playwright) {
        String name = TestConfig.browser().toLowerCase();
        if (name.equals("firefox")) {
            return playwright.firefox();
        }
        if (name.equals("webkit")) {
            return playwright.webkit();
        }
        return playwright.chromium();
    }
}
