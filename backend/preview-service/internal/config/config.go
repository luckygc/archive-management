package config

import (
	"os"
	"strconv"
	"time"
)

const (
	defaultAddr           = ":8088"
	defaultMaxUploadBytes = 50 << 20
)

type Config struct {
	Addr              string
	MaxUploadBytes    int64
	MagikaCommand     string
	MagikaTimeout     time.Duration
	ReadHeaderTimeout time.Duration
	ReadTimeout       time.Duration
	WriteTimeout      time.Duration
	IdleTimeout       time.Duration
}

func FromEnv() Config {
	return Config{
		Addr:              getenv("PREVIEW_ADDR", defaultAddr),
		MaxUploadBytes:    getenvInt64("PREVIEW_MAX_UPLOAD_BYTES", defaultMaxUploadBytes),
		MagikaCommand:     getenv("PREVIEW_MAGIKA_COMMAND", "magika"),
		MagikaTimeout:     getenvDuration("PREVIEW_MAGIKA_TIMEOUT", 5*time.Second),
		ReadHeaderTimeout: getenvDuration("PREVIEW_READ_HEADER_TIMEOUT", 5*time.Second),
		ReadTimeout:       getenvDuration("PREVIEW_READ_TIMEOUT", 30*time.Second),
		WriteTimeout:      getenvDuration("PREVIEW_WRITE_TIMEOUT", 30*time.Second),
		IdleTimeout:       getenvDuration("PREVIEW_IDLE_TIMEOUT", 60*time.Second),
	}
}

func getenv(name, fallback string) string {
	value := os.Getenv(name)
	if value == "" {
		return fallback
	}
	return value
}

func getenvInt64(name string, fallback int64) int64 {
	value := os.Getenv(name)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil || parsed <= 0 {
		return fallback
	}
	return parsed
}

func getenvDuration(name string, fallback time.Duration) time.Duration {
	value := os.Getenv(name)
	if value == "" {
		return fallback
	}
	parsed, err := time.ParseDuration(value)
	if err != nil || parsed <= 0 {
		return fallback
	}
	return parsed
}
