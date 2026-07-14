package github.luckygc.am.module.archive.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("档案本体职责边界")
class ArchiveOntologyServiceBoundaryTests {

    @Test
    @DisplayName("对象属性入口不转发关系和事件用例")
    void objectAttributeEntryShouldNotForwardRelationUseCases() {
        assertThat(
                        Arrays.stream(ArchiveOntologyService.class.getDeclaredMethods())
                                .map(Method::getName))
                .doesNotContain(
                        "listRelationTypes",
                        "createRelationType",
                        "updateRelationType",
                        "deleteRelationType",
                        "listEventTypes",
                        "initializeBuiltInEventTypes",
                        "createEventType",
                        "updateEventType",
                        "deleteEventType");
    }
}
