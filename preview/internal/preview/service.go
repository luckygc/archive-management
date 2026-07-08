package preview

import (
	"context"
	"fmt"

	"github.com/luckygc/archive-management/preview/internal/detect"
)

type Input struct {
	Filename    string
	ContentType string
	Data        []byte
}

type Result struct {
	Strategy        string        `json:"strategy"`
	InputFormat     detect.Format `json:"inputFormat"`
	OutputMediaType string        `json:"outputMediaType"`
	OutputFilename  string        `json:"outputFilename"`
	Content         []byte        `json:"-"`
}

type Service struct {
	detector detect.Detector
}

func NewService(detector detect.Detector) Service {
	return Service{detector: detector}
}

func (service Service) Convert(ctx context.Context, input Input) (Result, error) {
	format, err := service.detector.Detect(ctx, input.Filename, input.ContentType, input.Data)
	if err != nil {
		return Result{}, fmt.Errorf("识别文件类型失败: %w", err)
	}

	if format.BrowserPreviewable {
		return Result{
			Strategy:        "passthrough",
			InputFormat:     format,
			OutputMediaType: format.MediaType,
			OutputFilename:  input.Filename,
			Content:         input.Data,
		}, nil
	}

	if format.IsText || format.Group == "text" || format.Group == "code" {
		return Result{
			Strategy:        "text",
			InputFormat:     format,
			OutputMediaType: "text/plain; charset=utf-8",
			OutputFilename:  input.Filename,
			Content:         input.Data,
		}, nil
	}

	return Result{}, &UnsupportedFormatError{Format: format}
}

type UnsupportedFormatError struct {
	Format detect.Format
}

func (err *UnsupportedFormatError) Error() string {
	return "当前预览服务尚不支持该文件格式: " + err.Format.ID
}
