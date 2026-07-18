package detect

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestMagikaDetectorUsesJSONOutput(t *testing.T) {
	command := writeFakeCommand(t, `#!/bin/sh
cat <<'JSON'
[
  {
    "path": "/tmp/sample",
    "result": {
      "status": "ok",
      "value": {
        "output": {
          "description": "Microsoft Word 2007+ document",
          "extensions": ["docx"],
          "group": "document",
          "is_text": false,
          "label": "docx",
          "mime_type": "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        },
        "score": 0.99
      }
    }
  }
]
JSON
`)

	detector := NewMagikaDetector(command, time.Second)
	format, err := detector.Detect(context.Background(), "report.docx", "application/octet-stream", []byte("not inspected by fake command"))

	if err != nil {
		t.Fatalf("Detect returned error: %v", err)
	}
	if format.ID != "docx" {
		t.Fatalf("format.ID = %q, want docx", format.ID)
	}
	if format.MediaType != "application/vnd.openxmlformats-officedocument.wordprocessingml.document" {
		t.Fatalf("format.MediaType = %q, want docx MIME type", format.MediaType)
	}
	if format.Group != "document" {
		t.Fatalf("format.Group = %q, want document", format.Group)
	}
	if format.Detector != "magika" {
		t.Fatalf("format.Detector = %q, want magika", format.Detector)
	}
	if format.Score == nil || *format.Score != 0.99 {
		t.Fatalf("format.Score = %v, want 0.99", format.Score)
	}
}

func TestDetectorFallsBackWhenMagikaIsUnavailable(t *testing.T) {
	detector := NewDetector(NewMagikaDetector(filepath.Join(t.TempDir(), "missing-magika"), time.Second))
	format, err := detector.Detect(context.Background(), "unknown.bin", "application/octet-stream", []byte("%PDF-1.7\n..."))

	if err != nil {
		t.Fatalf("Detect returned error: %v", err)
	}

	if format.ID != "pdf" {
		t.Fatalf("format.ID = %q, want pdf", format.ID)
	}
	if format.MediaType != "application/pdf" {
		t.Fatalf("format.MediaType = %q, want application/pdf", format.MediaType)
	}
	if !format.BrowserPreviewable {
		t.Fatal("PDF should be browser-previewable")
	}
	if format.Detector != "fallback" {
		t.Fatalf("format.Detector = %q, want fallback", format.Detector)
	}
}

func TestFallbackDetectsUTF8Text(t *testing.T) {
	format := FallbackDetect("notes.txt", "application/octet-stream", []byte("档案预览\nhello"))

	if format.ID != "text" {
		t.Fatalf("format.ID = %q, want text", format.ID)
	}
	if format.MediaType != "text/plain; charset=utf-8" {
		t.Fatalf("format.MediaType = %q, want text/plain; charset=utf-8", format.MediaType)
	}
}

func TestFallbackDetectsOfficeFromExtension(t *testing.T) {
	format := FallbackDetect("report.docx", "application/octet-stream", []byte("PK\x03\x04..."))

	if format.ID != "office-docx" {
		t.Fatalf("format.ID = %q, want office-docx", format.ID)
	}
	if format.Group != "office" {
		t.Fatalf("format.Group = %q, want office", format.Group)
	}
	if format.BrowserPreviewable {
		t.Fatal("Office files should not be browser-previewable in the first version")
	}
}

func TestFallbackDetectsUnknownBinary(t *testing.T) {
	format := FallbackDetect("blob.bin", "application/octet-stream", []byte{0x00, 0x01, 0x02, 0xff})

	if format.ID != "unknown" {
		t.Fatalf("format.ID = %q, want unknown", format.ID)
	}
}

func writeFakeCommand(t *testing.T, content string) string {
	t.Helper()

	path := filepath.Join(t.TempDir(), "magika")
	if err := os.WriteFile(path, []byte(content), 0o755); err != nil {
		t.Fatalf("write fake command: %v", err)
	}
	return path
}
