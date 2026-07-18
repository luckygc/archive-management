FROM golang:1.22-bookworm AS build

WORKDIR /src
COPY go.mod ./
COPY cmd ./cmd
COPY internal ./internal
RUN go build -trimpath -ldflags="-s -w" -o /out/preview-service ./cmd/preview-service

FROM debian:bookworm-slim

RUN useradd --create-home --shell /usr/sbin/nologin preview
COPY --from=build /out/preview-service /usr/local/bin/preview-service

USER preview
EXPOSE 8088
ENTRYPOINT ["/usr/local/bin/preview-service"]
