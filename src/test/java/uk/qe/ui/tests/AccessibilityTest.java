package uk.qe.ui.tests;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.qe.config.TestConfig;
import uk.qe.ui.pages.AddEmployeePage;
import uk.qe.ui.pages.EmployeeListPage;
import uk.qe.ui.pages.LoginPage;
import uk.qe.ui.support.AccessibilityScan;
import uk.qe.ui.support.UiTestBase;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Automated accessibility checks (axe-core, WCAG 2.2 A and AA) on the pages the UI
 * tests already visit.
 *
 * The demo already has accessibility failures, so the normal run compares each page
 * with its list of known issues: a new kind of failure turns the build red, and so does
 * a fixed one that is still listed. The stricter "no serious issues at all" test is
 * tagged known-defect, like the Petstore defects.
 *
 * Automated rules find roughly a third of accessibility problems. Keyboard-only use and
 * screen reader checks still need a person.
 */
@Tag("ui")
@Tag("accessibility")
@Tag("regression")
@DisplayName("UI - OrangeHRM accessibility (WCAG 2.2 AA)")
class AccessibilityTest extends UiTestBase {

    @Test
    @DisplayName("login page has no new accessibility issues")
    void loginPage() {
        BrowserContext loggedOut = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(TestConfig.orangeHrmBaseUrl()));
        try {
            Page loginPage = loggedOut.newPage();
            new LoginPage(loginPage).open();
            loginPage.locator("input[name='username']").waitFor();

            assertOnlyKnownIssues(AccessibilityScan.of(loginPage, "login"));
        } finally {
            loggedOut.close();
        }
    }

    @Test
    @DisplayName("add employee page has no new accessibility issues")
    void addEmployeePage() {
        new AddEmployeePage(page).open();

        assertOnlyKnownIssues(AccessibilityScan.of(page, "addEmployee"));
    }

    @Test
    @DisplayName("employee list page has no new accessibility issues")
    void employeeListPage() {
        new EmployeeListPage(page).open();

        assertOnlyKnownIssues(AccessibilityScan.of(page, "employeeList"));
    }

    @Test
    @Tag("known-defect")
    @DisplayName("[known defect] add employee page has no serious or critical issues")
    void addEmployeePageHasNoSeriousIssues() {
        new AddEmployeePage(page).open();
        AccessibilityScan scan = AccessibilityScan.of(page, "addEmployee");

        assertThat(scan.seriousOrCriticalRules())
                .as("serious or critical WCAG issues on Add Employee:%s", scan.summary())
                .isEmpty();
    }

    private void assertOnlyKnownIssues(AccessibilityScan scan) {
        Set<String> found = scan.failedRules();
        Set<String> known = scan.knownRules();

        Set<String> newIssues = new TreeSet<>(found);
        newIssues.removeAll(known);

        Set<String> fixedIssues = new TreeSet<>(known);
        fixedIssues.removeAll(found);

        assertThat(newIssues)
                .as("new accessibility issues (not in known-issues.properties):%s", scan.summary())
                .isEmpty();
        assertThat(fixedIssues)
                .as("these issues are fixed - remove them from known-issues.properties")
                .isEmpty();
    }
}
