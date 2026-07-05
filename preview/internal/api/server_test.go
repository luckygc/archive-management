package api

import (
	"bytes"
	"encoding/json"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"net/textproto"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/luckygc/archive-management/preview/internal/config"
)

func TestHealthz(t *testing.T) {
	server := NewServer(testConfig(t), nil)
	request := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	response := httptest.NewRecorder()

	server.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("response.Code = %d, want 200", response.Code)
	}
	if !strings.Contains(response.Body.String(), `"status":"ok"`) {
		t.Fatalf("response body = %s, want ok status", response.Body.String())
	}
}

func TestCapabilities(t *testing.T) {
	server := NewServer(testConfig(t), nil)
	request := httptest.NewRequest(http.MethodGet, "/v1/capabilities", nil)
	response := httptest.NewRecorder()

	server.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("response.Code = %d, want 200", response.Code)
	}
	if !strings.Contains(response.Body.String(), `"magika"`) {
		t.Fatalf("response body = %s, want magika capability", response.Body.String())
	}
}

func TestConvertPDFPassthrough(t *testing.T) {
	server := NewServer(testConfig(t), nil)
	body, contentType := multipartBody(t, "sample.pdf", "application/octet-stream", []byte("%PDF-1.7\nbody"))
	request := httptest.NewRequest(http.MethodPost, "/v1/preview:convert", body)
	request.Header.Set("Content-Type", contentType)
	response := httptest.NewRecorder()

	server.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("response.Code = %d, body = %s", response.Code, response.Body.String())
	}

	var payload convertResponse
	if err := json.Unmarshal(response.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if payload.Strategy != "passthrough" {
		t.Fatalf("payload.Strategy = %q, want passthrough", payload.Strategy)
	}
	if payload.InputFormat.ID != "pdf" {
		t.Fatalf("payload.InputFormat.ID = %q, want pdf", payload.InputFormat.ID)
	}
	if payload.ContentBase64 == "" {
		t.Fatal("payload.ContentBase64 should not be empty")
	}
}

func TestConvertUnsupportedOfficeFile(t *testing.T) {
	server := NewServer(testConfig(t), nil)
	body, contentType := multipartBody(t, "report.docx", "application/octet-stream", []byte("PK\x03\x04binary"))
	request := httptest.NewRequest(http.MethodPost, "/v1/preview:convert", body)
	request.Header.Set("Content-Type", contentType)
	response := httptest.NewRecorder()

	server.ServeHTTP(response, request)

	if response.Code != http.StatusUnsupportedMediaType {
		t.Fatalf("response.Code = %d, body = %s", response.Code, response.Body.String())
	}
	if !strings.Contains(response.Body.String(), `"formatId":"office-docx"`) {
		t.Fatalf("response body = %s, want formatId office-docx", response.Body.String())
	}
}

func TestConvertRejectsOversizedUpload(t *testing.T) {
	cfg := testConfig(t)
	cfg.MaxUploadBytes = 4
	server := NewServer(cfg, nil)
	body, contentType := multipartBody(t, "sample.pdf", "application/pdf", []byte("%PDF-1.7"))
	request := httptest.NewRequest(http.MethodPost, "/v1/preview:convert", body)
	request.Header.Set("Content-Type", contentType)
	response := httptest.NewRecorder()

	server.ServeHTTP(response, request)

	if response.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("response.Code = %d, body = %s", response.Code, response.Body.String())
	}
}

func multipartBody(t *testing.T, filename string, contentType string, content []byte) (*bytes.Buffer, string) {
	t.Helper()

	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)
	header := make(textproto.MIMEHeader)
	header.Set("Content-Disposition", `form-data; name="file"; filename="`+filename+`"`)
	header.Set("Content-Type", contentType)
	part, err := writer.CreatePart(header)
	if err != nil {
		t.Fatalf("create multipart part: %v", err)
	}
	if _, err := part.Write(content); err != nil {
		t.Fatalf("write multipart part: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close multipart writer: %v", err)
	}
	return body, writer.FormDataContentType()
}

func testConfig(t *testing.T) config.Config {
	t.Helper()

	return config.Config{
		Addr:              ":0",
		MaxUploadBytes:    1 << 20,
		MagikaCommand:     filepath.Join(t.TempDir(), "missing-magika"),
		MagikaTimeout:     time.Millisecond,
		ReadHeaderTimeout: time.Second,
		ReadTimeout:       time.Second,
		WriteTimeout:      time.Second,
		IdleTimeout:       time.Second,
	}
}
