package api

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"os/exec"
	"time"

	"github.com/luckygc/archive-management/preview/internal/config"
	"github.com/luckygc/archive-management/preview/internal/detect"
	"github.com/luckygc/archive-management/preview/internal/preview"
)

type Server struct {
	cfg            config.Config
	logger         *slog.Logger
	previewService preview.Service
}

func NewServer(cfg config.Config, logger *slog.Logger) http.Handler {
	if logger == nil {
		logger = slog.New(slog.NewTextHandler(io.Discard, nil))
	}

	detector := detect.NewDetector(detect.NewMagikaDetector(cfg.MagikaCommand, cfg.MagikaTimeout))
	return Server{
		cfg:            cfg,
		logger:         logger,
		previewService: preview.NewService(detector),
	}
}

func (server Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	switch {
	case r.Method == http.MethodGet && r.URL.Path == "/healthz":
		server.handleHealthz(w, r)
	case r.Method == http.MethodGet && r.URL.Path == "/v1/capabilities":
		server.handleCapabilities(w, r)
	case r.Method == http.MethodPost && r.URL.Path == "/v1/preview:convert":
		server.handleConvert(w, r)
	default:
		writeError(w, http.StatusNotFound, "not_found", "接口不存在", nil)
	}
}

func (server Server) handleHealthz(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"status": "ok",
		"time":   time.Now().UTC().Format(time.RFC3339),
	})
}

func (server Server) handleCapabilities(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"detectors": []map[string]any{
			{
				"id":        "magika",
				"required":  false,
				"command":   server.cfg.MagikaCommand,
				"timeout":   server.cfg.MagikaTimeout.String(),
				"available": magikaAvailable(server.cfg.MagikaCommand),
			},
			{
				"id":       "fallback",
				"required": true,
			},
		},
		"strategies": []string{"passthrough", "text"},
		"formatGroups": []map[string]any{
			{"id": "document", "status": "partial"},
			{"id": "image", "status": "passthrough"},
			{"id": "audio", "status": "passthrough"},
			{"id": "video", "status": "passthrough"},
			{"id": "text", "status": "text"},
			{"id": "code", "status": "text"},
			{"id": "office", "status": "unsupported"},
			{"id": "archive", "status": "unsupported"},
			{"id": "cad", "status": "unsupported"},
			{"id": "dicom", "status": "unsupported"},
		},
	})
}

func (server Server) handleConvert(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, server.cfg.MaxUploadBytes)
	if err := r.ParseMultipartForm(server.cfg.MaxUploadBytes); err != nil {
		if isTooLargeError(err) {
			writeError(w, http.StatusRequestEntityTooLarge, "upload_too_large", "上传文件超过大小限制", nil)
			return
		}
		writeError(w, http.StatusBadRequest, "bad_multipart", "请求必须使用 multipart/form-data 并包含 file 字段", nil)
		return
	}

	file, header, err := r.FormFile("file")
	if err != nil {
		writeError(w, http.StatusBadRequest, "missing_file", "请求必须包含 file 字段", nil)
		return
	}
	defer file.Close()

	data, err := io.ReadAll(io.LimitReader(file, server.cfg.MaxUploadBytes+1))
	if err != nil {
		writeError(w, http.StatusBadRequest, "read_file_failed", "读取上传文件失败", nil)
		return
	}
	if int64(len(data)) > server.cfg.MaxUploadBytes {
		writeError(w, http.StatusRequestEntityTooLarge, "upload_too_large", "上传文件超过大小限制", nil)
		return
	}

	contentType := header.Header.Get("Content-Type")
	result, err := server.previewService.Convert(r.Context(), preview.Input{
		Filename:    header.Filename,
		ContentType: contentType,
		Data:        data,
	})
	if err != nil {
		var unsupported *preview.UnsupportedFormatError
		if errors.As(err, &unsupported) {
			writeError(w, http.StatusUnsupportedMediaType, "unsupported_format", "当前预览服务尚不支持该文件格式", map[string]any{
				"formatId":  unsupported.Format.ID,
				"format":    unsupported.Format,
				"mediaType": unsupported.Format.MediaType,
			})
			return
		}
		server.logger.Error("preview conversion failed", "error", err)
		writeError(w, http.StatusInternalServerError, "conversion_failed", "预览转换失败", nil)
		return
	}

	writeJSON(w, http.StatusOK, convertResponse{
		Strategy:        result.Strategy,
		InputFormat:     result.InputFormat,
		OutputMediaType: result.OutputMediaType,
		OutputFilename:  result.OutputFilename,
		ContentBase64:   base64.StdEncoding.EncodeToString(result.Content),
		Size:            len(result.Content),
	})
}

type convertResponse struct {
	Strategy        string        `json:"strategy"`
	InputFormat     detect.Format `json:"inputFormat"`
	OutputMediaType string        `json:"outputMediaType"`
	OutputFilename  string        `json:"outputFilename"`
	ContentBase64   string        `json:"contentBase64"`
	Size            int           `json:"size"`
}

type errorResponse struct {
	Code    string         `json:"code"`
	Message string         `json:"message"`
	Details map[string]any `json:"details,omitempty"`
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status int, code string, message string, details map[string]any) {
	writeJSON(w, status, errorResponse{
		Code:    code,
		Message: message,
		Details: details,
	})
}

func isTooLargeError(err error) bool {
	var maxBytesError *http.MaxBytesError
	return errors.As(err, &maxBytesError) ||
		errors.Is(err, http.ErrBodyReadAfterClose) ||
		errors.Is(err, io.ErrUnexpectedEOF) ||
		err.Error() == "http: request body too large"
}

func magikaAvailable(command string) bool {
	if command == "" {
		command = "magika"
	}
	_, err := exec.LookPath(command)
	return err == nil
}
