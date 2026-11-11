package uk.qe.ui.support;

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
 * Clean-up through the API is much faster and steadier than clicking through
 * "Delete" dialogs, and it is not what we are testing.
 */
public class OrangeHrmApi {

    private final BrowserContext context;

    public OrangeHrmApi(BrowserContext context) {
        this.context = context;
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
