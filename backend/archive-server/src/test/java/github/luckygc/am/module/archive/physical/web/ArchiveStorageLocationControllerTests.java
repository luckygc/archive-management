package github.luckygc.am.module.archive.physical.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

@DisplayName("真实库房与存放位置 HTTP 入口")
class ArchiveStorageLocationControllerTests {

    @Test
    @DisplayName("真实库房和存放位置不复用虚拟业务库资源")
    void physicalResourcesStaySeparateFromArchiveRepositories() throws Exception {
        Method warehouses =
                ArchiveStorageLocationController.class.getDeclaredMethod(
                        "listWarehouses", Boolean.class);
        Method locations =
                ArchiveStorageLocationController.class.getDeclaredMethod(
                        "listLocations", Long.class);

        assertThat(warehouses.getAnnotation(GetMapping.class).value())
                .containsExactly("/api/v1/archive-warehouses");
        assertThat(locations.getAnnotation(GetMapping.class).value())
                .containsExactly("/api/v1/archive-storage-locations");
    }
}
