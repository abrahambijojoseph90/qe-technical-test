package uk.qe.api.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.qe.api.client.PetApiClient;
import uk.qe.api.data.PetTestData;
import uk.qe.api.model.Pet;
import uk.qe.api.support.StrictJson;
import uk.qe.config.TestConfig;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.qe.api.support.PetstoreAssertions.assertMatchesContract;
import static uk.qe.api.support.PetstoreAssertions.assertPetstoreError;

/**
 * Integration: one pet through its whole life, across five endpoints.
 *
 * GetPetByIdTest checks one endpoint in depth. This checks that the endpoints agree
 * with each other about the same data: what POST stores, GET returns; what PUT
 * changes, GET and findByStatus both see; after DELETE, nothing returns it.
 * Each step shows up separately in the Allure report.
 */
@Tag("api")
@Tag("integration")
@Tag("regression")
@DisplayName("API - a pet's life: create, read, update, find, delete")
class PetLifecycleTest {

    private static Playwright playwright;
    private static APIRequestContext request;

    private Long petToDelete;

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

    @AfterEach
    void removeTestData() {
        // Only needed if a step failed before the delete step
        if (petToDelete != null) {
            new PetApiClient(request).deletePet(petToDelete);
            petToDelete = null;
        }
    }

    @Test
    @DisplayName("every endpoint sees the same pet at every stage")
    void petLifecycle() {
        PetApiClient pets = new PetApiClient(request);
        Pet newPet = PetTestData.availableDog();
        Pet soldPet = new Pet(newPet.id(), newPet.category(), newPet.name() + "-renamed",
                newPet.photoUrls(), newPet.tags(), "sold");

        Allure.step("Create the pet", () -> {
            APIResponse created = pets.createPet(newPet);
            assertThat(created.status()).as("create status").isEqualTo(200);
            petToDelete = newPet.id();
        });

        Allure.step("Read it back unchanged", () -> {
            APIResponse response = pets.getPet(newPet.id());
            assertThat(response.status()).as("get status").isEqualTo(200);
            assertMatchesContract(response, "Pet");
            assertThat(pets.readBody(response, Pet.class)).as("pet after create").isEqualTo(newPet);
        });

        Allure.step("Rename it and mark it sold", () -> {
            APIResponse updated = pets.updatePet(soldPet);
            assertThat(updated.status()).as("update status").isEqualTo(200);
            assertMatchesContract(updated, "Pet");
        });

        Allure.step("Read it back with the changes", () -> {
            APIResponse response = pets.getPet(newPet.id());
            assertThat(response.status()).as("get status").isEqualTo(200);
            assertThat(pets.readBody(response, Pet.class)).as("pet after update").isEqualTo(soldPet);
        });

        Allure.step("Find it in the list of sold pets", () -> {
            APIResponse response = pets.findByStatus("sold");
            assertThat(response.status()).as("findByStatus status").isEqualTo(200);
            assertThat(containsPetWithId(response, newPet.id()))
                    .as("pet %d in findByStatus?status=sold", newPet.id())
                    .isTrue();
        });

        Allure.step("Delete it", () -> {
            APIResponse deleted = pets.deletePet(newPet.id());
            assertThat(deleted.status()).as("delete status").isEqualTo(200);
            petToDelete = null;
        });

        Allure.step("Reading it now gives 'Pet not found'", () -> {
            APIResponse response = pets.getPet(newPet.id());
            assertPetstoreError(response, 404, 1, "error", "Pet not found");
        });
    }

    /**
     * The list holds every sold pet in the shared demo, including other people's
     * badly formed test data, so only our pet's id is looked for. Strict checks on
     * other people's records would fail for reasons that have nothing to do with us.
     */
    private static boolean containsPetWithId(APIResponse response, long petId) throws Exception {
        JsonNode list = StrictJson.mapper().readTree(response.text());
        for (JsonNode pet : list) {
            if (pet.path("id").asLong() == petId) {
                return true;
            }
        }
        return false;
    }
}
