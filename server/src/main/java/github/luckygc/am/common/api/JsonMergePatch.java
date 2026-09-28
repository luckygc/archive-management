package github.luckygc.am.common.api;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public final class JsonMergePatch {

    private JsonMergePatch() {}

    public static JsonNode apply(@Nullable JsonNode target, JsonNode patch) {
        if (!patch.isObject()) {
            return patch.deepCopy();
        }
        ObjectNode result =
                target instanceof ObjectNode object
                        ? object.deepCopy()
                        : JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> entry : patch.properties()) {
            if (entry.getValue().isNull()) {
                result.remove(entry.getKey());
            } else {
                result.set(entry.getKey(), apply(result.get(entry.getKey()), entry.getValue()));
            }
        }
        return result;
    }
}
