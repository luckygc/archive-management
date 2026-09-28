package github.luckygc.am.module.archive.physical.web;

import java.math.BigDecimal;
import java.util.Set;

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
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.ArchivePhysicalLocationHistoryResponse;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.ArchivePhysicalObjectResponse;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.BatchAssignArchiveLocationRequest;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.BatchAssignArchiveLocationResponse;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.CreateArchivePhysicalObjectRequest;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.UpdateArchivePhysicalObjectRequest;

import tools.jackson.databind.JsonNode;

@RestController
public class ArchivePhysicalObjectController {

    private static final Set<String> PATCH_FIELDS =
            Set.of("barcode", "carrierType", "quantity", "quantityUnit", "conditionNote", "remark");
    private static final Set<String> READ_ONLY_FIELDS =
            Set.of(
                    "id",
                    "archiveItemId",
                    "archiveVolumeId",
                    "custodyStatus",
                    "currentLocationId",
                    "createdAt",
                    "updatedAt");

    private final ArchivePhysicalObjectService service;

    public ArchivePhysicalObjectController(ArchivePhysicalObjectService service) {
        this.service = service;
    }

    @GetMapping("/archive-items/{archiveItemId}/physical-object")
    public ArchivePhysicalObjectResponse getByArchiveItem(
            @PathVariable Long archiveItemId, Authentication authentication) {
        return service.getByArchiveItem(archiveItemId, userId(authentication));
    }

    @GetMapping("/archive-volumes/{archiveVolumeId}/physical-object")
    public ArchivePhysicalObjectResponse getByArchiveVolume(
            @PathVariable Long archiveVolumeId, Authentication authentication) {
        return service.getByArchiveVolume(archiveVolumeId, userId(authentication));
    }

    @PostMapping("/archive-physical-objects")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchivePhysicalObjectResponse create(
            @RequestBody CreateArchivePhysicalObjectRequest request,
            Authentication authentication) {
        return service.create(request, userId(authentication));
    }

    @GetMapping("/archive-physical-objects/{id}")
    public ArchivePhysicalObjectResponse get(@PathVariable Long id, Authentication authentication) {
        return service.get(id, userId(authentication));
    }

    @PatchMapping(
            value = "/archive-physical-objects/{id}",
            consumes = "application/merge-patch+json")
    public ArchivePhysicalObjectResponse update(
            @PathVariable Long id, @RequestBody JsonNode request, Authentication authentication) {
        Long operatorUserId = userId(authentication);
        if (request == null || !request.isObject()) {
            throw new BadRequestException("实物对象补丁必须是对象");
        }
        for (String field : request.propertyNames()) {
            if (!PATCH_FIELDS.contains(field)
                    && (READ_ONLY_FIELDS.contains(field) || !request.get(field).isNull())) {
                throw new BadRequestException("不支持修改字段 " + field, field, "字段不可修改");
            }
        }
        return service.update(
                id,
                new UpdateArchivePhysicalObjectRequest(
                        Set.copyOf(request.propertyNames()),
                        textField(request, "barcode"),
                        textField(request, "carrierType"),
                        quantityField(request),
                        textField(request, "quantityUnit"),
                        textField(request, "conditionNote"),
                        textField(request, "remark")),
                operatorUserId);
    }

    @DeleteMapping("/archive-physical-objects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, userId(authentication));
    }

    @PostMapping("/archive-physical-objects:batchAssignLocation")
    public BatchAssignArchiveLocationResponse batchAssignLocation(
            @RequestBody BatchAssignArchiveLocationRequest request, Authentication authentication) {
        return service.batchAssignLocation(request, userId(authentication));
    }

    @GetMapping("/archive-physical-objects/{id}/location-history")
    public CollectionResponse<ArchivePhysicalLocationHistoryResponse> listLocationHistory(
            @PathVariable Long id, Authentication authentication) {
        return CollectionResponse.of(service.listLocationHistory(id, userId(authentication)));
    }

    private Long userId(Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }

    private @Nullable String textField(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.asText();
    }

    private @Nullable BigDecimal quantityField(JsonNode request) {
        JsonNode value = request.get("quantity");
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new BadRequestException("quantity 不合法", "quantity", "quantity 不合法");
        }
        return value.decimalValue();
    }
}
