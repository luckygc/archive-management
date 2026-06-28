---
name: archive-api-design-strategy
description: Use when designing, reviewing, or refactoring archive-management HTTP APIs, controller mappings, request/response DTOs, resource names, pagination, filtering, sorting, field masks, custom methods, ProblemDetail errors, or frontend-facing ID fields. Trigger before changing project-owned API contracts, OpenSpec API sections, DTO records, or controller URLs.
---

# Archive API Design Strategy

## Overview

Use this skill before changing archive-management API contracts. The project default is Zalando RESTful API Guidelines for project-owned HTTP API resources, URLs, methods, success responses, pagination, filtering, sorting, and compatibility, adapted to Spring MVC JSON APIs. Google AIP-136 is used only as the custom method extension for complex business actions. Error responses use Spring ProblemDetail / RFC 9457 with project extension fields.

## First Checks

- Read the nearest `AGENTS.md` before editing API code or specs.
- If the task asks about Google AIP, Zalando RESTful API Guidelines, or another framework/API behavior, verify the referenced docs first.
- Inspect the relevant OpenSpec change, Controller, DTO, frontend type, and API client before changing a contract.
- Keep third-party callback protocols isolated as adapter exceptions; do not let their URL style become project-owned API style.

## Resource Model

- Model APIs around resources first, then choose standard methods or AIP-136 custom methods.
- Use `/api/v1` as the project-owned REST API prefix. Do not introduce minor or patch versions in the URL.
- Prefer standard CRUD methods:
    - `GET /api/v1/archive-categories/{archiveCategory}`
    - `GET /api/v1/archive-categories`
    - `POST /api/v1/archive-categories`
    - `PATCH /api/v1/archive-categories/{archiveCategory}`
    - `DELETE /api/v1/archive-categories/{archiveCategory}`
- Use AIP-136 custom methods only when standard methods do not fit: `POST /api/v1/archive-records/{archiveRecord}:lock`.
- Custom method verbs use lower camelCase after the colon. Do not use extra action path segments such as `/lock`, `/_lock`, `/validate_token`, or `/validateToken`.
- Collection custom methods use the collection path plus the colon verb, for example `POST /api/v1/archive-records:search` or `POST /api/v1/archive-records:batchArchive`.
- Spring MVC mappings must write the complete URL on each Controller method; do not rely on class-level `@RequestMapping` plus relative method paths.

## Names and IDs

- Resource representations may expose a stable string `name` field when a concrete business spec requires it, but `name` is not mandatory for every DTO.
- Use collection-style resource paths, for example `/api/v1/archive-categories/{archiveCategory}` and `/api/v1/archive-records/{archiveRecord}`.
- DTO can expose `id`, `categoryId`, `fieldId`, `createdBy`, `updatedBy`, and similar fields when they are the natural API contract.
- Project-owned IDs default to JSON numbers. Do not introduce Long-to-string serialization for speculative JavaScript precision risk in this archive system.
- Only external protocols or resources explicitly identified by a concrete OpenSpec as exceeding the safe integer range may use string IDs.

## Pagination and Lists

- Large or user-facing collection APIs must be paginated. Do not return unbounded bare lists unless the resource set is intentionally tiny and documented.
- Do not serialize Jakarta Data, Hibernate, MyBatis, or other persistence pagination objects directly as HTTP responses. Define project-owned response records/types.
- Use parallel project-owned records instead of inheritance or polymorphic JSON contracts: `CollectionResponse<T>` for small unpaged collections, `OffsetPageResponse<T>` for offset pages, and `CursorPageResponse<T>` for keyset/cursor pages.
- Offset pagination is for bounded or count-oriented lists: request `limit`, `offset`; response fixed `items`, `limit`, `offset`, and `total`. Run `total` as a separate count query.
- Keyset/cursor pagination is for large or complex lists: request `limit`, `cursor`; response `self`, optional `prev`, optional `next`, optional `first`, rarely `last`, and fixed `items`. These navigation fields are opaque tokens, not URLs. Do not return `total` and do not run count by default.
- Cursor pagination defaults to `limit=100`, common frontend options are `100`, `200`, `500`, and `1000`, and the default maximum is `1000` unless a concrete spec declares a special limit.
- When the user changes page size, start a fresh first-page query with the committed query state and no old cursor.
- Cursor page turns must repeat the first request's effective filters, search terms, sorting, page size, and business scope; only `cursor` changes to the returned `next` or `prev` token. Changing any of those parameters requires a fresh search without the old cursor.
- Frontend search forms must separate draft input state from the committed query state used by the current result list. Typing a new keyword updates only the draft; pagination and refresh continue using the committed query and cursor until the user explicitly submits a new search.
- Cursor tokens must bind a signed query fingerprint. Reject requests whose body/query parameters do not match the cursor fingerprint.
- Search endpoints such as `POST /api/v1/archive-records:search` may include `query` in the page response to echo the applied query body.
- Do not use AIP pagination fields such as `pageSize`, `pageToken`, and `nextPageToken` for new project-owned collection APIs.
- Do not create per-resource list response shapes with `archives`, `tasks`, or other resource-specific array fields. Use fixed `items`.
- Provide two project-level page response contracts: keyset/cursor without `total`, and offset with `total`.
- If a keyset/cursor endpoint needs a total, prefer a separate `:count` custom method. Indicate whether the total is exact when the API returns it.
- Every collection API must use the project page response contract. Do not expose framework, persistence, or internal pagination types as HTTP contracts.
- Define deterministic ordering for every paged API. Apply user ordering first, then append `createdAt DESC` and `id DESC` as fallback ordering; do not duplicate a fallback field already supplied by the user.
- Filtering and sorting names should use API field names, not database column names. Validate any dynamic field or sort expression before it reaches SQL.
- Cursor/page tokens are API contracts. Treat token format as opaque to clients.

## Async Jobs

- Long-running operations follow Microsoft Azure REST API Guidelines style: return `202 Accepted`, include an operation monitor location, and expose a pollable job resource.
- Use `JobAcceptedResponse` for accepted async starts and `JobStatusResponse` for job status reads.
- Prefer `Operation-Location` and optional `Retry-After` headers for async job starts.
- Do not return `202 Accepted` for actions that have already completed synchronously.

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
    "fieldViolations": [{ "field": "displayName", "message": "不能为空" }],
    "traceId": "..."
}
```

- Use top-level `fieldViolations: [{field, message}]` for field-level validation.
- Prefer stable project error code names such as `INVALID_ARGUMENT`, `NOT_FOUND`, `ALREADY_EXISTS`, `FAILED_PRECONDITION`, `ABORTED`, and `INTERNAL`.
- Do not make frontend code parse exception class names, stack traces, HTML error pages, or free-form validation text.

## Review Checklist

1. Identify the resource and whether the operation is standard CRUD or a custom method.
2. Verify the URL includes `/api/v1`, follows Zalando REST style for normal resources, and each Controller method declares the complete path.
3. Ensure response DTO IDs use numeric contracts unless a concrete spec explicitly requires string IDs.
4. Ensure growing collection APIs use the unified page contract: offset returns `items` plus `total`, keyset/cursor returns `items` and cursors without `total`.
5. Map validation, not-found, conflict, and precondition failures to ProblemDetail error bodies.
6. Update OpenSpec, frontend types, and API clients together when changing a contract.
