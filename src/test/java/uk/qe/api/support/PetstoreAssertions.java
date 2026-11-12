package uk.qe.api.support;

import com.microsoft.playwright.APIResponse;
import io.qameta.allure.Allure;
import uk.qe.api.model.ApiError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that are repeated across the Petstore tests, kept in one place so every
 * test checks responses the same, thorough way.
 */
public final class PetstoreAssertions {

    private PetstoreAssertions() {
    }

    /**
     * The response is JSON and its body matches the named definition in the Swagger file.
     */
    public static void assertMatchesContract(APIResponse response, String definitionName) {
        String body = response.text();
        // Put every checked response in the report, so a reader can see what came back
        Allure.addAttachment("Response " + response.status() + " " + response.url(), "application/json", body, "json");

        assertThat(response.headers().get("content-type"))
                .as("content type")
                .contains("application/json");

        List<String> problems = PetstoreContract.problemsWith(body, definitionName);
        assertThat(problems)
                .as("response should match the '" + definitionName + "' definition in the Swagger file.%nBody was: %s", body)
                .isEmpty();
    }

    /**
     * Full check of a Petstore error: status code, JSON content type, the body matches
     * the ApiResponse definition, and code, type and message are all exactly as expected.
     */
    public static void assertPetstoreError(APIResponse response,
                                           int expectedStatus,
                                           int expectedCode,
                                           String expectedType,
                                           String expectedMessage) {
        String body = response.text();

        assertThat(response.status())
                .as("status code (body was %s)", body)
                .isEqualTo(expectedStatus);

        assertMatchesContract(response, "ApiResponse");

        ApiError expected = new ApiError(expectedCode, expectedType, expectedMessage);
        ApiError actual = readStrictly(body, ApiError.class);
        assertThat(actual)
                .as("error body")
                .isEqualTo(expected);
    }

    public static <T> T readStrictly(String body, Class<T> type) {
        try {
            return StrictJson.mapper().readValue(body, type);
        } catch (Exception e) {
            throw new AssertionError("Response body does not fit " + type.getSimpleName()
                    + " exactly (wrong type or unexpected field?): " + body, e);
        }
    }
}
