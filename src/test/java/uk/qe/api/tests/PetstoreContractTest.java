package uk.qe.api.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.qe.api.support.PetstoreContract;
import uk.qe.api.support.StrictJson;
import uk.qe.config.TestConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Answers "how do we know the contract has not changed?"
 *
 * The response checks in GetPetByIdTest use our saved copy of the Swagger file. This
 * test compares the parts we rely on with the Swagger file the live API publishes.
 * If someone changes the Pet or ApiResponse definition, this fails with a clear
 * message, and we decide on purpose whether to update our copy and tests.
 */
@Tag("api")
@Tag("regression")
@DisplayName("API - Petstore contract has not changed")
class PetstoreContractTest {

    private static Playwright playwright;
    private static APIRequestContext request;
    private static JsonNode liveSwagger;

    @BeforeAll
    static void downloadLiveSwagger() throws Exception {
        playwright = Playwright.create();
        request = playwright.request().newContext(
                new APIRequest.NewContextOptions().setBaseURL(TestConfig.petstoreBaseUrl()));

        APIResponse response = request.get("swagger.json");
        assertThat(response.status()).as("downloading swagger.json").isEqualTo(200);
        liveSwagger = StrictJson.mapper().readTree(response.text());
    }

    @AfterAll
    static void close() {
        if (request != null) {
            request.dispose();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @ParameterizedTest(name = "definitions/{0}")
    @ValueSource(strings = {"Pet", "Category", "Tag", "ApiResponse"})
    @DisplayName("the live Swagger definition matches our saved copy")
    void definitionMatchesSavedCopy(String definitionName) {
        JsonNode saved = PetstoreContract.swagger().path("definitions").path(definitionName);
        JsonNode live = liveSwagger.path("definitions").path(definitionName);

        assertThat(live)
                .as("definitions/%s in the live swagger.json - if this changed on purpose, update %s",
                        definitionName, PetstoreContract.SWAGGER_FILE)
                .isEqualTo(saved);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"get", "delete"})
    @DisplayName("the documented responses for /pet/{petId} match our saved copy")
    void petByIdResponsesMatchSavedCopy(String method) {
        JsonNode saved = PetstoreContract.swagger().path("paths").path("/pet/{petId}").path(method).path("responses");
        JsonNode live = liveSwagger.path("paths").path("/pet/{petId}").path(method).path("responses");

        assertThat(live).as("responses for %s /pet/{petId}", method.toUpperCase()).isEqualTo(saved);
    }
}
