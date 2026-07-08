package github.luckygc.am.module.archive.item.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.RawRequestStrings;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService.ArchiveExcelFile;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService.ArchiveImportResult;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.SearchArchiveItemsRequest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class ArchiveItemImportExportController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ArchiveItemImportExportService importExportService;
    private final JsonMapper jsonMapper;

    public ArchiveItemImportExportController(
            ArchiveItemImportExportService importExportService, JsonMapper jsonMapper) {
        this.importExportService = importExportService;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping("/api/v1/archive-categories/{categoryId}/archive-items:importTemplate")
    public ResponseEntity<ByteArrayResource> downloadImportTemplate(
            @PathVariable Long categoryId, Authentication authentication) {
        return excelResponse(
                importExportService.generateImportTemplate(
                        categoryId,
                        AuthenticatedUsers.requireUserId(
                                authentication == null ? null : authentication.getPrincipal())));
    }

    @PostMapping(
            path = "/api/v1/archive-categories/{categoryId}/archive-items:import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ArchiveImportResult importItems(
            @PathVariable Long categoryId,
            @RequestParam MultipartFile file,
            Authentication authentication)
            throws IOException {
        return importExportService.importItems(
                categoryId,
                file.getInputStream(),
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/api/v1/archive-items:export")
    public ResponseEntity<ByteArrayResource> exportItems(
            @RawRequestStrings @RequestBody(required = false) SearchArchiveItemsRequest request,
            Authentication authentication) {
        return excelResponse(
                importExportService.exportItems(
                        request,
                        AuthenticatedUsers.requireUserId(
                                authentication == null ? null : authentication.getPrincipal())));
    }

    @GetMapping("/api/v1/archive-items:export")
    public ResponseEntity<ByteArrayResource> exportItemsFromLink(
            @RequestParam(required = false) String query, Authentication authentication) {
        return excelResponse(
                importExportService.exportItems(
                        decodeExportQuery(query),
                        AuthenticatedUsers.requireUserId(
                                authentication == null ? null : authentication.getPrincipal())));
    }

    private ResponseEntity<ByteArrayResource> excelResponse(ArchiveExcelFile file) {
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename())
                                .build()
                                .toString())
                .contentLength(file.bytes().length)
                .body(new ByteArrayResource(file.bytes()));
    }

    private @Nullable SearchArchiveItemsRequest decodeExportQuery(@Nullable String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(query);
            return jsonMapper.readValue(
                    new String(bytes, StandardCharsets.UTF_8), SearchArchiveItemsRequest.class);
        } catch (IllegalArgumentException | JacksonException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导出查询参数无效", ex);
        }
    }
}
