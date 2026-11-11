package uk.qe.ui.pages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Pattern;

/**
 * PIM > Add Employee
 */
public class AddEmployeePage {

    private static final Pattern PERSONAL_DETAILS_URL =
            Pattern.compile(".*/pim/viewPersonalDetails/empNumber/(\\d+)");

    private final Page page;
    private final Locator heading;
    private final Locator firstName;
    private final Locator lastName;
    private final Locator employeeId;
    private final Locator saveButton;
    private final Locator toast;

    public AddEmployeePage(Page page) {
        this.page = page;
        this.heading = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Add Employee"));
        this.firstName = page.locator("input[name='firstName']");
        this.lastName = page.locator("input[name='lastName']");
        this.employeeId = FormField.inputLabelled(page, "Employee Id");
        this.saveButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save"));
        this.toast = page.locator(".oxd-toast");
    }

    public AddEmployeePage open() {
        page.navigate("/web/index.php/pim/addEmployee");
        heading.waitFor();
        return this;
    }

    public AddEmployeePage enterName(String first, String last) {
        firstName.fill(first);
        lastName.fill(last);
        return this;
    }

    public AddEmployeePage enterEmployeeId(String id) {
        // The page pre-fills a suggested id; replace it with ours so we can search for it later
        employeeId.fill(id);
        return this;
    }

    public void clickSave() {
        saveButton.click();
    }

    /**
     * Clicks Save and waits for the API call the page makes to create the employee.
     *
     * Returns OrangeHRM's internal employee number from that response. We grab it here,
     * straight away, so the test can always clean up - even if a later step fails.
     */
    public int saveNewEmployee() {
        Response createResponse = page.waitForResponse(
                response -> response.url().endsWith("/api/v2/pim/employees")
                        && response.request().method().equals("POST"),
                saveButton::click);

        if (createResponse.status() != 200) {
            throw new IllegalStateException("Creating the employee failed: "
                    + createResponse.status() + " " + createResponse.text());
        }

        try {
            JsonNode body = new ObjectMapper().readTree(createResponse.text());
            return body.path("data").path("empNumber").asInt();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unexpected response when creating the employee", e);
        }
    }

    public void waitForPersonalDetailsPage(int empNumber) {
        page.waitForURL(PERSONAL_DETAILS_URL);
        String expectedEnding = "/empNumber/" + empNumber;
        if (!page.url().endsWith(expectedEnding)) {
            throw new IllegalStateException("Expected the details page for " + empNumber + " but got " + page.url());
        }
    }

    public Locator heading() {
        return heading;
    }

    public Locator toast() {
        return toast;
    }

    public Locator firstNameError() {
        return FormField.errorUnder(firstName);
    }

    public Locator lastNameError() {
        return FormField.errorUnder(lastName);
    }
}
