package uk.qe.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The Pet object from the Petstore Swagger definition.
 * name and photoUrls are the two required fields in the spec.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Pet(
        Long id,
        Category category,
        String name,
        List<String> photoUrls,
        List<Tag> tags,
        String status) {

    public record Category(Long id, String name) {
    }

    public record Tag(Long id, String name) {
    }
}
