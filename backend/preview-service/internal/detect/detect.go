package detect

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"mime"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
	"unicode/utf8"
)

type Format struct {
	ID                 string   `json:"id"`
	Description        string   `json:"description"`
	Group              string   `json:"group"`
	MediaType          string   `json:"mediaType"`
	Extensions         []string `json:"extensions,omitempty"`
	IsText             bool     `json:"isText"`
	BrowserPreviewable bool     `json:"browserPreviewable"`
	Detector           string   `json:"detector"`
	Score              *float64 `json:"score,omitempty"`
}

type Detector interface {
	Detect(ctx context.Context, filename string, declaredContentType string, data []byte) (Format, error)
}

type CompositeDetector struct {
	primary Detector
}

func NewDetector(primary Detector) CompositeDetector {
	return CompositeDetector{primary: primary}
}

func (detector CompositeDetector) Detect(ctx context.Context, filename string, declaredContentType string, data []byte) (Format, error) {
	if detector.primary != nil {
		format, err := detector.primary.Detect(ctx, filename, declaredContentType, data)
		if err == nil && format.ID != "" {
			return format, nil
		}
	}
	return FallbackDetect(filename, declaredContentType, data), nil
}

type MagikaDetector struct {
	command string
	timeout time.Duration
}

func NewMagikaDetector(command string, timeout time.Duration) MagikaDetector {
	if command == "" {
		command = "magika"
	}
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	return MagikaDetector{command: command, timeout: timeout}
}

func (detector MagikaDetector) Detect(ctx context.Context, filename string, _ string, data []byte) (Format, error) {
	dir, err := os.MkdirTemp("", "archive-preview-detect-*")
	if err != nil {
		return Format{}, fmt.Errorf("创建临时探测目录失败: %w", err)
	}
	defer os.RemoveAll(dir)

	path := filepath.Join(dir, safeTempName(filename))
	if err := os.WriteFile(path, data, 0o600); err != nil {
		return Format{}, fmt.Errorf("写入临时探测文件失败: %w", err)
	}

	ctx, cancel := context.WithTimeout(ctx, detector.timeout)
	defer cancel()

	cmd := exec.CommandContext(ctx, detector.command, path, "--json")
	output, err := cmd.Output()
	if err != nil {
		return Format{}, err
	}

	format, err := parseMagikaOutput(output)
	if err != nil {
		return Format{}, err
	}
	return format, nil
}

func parseMagikaOutput(output []byte) (Format, error) {
	var results []magikaFileResult
	if err := json.Unmarshal(output, &results); err != nil {
		return Format{}, fmt.Errorf("解析 Magika JSON 失败: %w", err)
	}
	if len(results) == 0 {
		return Format{}, errors.New("Magika 没有返回结果")
	}
	result := results[0].Result
	if result.Status != "ok" {
		return Format{}, fmt.Errorf("Magika 返回非 OK 状态: %s", result.Status)
	}
	value := result.Value.Output
	if value.Label == "" {
		return Format{}, errors.New("Magika 返回空 label")
	}
	format := Format{
		ID:                 value.Label,
		Description:        value.Description,
		Group:              value.Group,
		MediaType:          value.MIMEType,
		Extensions:         value.Extensions,
		IsText:             value.IsText,
		BrowserPreviewable: browserPreviewable(value.Group, value.MIMEType, value.IsText),
		Detector:           "magika",
		Score:              result.Value.Score,
	}
	if format.MediaType == "" {
		format.MediaType = "application/octet-stream"
	}
	return format, nil
}

func FallbackDetect(filename string, declaredContentType string, data []byte) Format {
	mediaType := cleanMediaType(declaredContentType)
	extension := strings.TrimPrefix(strings.ToLower(filepath.Ext(filename)), ".")

	if bytes.HasPrefix(data, []byte("%PDF-")) {
		return Format{
			ID:                 "pdf",
			Description:        "PDF document",
			Group:              "document",
			MediaType:          "application/pdf",
			Extensions:         []string{"pdf"},
			IsText:             false,
			BrowserPreviewable: true,
			Detector:           "fallback",
		}
	}

	if isUTF8Text(data) {
		return Format{
			ID:          "text",
			Description: "Plain text",
			Group:       "text",
			MediaType:   "text/plain; charset=utf-8",
			Extensions:  extensionList(extension),
			IsText:      true,
			Detector:    "fallback",
		}
	}

	if known, ok := byExtension(extension); ok {
		known.Detector = "fallback"
		return known
	}

	if mediaType == "" || mediaType == "application/octet-stream" {
		mediaType = http.DetectContentType(data)
	}
	if format, ok := byMediaType(mediaType); ok {
		format.Detector = "fallback"
		return format
	}

	return Format{
		ID:          "unknown",
		Description: "Unknown binary data",
		Group:       "unknown",
		MediaType:   "application/octet-stream",
		Extensions:  extensionList(extension),
		Detector:    "fallback",
	}
}

func byExtension(extension string) (Format, bool) {
	switch extension {
	case "pdf":
		return format("pdf", "PDF document", "document", "application/pdf", []string{"pdf"}, false, true), true
	case "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg":
		mediaType := mime.TypeByExtension("." + extension)
		if mediaType == "" {
			mediaType = "image/" + extension
		}
		return format(extension, strings.ToUpper(extension)+" image", "image", mediaType, []string{extension}, false, true), true
	case "mp3", "wav", "ogg", "flac", "aac":
		mediaType := mime.TypeByExtension("." + extension)
		if mediaType == "" {
			mediaType = "audio/" + extension
		}
		return format(extension, strings.ToUpper(extension)+" audio", "audio", mediaType, []string{extension}, false, true), true
	case "mp4", "webm", "mov", "m4v":
		mediaType := mime.TypeByExtension("." + extension)
		if mediaType == "" {
			mediaType = "video/" + extension
		}
		return format(extension, strings.ToUpper(extension)+" video", "video", mediaType, []string{extension}, false, true), true
	case "txt", "csv", "log", "md", "json", "xml", "html", "htm":
		mediaType := mime.TypeByExtension("." + extension)
		if mediaType == "" {
			mediaType = "text/plain; charset=utf-8"
		}
		return format(extension, strings.ToUpper(extension)+" text", "text", mediaType, []string{extension}, true, false), true
	case "doc":
		return format("office-doc", "Microsoft Word document", "office", "application/msword", []string{"doc"}, false, false), true
	case "docx":
		return format("office-docx", "Microsoft Word 2007+ document", "office", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", []string{"docx"}, false, false), true
	case "xls":
		return format("office-xls", "Microsoft Excel document", "office", "application/vnd.ms-excel", []string{"xls"}, false, false), true
	case "xlsx":
		return format("office-xlsx", "Microsoft Excel 2007+ document", "office", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", []string{"xlsx"}, false, false), true
	case "ppt":
		return format("office-ppt", "Microsoft PowerPoint document", "office", "application/vnd.ms-powerpoint", []string{"ppt"}, false, false), true
	case "pptx":
		return format("office-pptx", "Microsoft PowerPoint 2007+ document", "office", "application/vnd.openxmlformats-officedocument.presentationml.presentation", []string{"pptx"}, false, false), true
	case "zip", "rar", "7z":
		return format("archive-"+extension, strings.ToUpper(extension)+" archive", "archive", archiveMediaType(extension), []string{extension}, false, false), true
	}
	return Format{}, false
}

func byMediaType(mediaType string) (Format, bool) {
	baseType := cleanMediaType(mediaType)
	switch {
	case baseType == "application/pdf":
		return format("pdf", "PDF document", "document", "application/pdf", []string{"pdf"}, false, true), true
	case strings.HasPrefix(baseType, "image/"):
		return format(strings.TrimPrefix(baseType, "image/"), "Image", "image", baseType, nil, false, true), true
	case strings.HasPrefix(baseType, "audio/"):
		return format(strings.TrimPrefix(baseType, "audio/"), "Audio", "audio", baseType, nil, false, true), true
	case strings.HasPrefix(baseType, "video/"):
		return format(strings.TrimPrefix(baseType, "video/"), "Video", "video", baseType, nil, false, true), true
	case strings.HasPrefix(baseType, "text/"):
		return format("text", "Text", "text", mediaType, nil, true, false), true
	}
	return Format{}, false
}

func format(id string, description string, group string, mediaType string, extensions []string, isText bool, browserPreviewable bool) Format {
	return Format{
		ID:                 id,
		Description:        description,
		Group:              group,
		MediaType:          mediaType,
		Extensions:         extensions,
		IsText:             isText,
		BrowserPreviewable: browserPreviewable,
	}
}

func cleanMediaType(mediaType string) string {
	value, _, err := mime.ParseMediaType(mediaType)
	if err != nil {
		return strings.TrimSpace(strings.ToLower(mediaType))
	}
	return strings.ToLower(value)
}

func browserPreviewable(group string, mediaType string, isText bool) bool {
	if isText {
		return false
	}
	baseType := cleanMediaType(mediaType)
	return baseType == "application/pdf" ||
		strings.HasPrefix(baseType, "image/") ||
		strings.HasPrefix(baseType, "audio/") ||
		strings.HasPrefix(baseType, "video/") ||
		group == "image" ||
		group == "audio" ||
		group == "video"
}

func isUTF8Text(data []byte) bool {
	if len(data) == 0 {
		return true
	}
	if !utf8.Valid(data) {
		return false
	}
	for _, value := range data {
		if value == 0 {
			return false
		}
		if value < 0x20 && value != '\n' && value != '\r' && value != '\t' {
			return false
		}
	}
	return true
}

func extensionList(extension string) []string {
	if extension == "" {
		return nil
	}
	return []string{extension}
}

func archiveMediaType(extension string) string {
	switch extension {
	case "zip":
		return "application/zip"
	case "rar":
		return "application/vnd.rar"
	case "7z":
		return "application/x-7z-compressed"
	default:
		return "application/octet-stream"
	}
}

func safeTempName(filename string) string {
	name := filepath.Base(filename)
	if name == "." || name == string(filepath.Separator) || name == "" {
		return "upload.bin"
	}
	return name
}

type magikaFileResult struct {
	Result magikaResult `json:"result"`
}

type magikaResult struct {
	Status string      `json:"status"`
	Value  magikaValue `json:"value"`
}

type magikaValue struct {
	Output magikaOutput `json:"output"`
	Score  *float64     `json:"score"`
}

type magikaOutput struct {
	Description string   `json:"description"`
	Extensions  []string `json:"extensions"`
	Group       string   `json:"group"`
	IsText      bool     `json:"is_text"`
	Label       string   `json:"label"`
	MIMEType    string   `json:"mime_type"`
}
