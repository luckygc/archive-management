package github.luckygc.am.module.archive.ontology.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.ArchiveOntologyAttributeMappingResponse;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.ArchiveOntologyAttributeTypeResponse;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.ArchiveOntologyEventTypeResponse;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.ArchiveOntologyObjectTypeResponse;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.ArchiveOntologyRelationTypeResponse;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.CreateArchiveOntologyAttributeMappingRequest;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.CreateArchiveOntologyAttributeTypeRequest;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.CreateArchiveOntologyEventTypeRequest;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.CreateArchiveOntologyObjectTypeRequest;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.CreateArchiveOntologyRelationTypeRequest;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.UpdateArchiveOntologyAttributeTypeRequest;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.UpdateArchiveOntologyEventTypeRequest;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.UpdateArchiveOntologyObjectTypeRequest;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyService.UpdateArchiveOntologyRelationTypeRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@RestController
public class ArchiveOntologyController {

    private final ArchiveOntologyService ontologyService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveOntologyController(
            ArchiveOntologyService ontologyService,
            AuthorizationPermissionService permissionService) {
        this.ontologyService = ontologyService;
        this.permissionService = permissionService;
    }

    @GetMapping("/api/v1/archive-ontology-object-types")
    public CollectionResponse<ArchiveOntologyObjectTypeResponse> listObjectTypes(Boolean enabled) {
        return CollectionResponse.of(ontologyService.listObjectTypes(enabled));
    }

    @PostMapping("/api/v1/archive-ontology-object-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveOntologyObjectTypeResponse createObjectType(
            @RequestBody CreateArchiveOntologyObjectTypeRequest request,
            Authentication authentication) {
        return ontologyService.createObjectType(request, requireManage(authentication));
    }

    @PatchMapping("/api/v1/archive-ontology-object-types/{objectTypeId}")
    public ArchiveOntologyObjectTypeResponse updateObjectType(
            @PathVariable Long objectTypeId,
            @RequestBody UpdateArchiveOntologyObjectTypeRequest request,
            Authentication authentication) {
        return ontologyService.updateObjectType(
                objectTypeId, request, requireManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-ontology-object-types/{objectTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteObjectType(@PathVariable Long objectTypeId, Authentication authentication) {
        ontologyService.deleteObjectType(objectTypeId, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-ontology-object-types:initializeBuiltins")
    public CollectionResponse<ArchiveOntologyObjectTypeResponse> initializeBuiltInObjectTypes(
            Authentication authentication) {
        return CollectionResponse.of(
                ontologyService.initializeBuiltInObjectTypes(requireManage(authentication)));
    }

    @GetMapping("/api/v1/archive-ontology-attribute-types")
    public CollectionResponse<ArchiveOntologyAttributeTypeResponse> listAttributeTypes(
            @RequestParam(required = false) Long objectTypeId) {
        return CollectionResponse.of(ontologyService.listAttributeTypes(objectTypeId));
    }

    @PostMapping("/api/v1/archive-ontology-attribute-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveOntologyAttributeTypeResponse createAttributeType(
            @RequestBody CreateArchiveOntologyAttributeTypeRequest request,
            Authentication authentication) {
        return ontologyService.createAttributeType(request, requireManage(authentication));
    }

    @PatchMapping("/api/v1/archive-ontology-attribute-types/{attributeTypeId}")
    public ArchiveOntologyAttributeTypeResponse updateAttributeType(
            @PathVariable Long attributeTypeId,
            @RequestBody UpdateArchiveOntologyAttributeTypeRequest request,
            Authentication authentication) {
        return ontologyService.updateAttributeType(
                attributeTypeId, request, requireManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-ontology-attribute-types/{attributeTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttributeType(
            @PathVariable Long attributeTypeId, Authentication authentication) {
        ontologyService.deleteAttributeType(attributeTypeId, requireManage(authentication));
    }

    @GetMapping("/api/v1/archive-ontology-attribute-mappings")
    public CollectionResponse<ArchiveOntologyAttributeMappingResponse> listAttributeMappings(
            @RequestParam(required = false) Long attributeTypeId) {
        return CollectionResponse.of(ontologyService.listAttributeMappings(attributeTypeId));
    }

    @PostMapping("/api/v1/archive-ontology-attribute-mappings")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveOntologyAttributeMappingResponse createAttributeMapping(
            @RequestBody CreateArchiveOntologyAttributeMappingRequest request,
            Authentication authentication) {
        return ontologyService.createAttributeMapping(request, requireManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-ontology-attribute-mappings/{mappingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttributeMapping(
            @PathVariable Long mappingId, Authentication authentication) {
        ontologyService.deleteAttributeMapping(mappingId, requireManage(authentication));
    }

    @GetMapping("/api/v1/archive-ontology-relation-types")
    public CollectionResponse<ArchiveOntologyRelationTypeResponse> listRelationTypes() {
        return CollectionResponse.of(ontologyService.listRelationTypes());
    }

    @PostMapping("/api/v1/archive-ontology-relation-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveOntologyRelationTypeResponse createRelationType(
            @RequestBody CreateArchiveOntologyRelationTypeRequest request,
            Authentication authentication) {
        return ontologyService.createRelationType(request, requireManage(authentication));
    }

    @PatchMapping("/api/v1/archive-ontology-relation-types/{relationTypeId}")
    public ArchiveOntologyRelationTypeResponse updateRelationType(
            @PathVariable Long relationTypeId,
            @RequestBody UpdateArchiveOntologyRelationTypeRequest request,
            Authentication authentication) {
        return ontologyService.updateRelationType(
                relationTypeId, request, requireManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-ontology-relation-types/{relationTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRelationType(
            @PathVariable Long relationTypeId, Authentication authentication) {
        ontologyService.deleteRelationType(relationTypeId, requireManage(authentication));
    }

    @GetMapping("/api/v1/archive-ontology-event-types")
    public CollectionResponse<ArchiveOntologyEventTypeResponse> listEventTypes() {
        return CollectionResponse.of(ontologyService.listEventTypes());
    }

    @PostMapping("/api/v1/archive-ontology-event-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveOntologyEventTypeResponse createEventType(
            @RequestBody CreateArchiveOntologyEventTypeRequest request,
            Authentication authentication) {
        return ontologyService.createEventType(request, requireManage(authentication));
    }

    @PatchMapping("/api/v1/archive-ontology-event-types/{eventTypeId}")
    public ArchiveOntologyEventTypeResponse updateEventType(
            @PathVariable Long eventTypeId,
            @RequestBody UpdateArchiveOntologyEventTypeRequest request,
            Authentication authentication) {
        return ontologyService.updateEventType(eventTypeId, request, requireManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-ontology-event-types/{eventTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEventType(@PathVariable Long eventTypeId, Authentication authentication) {
        ontologyService.deleteEventType(eventTypeId, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-ontology-event-types:initializeBuiltins")
    public CollectionResponse<ArchiveOntologyEventTypeResponse> initializeBuiltInEventTypes(
            Authentication authentication) {
        return CollectionResponse.of(
                ontologyService.initializeBuiltInEventTypes(requireManage(authentication)));
    }

    private Long requireManage(Authentication authentication) {
        Long userId =
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal());
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.ARCHIVE_GOVERNANCE_MANAGE);
        return userId;
    }
}
