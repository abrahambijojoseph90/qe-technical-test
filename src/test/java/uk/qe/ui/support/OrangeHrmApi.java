package uk.qe.ui.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.RequestOptions;

import java.util.List;
import java.util.Map;

/**
 * The OrangeHRM front end is built on its own REST API (/web/index.php/api/v2/...).
 * context.request() sends calls with the browser's logged-in session cookie, so the
 * UI tests can use the API for set-up and clean-up without logging in again.
 *
 * Set-up and clean-up through the API are much faster and steadier than clicking
 * through forms and "Delete" dialogs, and they are not what those tests are about.
 */
public class OrangeHrmApi {

    private final BrowserContext context;

    public OrangeHrmApi(BrowserContext context) {
        this.context = context;
    }

    /**
     * Creates an employee through the API and returns its empNumber. Used to set up
     * data for tests whose subject is something else, such as searching.
     */
    public int createEmployee(String firstName, String lastName, String employeeId) {
        Map<String, Object> body = Map.of(
                "firstName", firstName,
                "middleName", "",
                "lastName", lastName,
                "employeeId", employeeId);
        RequestOptions options = RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(body);

        APIResponse response = context.request().post("/web/index.php/api/v2/pim/employees", options);
        if (response.status() != 200) {
            throw new IllegalStateException("Could not create the test employee - status "
                    + response.status() + " " + response.text());
        }

        try {
            JsonNode json = new ObjectMapper().readTree(response.text());
            return json.path("data").path("empNumber").asInt();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Create employee response was not JSON: " + response.text(), e);
        }
    }

    public void deleteEmployee(int empNumber) {
        Map<String, Object> body = Map.of("ids", List.of(empNumber));
        RequestOptions options = RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(body);

        APIResponse response = context.request().delete("/web/index.php/api/v2/pim/employees", options);
        if (!response.ok()) {
            // Don't fail the test over clean-up, but make it visible
            System.out.println("Clean-up: could not delete employee " + empNumber
                    + " - status " + response.status() + " " + response.text());
        }
    }
}
