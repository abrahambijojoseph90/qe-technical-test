package uk.qe.api.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.qe.api.model.ApiError;
import uk.qe.api.model.Pet;
import uk.qe.api.support.PetstoreAssertions;
import uk.qe.api.support.PetstoreContract;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the test code itself. No network needed.
 *
 * A check that can never fail gives false confidence. These feed hand-written bad
 * responses into the schema check and the strict JSON reader to prove they really
 * do reject them.
 */
@Tag("unit")
@DisplayName("Self-check - response checks catch bad responses")
class ResponseChecksSelfTest {

    private static final String GOOD_PET =
            "{\"id\":123,\"category\":{\"id\":1,\"name\":\"Dogs\"},\"name\":\"Rex\","
                    + "\"photoUrls\":[\"a.jpg\"],\"tags\":[{\"id\":1,\"name\":\"t\"}],\"status\":\"available\"}";

    @Test
    @DisplayName("a correct pet passes the schema check")
    void goodPetPasses() {
        List<String> problems = PetstoreContract.problemsWith(GOOD_PET, "Pet");

        assertThat(problems).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "id sent as text                 | {\"id\":\"123\",\"name\":\"Rex\",\"photoUrls\":[]}",
            "name missing (required)         | {\"id\":123,\"photoUrls\":[]}",
            "photoUrls missing (required)    | {\"id\":123,\"name\":\"Rex\"}",
            "status not one of the allowed   | {\"id\":123,\"name\":\"Rex\",\"photoUrls\":[],\"status\":\"flying\"}",
            "unexpected extra field          | {\"id\":123,\"name\":\"Rex\",\"photoUrls\":[],\"colour\":\"brown\"}",
            "field renamed                   | {\"petId\":123,\"name\":\"Rex\",\"photoUrls\":[]}",
            "wrong type inside category      | {\"id\":123,\"name\":\"Rex\",\"photoUrls\":[],\"category\":{\"id\":\"one\"}}",
            "photoUrls is not a list         | {\"id\":123,\"name\":\"Rex\",\"photoUrls\":\"a.jpg\"}"
    })
    @DisplayName("a broken pet fails the schema check")
    void brokenPetFails(String whatIsWrong, String json) {
        List<String> problems = PetstoreContract.problemsWith(json, "Pet");

        assertThat(problems).as(whatIsWrong).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "code sent as text      | {\"code\":\"1\",\"type\":\"error\",\"message\":\"Pet not found\"}",
            "unexpected extra field | {\"code\":1,\"type\":\"error\",\"message\":\"Pet not found\",\"trace\":\"...\"}"
    })
    @DisplayName("a broken error body fails the schema check")
    void brokenErrorFails(String whatIsWrong, String json) {
        List<String> problems = PetstoreContract.problemsWith(json, "ApiResponse");

        assertThat(problems).as(whatIsWrong).isNotEmpty();
    }

    @Test
    @DisplayName("the strict reader rejects a number sent as text")
    void strictReaderRejectsNumberAsText() {
        String idAsText = "{\"id\":\"123\",\"name\":\"Rex\",\"photoUrls\":[]}";

        assertThatThrownBy(() -> PetstoreAssertions.readStrictly(idAsText, Pet.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("does not fit Pet exactly");
    }

    @Test
    @DisplayName("the strict reader rejects text sent as a number")
    void strictReaderRejectsTextAsNumber() {
        String messageAsNumber = "{\"code\":1,\"type\":\"error\",\"message\":404}";

        assertThatThrownBy(() -> PetstoreAssertions.readStrictly(messageAsNumber, ApiError.class))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("the strict reader rejects an unexpected field")
    void strictReaderRejectsUnknownField() {
        String extraField = "{\"id\":123,\"name\":\"Rex\",\"photoUrls\":[],\"colour\":\"brown\"}";

        assertThatThrownBy(() -> PetstoreAssertions.readStrictly(extraField, Pet.class))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("the strict reader accepts a correct pet")
    void strictReaderAcceptsGoodPet() {
        Pet pet = PetstoreAssertions.readStrictly(GOOD_PET, Pet.class);

        assertThat(pet.id()).isEqualTo(123L);
        assertThat(pet.name()).isEqualTo("Rex");
    }
}
