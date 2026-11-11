package uk.qe.api.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;

/**
 * A JSON reader that refuses to guess.
 *
 * Out of the box Jackson is forgiving: it will turn "123" (text) into the number 123,
 * 1.9 into 1, and quietly skip fields it does not know. That is handy in production
 * code but wrong for a test, because the API would be sending the wrong type and the
 * test would never notice. This mapper fails instead.
 */
public final class StrictJson {

    private static final ObjectMapper MAPPER = build();

    private StrictJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private static ObjectMapper build() {
        JsonMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .build();

        // Numbers must arrive as numbers: "123" is not accepted for a number field
        mapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Float)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail);

        // Text must arrive as text: 123 is not accepted for a text field
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);

        return mapper;
    }
}
