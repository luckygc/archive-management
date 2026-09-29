package github.luckygc.am.app;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import github.luckygc.am.module.archive.item.service.ArchiveItemLockService;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveItemRelationService;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService;
import github.luckygc.am.module.archive.item.service.ArchiveItemService;
import github.luckygc.am.module.archive.item.web.ArchiveItemController;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService;
import github.luckygc.am.module.archive.physical.web.ArchivePhysicalObjectController;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.web.FileLinkDownloadController;

@WebMvcTest({
    ArchiveItemController.class,
    ArchivePhysicalObjectController.class,
    FileLinkDownloadController.class
})
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration({
    SpringDocConfiguration.class,
    SpringDocConfigProperties.class,
    SpringDocWebMvcConfiguration.class
})
class ApiContractWebTests {

    @SpringBootConfiguration
    @Import({
        ArchiveItemController.class,
        ArchivePhysicalObjectController.class,
        FileLinkDownloadController.class,
        OpenApiConfiguration.class
    })
    static class TestApplication {}

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ArchiveItemService archiveItemService;
    @MockitoBean private ArchiveItemSearchService archiveItemSearchService;
    @MockitoBean private ArchiveItemReadService archiveItemReadService;
    @MockitoBean private ArchiveItemRelationService archiveItemRelationService;
    @MockitoBean private ArchiveItemLockService archiveItemLockService;
    @MockitoBean private FileLinkService fileLinkService;
    @MockitoBean private ArchivePhysicalObjectService archivePhysicalObjectService;

    @Test
    void publishesArchiveItemRoutesAsOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"/archive-items\"")))
                .andExpect(content().string(containsString("\"/archive-items:search\"")))
                .andExpect(
                        jsonPath(
                                "$.paths['/archive-items:search'].post.parameters[*].name",
                                hasItems("sort", "limit", "cursor")))
                .andExpect(
                        jsonPath(
                                "$.paths['/archive-items:search'].post.parameters[*].name",
                                not(hasItem("page"))))
                .andExpect(
                        jsonPath(
                                        "$.paths['/archive-items:search'].post.responses['200'].content['application/json']")
                                .exists())
                .andExpect(
                        jsonPath(
                                        "$.paths['/archive-items:search'].post.responses.default.content['application/problem+json'].schema['$ref']")
                                .value("#/components/schemas/ProblemDetail"))
                .andExpect(
                        jsonPath("$.components.schemas.ProblemDetail.properties.type.type")
                                .value("string"));
    }

    @Test
    void describesDownloadsAsBinaryResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                        "$.paths['/file-links/{code}:download'].get.responses['200'].content['application/octet-stream'].schema.format")
                                .value("binary"));
    }

    @Test
    void announcesMergePatchOnOptions() throws Exception {
        mockMvc.perform(options("/archive-items/42"))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Patch", "application/merge-patch+json"));
    }

    @Test
    void describesPhysicalLocationHistoryPagination() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.paths['/archive-physical-objects/{id}/location-history'].get.parameters[*].name",
                                hasItems("id", "limit", "cursor")))
                .andExpect(
                        jsonPath(
                                "$.paths['/archive-physical-objects/{id}/location-history'].get.parameters[*].name",
                                not(hasItem("page"))));
    }
}
