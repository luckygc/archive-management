package github.luckygc.am.module.archive.item.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService.ArchiveItemLineRowResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService.ArchiveItemLineTableDefinitionResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService.CreateArchiveItemLineRowRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService.PatchArchiveItemLineRowRequest;

import tools.jackson.databind.JsonNode;

@RestController
public class ArchiveItemLineRowController {

    private static final Set<String> PATCH_FIELDS = Set.of("lineOrder", "values");

    private final ArchiveItemLineRowService archiveItemLineRowService;

    public ArchiveItemLineRowController(ArchiveItemLineRowService archiveItemLineRowService) {
        this.archiveItemLineRowService = archiveItemLineRowService;
    }

    @GetMapping("/api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows")
    public CursorPageResponse<ArchiveItemLineRowResponse> listRows(
            @PathVariable Long archiveItem,
            @PathVariable Long lineTable,
            PageRequest page,
            Authentication authentication) {
        return archiveItemLineRowService.listRows(
                archiveItem, lineTable, page, userId(authentication));
    }

    @GetMapping("/api/v1/archive-items/{archiveItem}/line-tables")
    public CollectionResponse<ArchiveItemLineTableDefinitionResponse> listLineTables(
            @PathVariable Long archiveItem, Authentication authentication) {
        return CollectionResponse.of(
                archiveItemLineRowService.listLineTables(archiveItem, userId(authentication)));
    }

    @PostMapping("/api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveItemLineRowResponse createRow(
            @PathVariable Long archiveItem,
            @PathVariable Long lineTable,
            @RequestBody(required = false) CreateArchiveItemLineRowRequest request,
            Authentication authentication) {
        return archiveItemLineRowService.createRow(
                archiveItem, lineTable, request, userId(authentication));
    }

    @PatchMapping("/api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows/{row}")
    public ArchiveItemLineRowResponse patchRow(
            @PathVariable Long archiveItem,
            @PathVariable Long lineTable,
            @PathVariable Long row,
            @RequestBody(required = false) JsonNode body,
            Authentication authentication) {
        return archiveItemLineRowService.patchRow(
                archiveItem, lineTable, row, parsePatch(body), userId(authentication));
    }

    @DeleteMapping("/api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows/{row}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRow(
            @PathVariable Long archiveItem,
            @PathVariable Long lineTable,
            @PathVariable Long row,
            Authentication authentication) {
        archiveItemLineRowService.deleteRow(archiveItem, lineTable, row, userId(authentication));
    }

    private PatchArchiveItemLineRowRequest parsePatch(@Nullable JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BadRequestException("请求体必须为 JSON 对象");
        }
        for (Map.Entry<String, JsonNode> entry : body.properties()) {
            if (!PATCH_FIELDS.contains(entry.getKey())) {
                throw new BadRequestException("未知请求字段：" + entry.getKey(), entry.getKey(), "未知请求字段");
            }
        }
        boolean lineOrderPresent = body.has("lineOrder");
        @Nullable Integer lineOrder = null;
        if (lineOrderPresent) {
            JsonNode node = body.get("lineOrder");
            if (node == null || node.isNull()) {
                throw new BadRequestException("lineOrder 不能为 null", "lineOrder", "不能为 null");
            }
            if (!node.isIntegralNumber() || !node.canConvertToInt()) {
                throw new BadRequestException("lineOrder 不合法", "lineOrder", "必须为整数");
            }
            lineOrder = node.asInt();
        }
        boolean valuesPresent = body.has("values");
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        if (valuesPresent) {
            JsonNode valuesNode = body.get("values");
            if (valuesNode == null || !valuesNode.isObject()) {
                throw new BadRequestException("values 不合法", "values", "必须为 JSON 对象");
            }
            for (Map.Entry<String, JsonNode> entry : valuesNode.properties()) {
                values.put(entry.getKey(), scalarValue(entry.getKey(), entry.getValue()));
            }
        }
        return new PatchArchiveItemLineRowRequest(
                lineOrderPresent, lineOrder, valuesPresent, values);
    }

    private @Nullable Object scalarValue(String fieldCode, JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.canConvertToInt() ? node.asInt() : node.asLong();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        throw new BadRequestException(
                "字段值类型不合法：" + fieldCode, "values." + fieldCode, "只允许字符串、数字、布尔值或 null");
    }

    private Long userId(@Nullable Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }
}
