package uk.qe.ui.tests;

import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.SameSiteAttribute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.qe.config.TestConfig;
import uk.qe.ui.pages.LoginPage;
import uk.qe.ui.support.UiTestBase;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic security checks on OrangeHRM: access control, the session cookie and the
 * response headers a browser relies on.
 *
 * These are cheap checks that belong in every run. They do not replace a proper
 * security test (for example an OWASP ZAP scan in its own pipeline, or a penetration
 * test before go-live).
 */
@Tag("ui")
@Tag("security")
@Tag("regression")
@DisplayName("Security - OrangeHRM session and headers")
class OrangeHrmSecurityTest extends UiTestBase {

    private static final String EMPLOYEES_API = "/web/index.php/api/v2/pim/employees";
    private static final String EMPLOYEE_LIST_PAGE = "/web/index.php/pim/viewEmployeeList";
    private static final String LOGIN_PAGE = "/web/index.php/auth/login";

    private APIRequestContext anonymous;

    @BeforeEach
    void startAnonymousClient() {
        anonymous = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(TestConfig.orangeHrmBaseUrl()));
    }

    @AfterEach
    void stopAnonymousClient() {
        if (anonymous != null) {
            anonymous.dispose();
        }
    }

    @Test
    @DisplayName("the employee API refuses requests without a session")
    void apiNeedsASession() {
        APIResponse response = anonymous.get(EMPLOYEES_API);

        assertThat(response.status()).as("status without a session").isEqualTo(401);
        assertThat(response.text()).as("body without a session").doesNotContain("empNumber");
    }

    @Test
    @DisplayName("a protected page sends you to the login page without a session")
    void protectedPageRedirectsToLogin() {
        APIResponse response = anonymous.get(EMPLOYEE_LIST_PAGE, RequestOptions.create().setMaxRedirects(0));

        assertThat(response.status()).as("status").isEqualTo(302);
        assertThat(response.headers().get("location")).as("redirect target").endsWith(LOGIN_PAGE);
    }

    @Test
    @DisplayName("the session cookie is Secure, HttpOnly and SameSite")
    void sessionCookieIsProtected() {
        Cookie sessionCookie = findSessionCookie(context.cookies());

        assertThat(sessionCookie.secure).as("Secure: only sent over HTTPS").isTrue();
        assertThat(sessionCookie.httpOnly).as("HttpOnly: JavaScript cannot read it").isTrue();
        assertThat(sessionCookie.sameSite).as("SameSite: not sent on cross-site requests")
                .isIn(SameSiteAttribute.LAX, SameSiteAttribute.STRICT);
    }

    @Test
    @DisplayName("HTTPS-only and no-sniff headers are set")
    void transportHeadersAreSet() {
        Map<String, String> headers = anonymous.get(LOGIN_PAGE).headers();

        assertThat(headers).as("response headers").containsKey("strict-transport-security");
        assertThat(headers.get("x-content-type-options")).as("X-Content-Type-Options").isEqualTo("nosniff");
    }

    @Test
    @DisplayName("after logout the old session can no longer be used")
    void logoutEndsTheSession() {
        // Use a separate login, so the shared session the other tests use stays valid
        BrowserContext ownSession = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(TestConfig.orangeHrmBaseUrl()));
        try {
            Page ownPage = ownSession.newPage();
            LoginPage login = new LoginPage(ownPage);
            login.open();
            login.logInAs(TestConfig.orangeHrmUsername(), TestConfig.orangeHrmPassword());
            login.waitUntilLoggedIn();

            Cookie oldSession = findSessionCookie(ownSession.cookies());
            ownPage.navigate("/web/index.php/auth/logout");

            APIResponse response = anonymous.get(EMPLOYEES_API, RequestOptions.create()
                    .setHeader("Cookie", oldSession.name + "=" + oldSession.value));
            assertThat(response.status()).as("status when replaying the logged-out session").isEqualTo(401);
        } finally {
            ownSession.close();
        }
    }

    @Test
    @Tag("known-defect")
    @DisplayName("[known defect] pages cannot be put inside a frame on another site")
    void pagesCannotBeFramed() {
        Map<String, String> headers = anonymous.get(LOGIN_PAGE).headers();
        String frameOptions = headers.getOrDefault("x-frame-options", "");
        String contentPolicy = headers.getOrDefault("content-security-policy", "");

        boolean protectedFromFraming = !frameOptions.isBlank() || contentPolicy.contains("frame-ancestors");

        assertThat(protectedFromFraming)
                .as("X-Frame-Options or CSP frame-ancestors should stop clickjacking. Got X-Frame-Options='%s'",
                        frameOptions)
                .isTrue();
    }

    @Test
    @Tag("known-defect")
    @DisplayName("[known defect] the Server header does not reveal software versions")
    void serverHeaderHidesVersion() {
        String server = anonymous.get(LOGIN_PAGE).headers().getOrDefault("server", "");

        assertThat(server).as("Server header").doesNotContainPattern("\\d");
    }

    private static Cookie findSessionCookie(List<Cookie> cookies) {
        for (Cookie cookie : cookies) {
            if (cookie.name.equals("orangehrm")) {
                return cookie;
            }
        }
        throw new AssertionError("No 'orangehrm' session cookie found. Cookies: " + cookies.size());
    }
}
