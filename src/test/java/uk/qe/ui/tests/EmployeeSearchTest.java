package uk.qe.ui.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.qe.ui.pages.EmployeeListPage;
import uk.qe.ui.support.OrangeHrmApi;
import uk.qe.ui.support.UiTestBase;

import java.util.concurrent.ThreadLocalRandom;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Integration: data created through the API shows up in the UI.
 *
 * The subject here is the employee search, not adding an employee. So the employee
 * is created through OrangeHRM's own API (a fraction of a second) and only the search
 * goes through the browser. AddEmployeeTest does the opposite: the form is the
 * subject, so it goes through the UI.
 */
@Tag("ui")
@Tag("integration")
@Tag("regression")
@DisplayName("UI - OrangeHRM PIM: employee search")
class EmployeeSearchTest extends UiTestBase {

    private Integer createdEmpNumber;

    @AfterEach
    void removeEmployee() {
        if (createdEmpNumber != null) {
            new OrangeHrmApi(context).deleteEmployee(createdEmpNumber);
            createdEmpNumber = null;
        }
    }

    @Test
    @DisplayName("an employee added through the API can be found by id in the employee list")
    void employeeAddedThroughApiIsFoundInTheList() {
        String uniquePart = String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 999_999));
        String firstName = "Api";
        String lastName = "Search" + uniquePart;
        String employeeId = "QS" + uniquePart;

        createdEmpNumber = new OrangeHrmApi(context).createEmployee(firstName, lastName, employeeId);

        EmployeeListPage employeeList = new EmployeeListPage(page).open();
        employeeList.searchByEmployeeId(employeeId);

        assertThat(employeeList.recordCount()).hasText("(1) Record Found");
        assertThat(employeeList.resultRows()).hasCount(1);
        assertThat(employeeList.resultRows().first()).containsText(employeeId);
        assertThat(employeeList.resultRows().first()).containsText(firstName);
        assertThat(employeeList.resultRows().first()).containsText(lastName);
    }
}
