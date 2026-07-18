package preview

import (
	"context"
	"errors"
	"testing"

	"github.com/luckygc/archive-management/backend/preview-service/internal/detect"
)

func TestConvertPassthroughForBrowserPreviewableFile(t *testing.T) {
	service := NewService(staticDetector{format: detect.Format{
		ID:                 "pdf",
		Description:        "PDF document",
		Group:              "document",
		MediaType:          "application/pdf",
		BrowserPreviewable: true,
		Detector:           "test",
	}})

	result, err := service.Convert(context.Background(), Input{
		Filename:    "sample.pdf",
		ContentType: "application/pdf",
		Data:        []byte("%PDF-1.7"),
	})

	if err != nil {
		t.Fatalf("Convert returned error: %v", err)
	}
	if result.Strategy != "passthrough" {
		t.Fatalf("result.Strategy = %q, want passthrough", result.Strategy)
	}
	if string(result.Content) != "%PDF-1.7" {
		t.Fatalf("result.Content = %q, want original content", string(result.Content))
	}
	if result.OutputMediaType != "application/pdf" {
		t.Fatalf("result.OutputMediaType = %q, want application/pdf", result.OutputMediaType)
	}
}

func TestConvertTextFile(t *testing.T) {
	service := NewService(staticDetector{format: detect.Format{
		ID:        "text",
		Group:     "text",
		MediaType: "text/plain; charset=utf-8",
		IsText:    true,
		Detector:  "test",
	}})

	result, err := service.Convert(context.Background(), Input{
		Filename: "notes.txt",
		Data:     []byte("hello"),
	})

	if err != nil {
		t.Fatalf("Convert returned error: %v", err)
	}
	if result.Strategy != "text" {
		t.Fatalf("result.Strategy = %q, want text", result.Strategy)
	}
	if result.OutputFilename != "notes.txt" {
		t.Fatalf("result.OutputFilename = %q, want notes.txt", result.OutputFilename)
	}
}

func TestConvertUnsupportedRecognizedFile(t *testing.T) {
	service := NewService(staticDetector{format: detect.Format{
		ID:        "docx",
		Group:     "document",
		MediaType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
		Detector:  "test",
	}})

	_, err := service.Convert(context.Background(), Input{
		Filename: "report.docx",
		Data:     []byte("docx"),
	})

	var unsupported *UnsupportedFormatError
	if !errors.As(err, &unsupported) {
		t.Fatalf("Convert error = %v, want UnsupportedFormatError", err)
	}
	if unsupported.Format.ID != "docx" {
		t.Fatalf("unsupported.Format.ID = %q, want docx", unsupported.Format.ID)
	}
}

type staticDetector struct {
	format detect.Format
	err    error
}

func (detector staticDetector) Detect(context.Context, string, string, []byte) (detect.Format, error) {
	return detector.format, detector.err
}
