package uk.qe.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks JSON against the definitions in the Petstore's own Swagger file.
 *
 * src/test/resources/contract/petstore-swagger.json is a copy of
 * https://petstore.swagger.io/v2/swagger.json taken when these tests were written.
 * It is the contract we agreed to test against. PetstoreContractTest compares it with
 * the live file, so a change on the API side shows up as a failing test rather than
 * as confusing failures somewhere else.
 *
 * Swagger 2.0 definitions are JSON Schema (draft 4) with a few extras, so the
 * "definitions" section can be fed straight to a schema validator.
 *
 * One deliberate change: Swagger allows extra, undocumented fields by default. Here
 * every object is made strict (additionalProperties: false), so a field that is
 * renamed, added or misspelt by the API fails the test instead of slipping through.
 */
public final class PetstoreContract {

    public static final String SWAGGER_FILE = "/contract/petstore-swagger.json";

    private static final JsonNode SWAGGER = loadSwagger();

    private PetstoreContract() {
    }

    public static JsonNode swagger() {
        return SWAGGER;
    }

    /**
     * Returns the list of problems found, or an empty list if the JSON matches.
     * definitionName is a name under "definitions" in the Swagger file, e.g. "Pet".
     */
    public static List<String> problemsWith(JsonNode json, String definitionName) {
        JsonSchema schema = schemaFor(definitionName);
        Set<ValidationMessage> messages = schema.validate(json);

        List<String> problems = new ArrayList<>();
        for (ValidationMessage message : messages) {
            problems.add(message.getMessage());
        }
        return problems;
    }

    public static List<String> problemsWith(String json, String definitionName) {
        try {
            JsonNode node = StrictJson.mapper().readTree(json);
            return problemsWith(node, definitionName);
        } catch (IOException e) {
            List<String> problems = new ArrayList<>();
            problems.add("Not valid JSON: " + json);
            return problems;
        }
    }

    private static JsonSchema schemaFor(String definitionName) {
        if (!SWAGGER.path("definitions").has(definitionName)) {
            throw new IllegalArgumentException("No definition called " + definitionName + " in " + SWAGGER_FILE);
        }

        ObjectNode definitions = SWAGGER.path("definitions").deepCopy();
        makeEveryObjectStrict(definitions);

        // A schema that just points at one definition and carries all of them for the $refs
        ObjectNode root = StrictJson.mapper().createObjectNode();
        root.put("$ref", "#/definitions/" + definitionName);
        root.set("definitions", definitions);

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
        return factory.getSchema(root);
    }

    private static void makeEveryObjectStrict(ObjectNode definitions) {
        Iterator<Map.Entry<String, JsonNode>> entries = definitions.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            ObjectNode definition = (ObjectNode) entry.getValue();

            // "xml" only describes the XML version of the API; it is not a JSON Schema keyword
            definition.remove("xml");

            if ("object".equals(definition.path("type").asText())) {
                definition.put("additionalProperties", false);
            }
        }
    }

    private static JsonNode loadSwagger() {
        try (InputStream in = PetstoreContract.class.getResourceAsStream(SWAGGER_FILE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + SWAGGER_FILE + " on the test classpath");
            }
            return StrictJson.mapper().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + SWAGGER_FILE, e);
        }
    }
}
