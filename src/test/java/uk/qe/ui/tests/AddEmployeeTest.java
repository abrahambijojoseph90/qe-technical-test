package uk.qe.ui.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.qe.ui.pages.AddEmployeePage;
import uk.qe.ui.pages.EmployeeListPage;
import uk.qe.ui.pages.PersonalDetailsPage;
import uk.qe.ui.support.OrangeHrmApi;
import uk.qe.ui.support.UiTestBase;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Scenario: an HR admin adds a new employee in PIM.
 *
 * Chosen because adding people is the core job of an HR system, and it exercises a
 * form, validation, a redirect and a search across two pages. The browser is already
 * logged in (see UiTestBase), and the employee is removed through the API afterwards.
 */
@Tag("ui")
@Tag("regression")
@DisplayName("UI - OrangeHRM PIM: add employee")
class AddEmployeeTest extends UiTestBase {

    private Integer createdEmpNumber;

    @AfterEach
    void removeEmployee() {
        if (createdEmpNumber != null) {
            new OrangeHrmApi(context).deleteEmployee(createdEmpNumber);
            createdEmpNumber = null;
        }
    }

    @Test
    @Tag("smoke")
    @Tag("e2e")
    @DisplayName("a new employee is saved and can be found in the employee list")
    void newEmployeeIsSavedAndSearchable() {
        String uniquePart = String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 999_999));
        String firstName = "Test";
        String lastName = "Auto" + uniquePart;
        String employeeId = "QA" + uniquePart;

        AddEmployeePage addEmployee = new AddEmployeePage(page).open();
        addEmployee.enterName(firstName, lastName);
        addEmployee.enterEmployeeId(employeeId);
        createdEmpNumber = addEmployee.saveNewEmployee();

        // The success message only shows for a few seconds, so check it before anything else
        assertThat(addEmployee.toast()).containsText("Successfully Saved");
        addEmployee.waitForPersonalDetailsPage(createdEmpNumber);

        PersonalDetailsPage details = new PersonalDetailsPage(page);
        assertThat(details.firstName()).hasValue(firstName);
        assertThat(details.lastName()).hasValue(lastName);
        assertThat(details.employeeId()).hasValue(employeeId);

        // And it shows up for anyone searching the employee list
        EmployeeListPage employeeList = new EmployeeListPage(page).open();
        employeeList.searchByEmployeeId(employeeId);

        assertThat(employeeList.recordCount()).hasText("(1) Record Found");
        assertThat(employeeList.resultRows()).hasCount(1);
        assertThat(employeeList.resultRows().first()).containsText(employeeId);
        assertThat(employeeList.resultRows().first()).containsText(firstName);
        assertThat(employeeList.resultRows().first()).containsText(lastName);
    }

    @Test
    @DisplayName("first and last name are required - nothing is saved without them")
    void firstAndLastNameAreRequired() {
        AddEmployeePage addEmployee = new AddEmployeePage(page).open();

        addEmployee.clickSave();

        assertThat(addEmployee.firstNameError()).hasText("Required");
        assertThat(addEmployee.lastNameError()).hasText("Required");
        // Still on the add page, so no employee was created
        assertThat(page).hasURL(Pattern.compile(".*/pim/addEmployee$"));
        assertThat(addEmployee.heading()).isVisible();
    }
}
