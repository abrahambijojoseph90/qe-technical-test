package uk.qe.api.data;

import uk.qe.api.model.Pet;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds pets for the tests.
 *
 * The public Petstore is shared by everyone on the internet, so fixed ids like 1 or 10
 * get overwritten by other people all the time. Each test gets its own random id in a
 * high range to keep its data to itself.
 */
public final class PetTestData {

    private static final long LOWEST_ID = 9_100_000_000L;
    private static final long HIGHEST_ID = 9_199_999_999L;

    private PetTestData() {
    }

    public static long uniqueId() {
        return ThreadLocalRandom.current().nextLong(LOWEST_ID, HIGHEST_ID);
    }

    public static Pet availableDog() {
        long id = uniqueId();
        Pet.Category category = new Pet.Category(1L, "Dogs");
        Pet.Tag tag = new Pet.Tag(1L, "automation");

        return new Pet(
                id,
                category,
                "Rex-" + id,
                List.of("https://example.org/photos/rex.jpg"),
                List.of(tag),
                "available");
    }
}
