# file-preview-service Specification

## Purpose
TBD - created by archiving change add-preview-service. Update Purpose after archive.
## Requirements
### Requirement: Preview service runtime boundary

The system SHALL provide a file preview service as an independently buildable and runnable Go service under `preview/`.

#### Scenario: Service can be checked for liveness

- **WHEN** a client sends `GET /healthz`
- **THEN** the service returns HTTP 200 with a JSON status body.

#### Scenario: Service exposes its preview capabilities

- **WHEN** a client sends `GET /v1/capabilities`
- **THEN** the service returns supported preview strategies and known format groups.

### Requirement: File detection

The preview service SHALL prefer Google Magika for submitted file type detection and SHALL fall back to local lightweight detection when Magika is unavailable.

#### Scenario: Magika result is used when available

- **WHEN** Magika returns an OK JSON result for an uploaded file
- **THEN** the service classifies the file using Magika's output label, MIME type, group and text flag.

#### Scenario: PDF is detected from bytes

- **WHEN** Magika is unavailable and a multipart upload contains bytes starting with the PDF signature
- **THEN** the service classifies the file as PDF even if the declared content type is generic.

#### Scenario: Text is detected from UTF-8 bytes

- **WHEN** Magika is unavailable and a multipart upload contains valid UTF-8 text bytes without binary control characters
- **THEN** the service classifies the file as text.

### Requirement: Synchronous preview conversion

The preview service SHALL provide a synchronous preview conversion endpoint for the first implementation stage.

#### Scenario: Browser-previewable file is returned as passthrough

- **WHEN** a client sends `POST /v1/preview:convert` with a browser-previewable file
- **THEN** the response identifies the strategy as `passthrough` and includes a base64 encoded preview payload.

#### Scenario: Unsupported file is rejected explicitly

- **WHEN** a client sends `POST /v1/preview:convert` with a recognized but unsupported file type
- **THEN** the service returns HTTP 415 with a JSON error that includes the detected format.

#### Scenario: Oversized upload is rejected

- **WHEN** a client sends `POST /v1/preview:convert` with a file larger than the configured maximum upload size
- **THEN** the service returns HTTP 413 without attempting conversion.

### Requirement: Root command integration

The repository SHALL expose repeatable preview service commands through the root `Makefile`.

#### Scenario: Preview tests can be run from root

- **WHEN** a developer runs `task preview-test`
- **THEN** the command runs Go tests for the `preview/` module.

#### Scenario: Preview binary can be built from root

- **WHEN** a developer runs `task preview-build`
- **THEN** the command builds the preview service binary from `preview/cmd/preview-service`.
