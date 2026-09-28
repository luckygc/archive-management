package github.luckygc.am.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("JSON Merge Patch")
class JsonMergePatchTests {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("递归合并对象、删除成员并整体替换数组")
    void appliesRfc7396Rules() {
        var original =
                mapper.readTree("{\"graph\":{\"nodes\":[1,2],\"edges\":[3]},\"name\":\"旧名\"}");
        var patch = mapper.readTree("{\"graph\":{\"nodes\":[4],\"missing\":null},\"name\":null}");

        var merged = JsonMergePatch.apply(original, patch);

        assertThat(merged).isEqualTo(mapper.readTree("{\"graph\":{\"nodes\":[4],\"edges\":[3]}}"));
        assertThat(original.get("name").asText()).isEqualTo("旧名");
    }

    @Test
    @DisplayName("非对象补丁替换整个目标")
    void replacesTargetForNonObjectPatch() {
        var merged =
                JsonMergePatch.apply(mapper.readTree("{\"name\":\"旧名\"}"), mapper.readTree("[]"));

        assertThat(merged).isEqualTo(mapper.readTree("[]"));
    }
}
