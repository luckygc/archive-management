package github.luckygc.am.module.archive.library.web;

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
import github.luckygc.am.module.archive.library.ArchiveRepositoryRole;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService.ArchiveRepositoryResponse;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService.CreateArchiveRepositoryRequest;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService.UpdateArchiveRepositoryRequest;

import tools.jackson.databind.JsonNode;

@RestController
public class ArchiveRepositoryController {

    private static final Set<String> PATCH_FIELDS =
            Set.of("repositoryCode", "repositoryName", "repositoryRole", "enabled", "sortOrder");
    private static final Set<String> READ_ONLY_FIELDS =
            Set.of("id", "systemFlag", "createdAt", "updatedAt");

    private final ArchiveRepositoryService service;

    public ArchiveRepositoryController(ArchiveRepositoryService service) {
        this.service = service;
    }

    @GetMapping("/archive-repositories")
    public CollectionResponse<ArchiveRepositoryResponse> list(Boolean enabled) {
        return CollectionResponse.of(service.list(enabled));
    }

    @PostMapping("/archive-repositories")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveRepositoryResponse create(
            @RequestBody CreateArchiveRepositoryRequest request, Authentication authentication) {
        return service.create(request, userId(authentication));
    }

    @PatchMapping(value = "/archive-repositories/{id}", consumes = "application/merge-patch+json")
    public ArchiveRepositoryResponse update(
            @PathVariable Long id, @RequestBody JsonNode request, Authentication authentication) {
        Long userId = userId(authentication);
        return service.update(id, toUpdateRequest(request), userId);
    }

    private UpdateArchiveRepositoryRequest toUpdateRequest(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new BadRequestException("业务库补丁必须是对象");
        }
        for (String fieldName : request.propertyNames()) {
            if (!PATCH_FIELDS.contains(fieldName)
                    && (READ_ONLY_FIELDS.contains(fieldName) || !request.get(fieldName).isNull())) {
                throw new BadRequestException("不支持修改字段 " + fieldName, fieldName, "字段不可修改");
            }
        }
        return new UpdateArchiveRepositoryRequest(
                textField(request, "repositoryCode"),
                textField(request, "repositoryName"),
                roleField(request),
                booleanField(request, "enabled"),
                integerField(request, "sortOrder"));
    }

    private @Nullable String textField(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null) {
            return null;
        }
        if (value.isNull() || !value.isTextual()) {
            throw invalidField(field);
        }
        return value.asText();
    }

    private @Nullable ArchiveRepositoryRole roleField(JsonNode request) {
        String value = textField(request, "repositoryRole");
        if (value == null) {
            return null;
        }
        try {
            return ArchiveRepositoryRole.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalidField("repositoryRole");
        }
    }

    private @Nullable Boolean booleanField(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null) {
            return null;
        }
        if (value.isNull() || !value.isBoolean()) {
            throw invalidField(field);
        }
        return value.asBoolean();
    }

    private @Nullable Integer integerField(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null) {
            return null;
        }
        if (value.isNull() || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalidField(field);
        }
        return value.asInt();
    }

    private BadRequestException invalidField(String field) {
        return new BadRequestException(field + " 不合法", field, field + " 不合法");
    }

    @DeleteMapping("/archive-repositories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, userId(authentication));
    }

    private Long userId(Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }
}
