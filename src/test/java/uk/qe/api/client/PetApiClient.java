package uk.qe.api.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import uk.qe.api.model.Pet;
import uk.qe.api.support.PetstoreAssertions;
import uk.qe.api.support.StrictJson;

/**
 * Talks to the /pet endpoints of the Petstore.
 *
 * Tests call these methods instead of building HTTP requests themselves, so if the
 * API path or headers change there is one place to fix. The client does not assert
 * anything - it hands the raw response back and the test decides what is right.
 */
public class PetApiClient {

    private final APIRequestContext request;

    public PetApiClient(APIRequestContext request) {
        this.request = request;
    }

    public APIResponse createPet(Pet pet) {
        RequestOptions options = RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(toJson(pet));
        return request.post("pet", options);
    }

    public APIResponse getPet(long petId) {
        return request.get("pet/" + petId);
    }

    /**
     * Takes the id as text so tests can send ids that are not valid numbers.
     */
    public APIResponse getPet(String rawPetId) {
        return request.get("pet/" + rawPetId);
    }

    public APIResponse deletePet(long petId) {
        return request.delete("pet/" + petId);
    }

    /**
     * Reads the body strictly: a wrong type (e.g. "123" as text for a number) or an
     * unexpected field fails instead of being quietly converted or ignored.
     */
    public <T> T readBody(APIResponse response, Class<T> type) {
        return PetstoreAssertions.readStrictly(response.text(), type);
    }

    private String toJson(Object value) {
        try {
            return StrictJson.mapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not turn the test data into JSON", e);
        }
    }
}
