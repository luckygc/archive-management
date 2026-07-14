import { httpClient } from "@archive-management/frontend-core/api";
import type {
    ArchiveOntologyAttributeMappingDto,
    ArchiveOntologyAttributeMappingRequest,
    ArchiveOntologyAttributeTypeDto,
    ArchiveOntologyAttributeTypeRequest,
    ArchiveOntologyEventTypeDto,
    ArchiveOntologyEventTypeRequest,
    ArchiveOntologyObjectTypeDto,
    ArchiveOntologyObjectTypeRequest,
    ArchiveOntologyRelationTypeDto,
    ArchiveOntologyRelationTypeRequest,
} from "../types/archive-ontology";
import type { CollectionResponse } from "../types/pagination";
import { queryString } from "./query-string";

export function listArchiveOntologyObjectTypes(enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveOntologyObjectTypeDto>>(
        `/api/v1/archive-ontology-object-types${queryString({ enabled })}`,
    );
}

export function createArchiveOntologyObjectType(payload: ArchiveOntologyObjectTypeRequest) {
    return httpClient.post<ArchiveOntologyObjectTypeDto>(
        "/api/v1/archive-ontology-object-types",
        payload,
    );
}

export function initializeArchiveOntologyObjectTypes() {
    return httpClient.post<CollectionResponse<ArchiveOntologyObjectTypeDto>>(
        "/api/v1/archive-ontology-object-types:initializeBuiltins",
    );
}

export function listArchiveOntologyAttributeTypes(objectTypeId?: number) {
    return httpClient.get<CollectionResponse<ArchiveOntologyAttributeTypeDto>>(
        `/api/v1/archive-ontology-attribute-types${queryString({ objectTypeId })}`,
    );
}

export function createArchiveOntologyAttributeType(payload: ArchiveOntologyAttributeTypeRequest) {
    return httpClient.post<ArchiveOntologyAttributeTypeDto>(
        "/api/v1/archive-ontology-attribute-types",
        payload,
    );
}

export function listArchiveOntologyAttributeMappings(attributeTypeId?: number) {
    return httpClient.get<CollectionResponse<ArchiveOntologyAttributeMappingDto>>(
        `/api/v1/archive-ontology-attribute-mappings${queryString({ attributeTypeId })}`,
    );
}

export function createArchiveOntologyAttributeMapping(
    payload: ArchiveOntologyAttributeMappingRequest,
) {
    return httpClient.post<ArchiveOntologyAttributeMappingDto>(
        "/api/v1/archive-ontology-attribute-mappings",
        payload,
    );
}

export function deleteArchiveOntologyAttributeMapping(id: number) {
    return httpClient.delete<void>(`/api/v1/archive-ontology-attribute-mappings/${id}`);
}

export function listArchiveOntologyRelationTypes() {
    return httpClient.get<CollectionResponse<ArchiveOntologyRelationTypeDto>>(
        "/api/v1/archive-ontology-relation-types",
    );
}

export function createArchiveOntologyRelationType(payload: ArchiveOntologyRelationTypeRequest) {
    return httpClient.post<ArchiveOntologyRelationTypeDto>(
        "/api/v1/archive-ontology-relation-types",
        payload,
    );
}

export function listArchiveOntologyEventTypes() {
    return httpClient.get<CollectionResponse<ArchiveOntologyEventTypeDto>>(
        "/api/v1/archive-ontology-event-types",
    );
}

export function createArchiveOntologyEventType(payload: ArchiveOntologyEventTypeRequest) {
    return httpClient.post<ArchiveOntologyEventTypeDto>(
        "/api/v1/archive-ontology-event-types",
        payload,
    );
}

export function initializeArchiveOntologyEventTypes() {
    return httpClient.post<CollectionResponse<ArchiveOntologyEventTypeDto>>(
        "/api/v1/archive-ontology-event-types:initializeBuiltins",
    );
}
