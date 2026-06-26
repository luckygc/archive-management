---
name: archive-api-design-strategy
description: Use when designing, reviewing, or refactoring archive-management HTTP APIs, controller mappings, request/response DTOs, resource names, pagination, filtering, sorting, field masks, custom methods, ProblemDetail errors, or frontend-facing ID fields. Trigger before changing project-owned API contracts, OpenSpec API sections, DTO records, or controller URLs.
---

# Archive API Design Strategy

## Overview

Use this skill before changing archive-management API contracts. The project default is Google Cloud API Design Guide / AIP for project-owned HTTP API resources, URLs, methods, success responses, filtering, sorting, and compatibility, adapted to Spring MVC JSON APIs. Error responses use Spring ProblemDetail / RFC 9457 with project extension fields.

## First Checks

- Read the nearest `AGENTS.md` before editing API code or specs.
- If the task asks about Google AIP or another framework/API behavior, fetch current docs with `npx ctx7@latest` first.
- Inspect the relevant OpenSpec change, Controller, DTO, frontend type, and API client before changing a contract.
- Keep third-party callback protocols isolated as adapter exceptions; do not let their URL style become project-owned API style.

## Resource Model

- Model APIs around resources first, then choose standard methods or custom methods.
- Use `/api/v1` as the project-owned REST API prefix. Do not introduce minor or patch versions in the URL.
- Prefer standard CRUD methods:
  - `GET /api/v1/archive-categories/{archiveCategory}`
  - `GET /api/v1/archive-categories`
  - `POST /api/v1/archive-categories`
  - `PATCH /api/v1/archive-categories/{archiveCategory}`
  - `DELETE /api/v1/archive-categories/{archiveCategory}`
- Use AIP-style custom methods only when standard methods do not fit: `POST /api/v1/archive-records/{archiveRecord}:lock`.
- Custom method verbs use lower camelCase after the colon. Do not use extra action path segments such as `/lock`, `/_lock`, `/validate_token`, or `/validateToken`.
- Spring MVC mappings must write the complete URL on each Controller method; do not rely on class-level `@RequestMapping` plus relative method paths.

## Names and IDs

- AIP-122/AIP-148: resource representations should use a string `name` field as the primary resource name, and `name` should be the first resource field when the DTO is resource-shaped.
- Use collection-style resource names, for example `archiveCategories/{archiveCategory}` and `archiveRecords/{archiveRecord}`.
- Short-term compatibility may keep `id`, `categoryId`, `fieldId`, `createdBy`, `updatedBy`, and similar fields. Only resources explicitly identified by `openspec/specs/api-contract/spec.md` or a concrete feature spec as high-growth or JavaScript precision-risk data must serialize database `Long`/`BigInt` IDs as `String`.
- Response records/DTOs for explicitly identified resources expose `String id` and related ID fields. Metadata configuration DTOs such as archive categories, fields, layouts, and unique rules may keep numeric IDs until a spec requires migration. Internal entities, mapper models, and service-local variables may keep `Long`.
- Path variables may be accepted as `String`; parse and validate them in the Service or a narrow parsing helper, then return ProblemDetail errors with `code=INVALID_ARGUMENT` for malformed IDs.
- Do not expose JavaScript-unsafe numeric identifiers to the frontend. Avoid relying on `number` for database IDs in TypeScript types.

## Pagination and Lists

- Large or user-facing collection APIs must be paginated. Do not return unbounded bare lists unless the resource set is intentionally tiny and documented.
- Cursor pagination uses AIP JSON field names: request `pageSize`, `pageToken`; response `nextPageToken`.
- Offset pagination uses dedicated request and response fields: request `pageSize`, `pageOffset`; response includes `pageSize`, `pageOffset`, and `totalSize`.
- Every collection API must use a dedicated list/page response DTO. Do not expose generic internal pagination types as HTTP contracts.
- Define deterministic ordering for every paged API. Add a unique immutable tie-breaker such as `id` when ordering by a non-unique business field.
- Filtering and sorting names should use API field names, not database column names. Validate any dynamic field or sort expression before it reaches SQL.
- Cursor/page tokens are API contracts. Treat token format as opaque to clients.

## Errors

- Project-owned errors use Spring ProblemDetail / RFC 9457 JSON shape with project extension fields:

```json
{
  "type": "about:blank",
  "title": "请求参数无效",
  "status": 400,
  "detail": "...",
  "code": "INVALID_ARGUMENT",
  "reason": "FIELD_VIOLATION",
  "fieldViolations": [
    {"field": "displayName", "message": "不能为空"}
  ],
  "traceId": "..."
}
```

- Use top-level `fieldViolations: [{field, message}]` for field-level validation.
- Prefer stable project error code names such as `INVALID_ARGUMENT`, `NOT_FOUND`, `ALREADY_EXISTS`, `FAILED_PRECONDITION`, `ABORTED`, and `INTERNAL`.
- Do not make frontend code parse exception class names, stack traces, HTML error pages, or free-form validation text.

## Review Checklist

1. Identify the resource and whether the operation is standard CRUD or a custom method.
2. Verify the URL includes `/api/v1` and each Controller method declares the complete path.
3. Ensure response DTO IDs are strings only for resources explicitly required to avoid JavaScript numeric precision risk; do not migrate ordinary metadata IDs without a spec requirement.
4. Ensure collection APIs use a dedicated cursor or offset pagination response DTO and stable ordering when the result can grow.
5. Map validation, not-found, conflict, and precondition failures to ProblemDetail error bodies.
6. Update OpenSpec, frontend types, and API clients together when changing a contract.
