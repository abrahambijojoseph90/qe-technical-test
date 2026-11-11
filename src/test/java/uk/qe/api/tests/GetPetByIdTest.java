package uk.qe.api.tests;

import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.qe.api.client.PetApiClient;
import uk.qe.api.data.PetTestData;
import uk.qe.api.model.Pet;
import uk.qe.config.TestConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.qe.api.support.PetstoreAssertions.assertMatchesContract;
import static uk.qe.api.support.PetstoreAssertions.assertPetstoreError;

/**
 * Endpoint under test: GET /pet/{petId}
 *
 * POST and DELETE are only used to set up and clean up data. The checks are all
 * about what GET /pet/{petId} returns. Expected results come from the Swagger
 * definition at https://petstore.swagger.io/v2/swagger.json :
 *   200 - the Pet
 *   400 - Invalid ID supplied
 *   404 - Pet not found
 *
 * Every response body is checked three ways:
 *   1. against the Swagger definition (structure, required fields, types, no extra fields)
 *   2. read strictly into a Java record (so "123" as text for a number fails)
 *   3. the actual values, compared with what we expect
 */
@Tag("api")
@DisplayName("API - GET /pet/{petId}")
class GetPetByIdTest {

    private static Playwright playwright;
    private static APIRequestContext request;

    private PetApiClient pets;
    private final List<Long> petsToDelete = new ArrayList<>();

    @BeforeAll
    static void startApiContext() {
        playwright = Playwright.create();
        APIRequest.NewContextOptions options = new APIRequest.NewContextOptions()
                .setBaseURL(TestConfig.petstoreBaseUrl())
                .setExtraHTTPHeaders(Map.of("Accept", "application/json"));
        request = playwright.request().newContext(options);
    }

    @AfterAll
    static void stopApiContext() {
        if (request != null) {
            request.dispose();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void setUp() {
        pets = new PetApiClient(request);
    }

    @AfterEach
    void removeTestData() {
        // Leave the shared demo store as we found it, even when a test fails half way
        for (Long petId : petsToDelete) {
            pets.deletePet(petId);
        }
        petsToDelete.clear();
    }

    // ---------- positive ----------

    @Test
    @DisplayName("returns 200 and the same pet that was created")
    void returnsThePetThatWasCreated() {
        Pet newPet = PetTestData.availableDog();
        givenPetExists(newPet);

        APIResponse response = pets.getPet(newPet.id());

        assertThat(response.status()).as("status code").isEqualTo(200);
        assertMatchesContract(response, "Pet");

        Pet returnedPet = pets.readBody(response, Pet.class);
        // Records compare every field, so this checks id, name, category, photos, tags and status together
        assertThat(returnedPet).as("pet returned by GET").isEqualTo(newPet);
    }

    // ---------- negative ----------

    @Test
    @DisplayName("returns 404 'Pet not found' for an id that does not exist")
    void returnsNotFoundForUnknownId() {
        long idThatWasNeverCreated = PetTestData.uniqueId();

        APIResponse response = pets.getPet(idThatWasNeverCreated);

        assertPetNotFound(response);
    }

    @Test
    @DisplayName("returns 404 once the pet has been deleted")
    void returnsNotFoundAfterDelete() {
        Pet newPet = PetTestData.availableDog();
        givenPetExists(newPet);

        APIResponse deleteResponse = pets.deletePet(newPet.id());
        assertThat(deleteResponse.status()).as("delete status code").isEqualTo(200);
        petsToDelete.remove(newPet.id());

        APIResponse response = pets.getPet(newPet.id());

        assertPetNotFound(response);
    }

    @ParameterizedTest(name = "id = {0}")
    @ValueSource(longs = {0, -1})
    @DisplayName("returns 404 for ids that can never belong to a pet")
    void returnsNotFoundForZeroAndNegativeIds(long impossibleId) {
        APIResponse response = pets.getPet(impossibleId);

        assertPetNotFound(response);
    }

    /**
     * The spec says an invalid id should get 400 "Invalid ID supplied".
     * The live demo returns 404 and puts a Java NumberFormatException in the message.
     * Kept out of the default run (see pom.xml) and written up in TEST_DESIGN.md.
     */
    @ParameterizedTest(name = "id = \"{0}\"")
    @ValueSource(strings = {"abc", "1.5", "99999999999999999999"})
    @Tag("known-defect")
    @DisplayName("[known defect] returns 400 for an id that is not a whole number")
    void returnsBadRequestForInvalidId(String invalidId) {
        APIResponse response = pets.getPet(invalidId);

        String body = response.text();
        assertThat(body).as("error message should not leak server internals").doesNotContain("java.lang");
        assertThat(response.status()).as("status code").isEqualTo(400);
    }

    // ---------- helpers ----------

    /**
     * The documented "not found" error: 404, JSON, matches ApiResponse in the
     * Swagger file, and {"code":1,"type":"error","message":"Pet not found"} exactly.
     */
    private void assertPetNotFound(APIResponse response) {
        assertPetstoreError(response, 404, 1, "error", "Pet not found");
    }

    private void givenPetExists(Pet pet) {
        APIResponse createResponse = pets.createPet(pet);
        assertThat(createResponse.status())
                .as("test setup: creating the pet failed with body " + createResponse.text())
                .isEqualTo(200);
        petsToDelete.add(pet.id());
    }
}
