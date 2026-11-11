package uk.qe.api.model;

/**
 * The error body Petstore sends back, e.g. {"code":1,"type":"error","message":"Pet not found"}
 */
public record ApiError(Integer code, String type, String message) {
}
