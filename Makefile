SERVER_DIR := server
PREVIEW_DIR := preview
MVN := mvn
PNPM := pnpm
GO := go

.PHONY: help \
	frontend-install frontend-check frontend-check-fix frontend-test frontend-build frontend-ready \
	web-check web-test web-build web-dev \
	mobile-check mobile-test mobile-build mobile-dev \
	frontend-core-check frontend-core-test frontend-core-build \
	server-java server-deps server-format-check server-format server-rewrite-dry-run server-rewrite-run \
	server-compile server-test server-package server-run \
	preview-test preview-build preview-run

help:
	@echo "可用命令:"
	@echo "  make frontend-install       安装前端依赖"
	@echo "  make frontend-check         检查全部前端包"
	@echo "  make frontend-check-fix     自动修复全部前端包 lint 问题"
	@echo "  make frontend-test          测试全部前端包"
	@echo "  make frontend-build         构建全部前端包"
	@echo "  make frontend-ready         前端检查、测试并构建"
	@echo "  make web-check              检查 PC 前端"
	@echo "  make web-test               测试 PC 前端"
	@echo "  make web-build              构建 PC 前端"
	@echo "  make web-dev                启动 PC 前端开发服务"
	@echo "  make mobile-check           检查移动端前端"
	@echo "  make mobile-test            测试移动端前端"
	@echo "  make mobile-build           构建移动端前端"
	@echo "  make mobile-dev             启动移动端前端开发服务"
	@echo "  make frontend-core-check    检查前端共享包"
	@echo "  make frontend-core-test     测试前端共享包"
	@echo "  make frontend-core-build    构建前端共享包"
	@echo "  make server-java            打印 Java/Maven 版本"
	@echo "  make server-deps            下载 Maven 依赖"
	@echo "  make server-format-check    检查 server 格式"
	@echo "  make server-format          自动格式化 server"
	@echo "  make server-rewrite-dry-run 预览 server OpenRewrite 迁移"
	@echo "  make server-rewrite-run     执行 server OpenRewrite 迁移"
	@echo "  make server-compile         编译 server"
	@echo "  make server-test            测试 server"
	@echo "  make server-package         打包 server"
	@echo "  make server-run             启动 server"
	@echo "  make preview-test           测试文件预览服务"
	@echo "  make preview-build          构建文件预览服务"
	@echo "  make preview-run            启动文件预览服务"

frontend-install:
	$(PNPM) exec vp install

frontend-check:
	$(PNPM) check

frontend-check-fix:
	$(PNPM) check:fix

frontend-test:
	$(PNPM) test

frontend-build:
	$(PNPM) build

frontend-ready:
	$(PNPM) ready

web-check:
	$(PNPM) -F @archive-management/web check

web-test:
	$(PNPM) -F @archive-management/web test

web-build:
	$(PNPM) -F @archive-management/web build

web-dev:
	$(PNPM) run dev:web

mobile-check:
	$(PNPM) -F @archive-management/mobile check

mobile-test:
	$(PNPM) -F @archive-management/mobile test

mobile-build:
	$(PNPM) -F @archive-management/mobile build

mobile-dev:
	$(PNPM) run dev:mobile

frontend-core-check:
	$(PNPM) -F @archive-management/frontend-core check

frontend-core-test:
	$(PNPM) -F @archive-management/frontend-core test

frontend-core-build:
	$(PNPM) -F @archive-management/frontend-core build

server-java:
	cd $(SERVER_DIR) && java -version
	cd $(SERVER_DIR) && $(MVN) -version

server-deps:
	cd $(SERVER_DIR) && $(MVN) -q dependency:resolve

server-format-check:
	cd $(SERVER_DIR) && $(MVN) spotless:check

server-format:
	cd $(SERVER_DIR) && $(MVN) spotless:apply

server-rewrite-dry-run:
	cd $(SERVER_DIR) && $(MVN) rewrite:dryRun

server-rewrite-run:
	cd $(SERVER_DIR) && $(MVN) rewrite:run

server-compile:
	cd $(SERVER_DIR) && $(MVN) compile

server-test:
	cd $(SERVER_DIR) && $(MVN) test

server-package:
	cd $(SERVER_DIR) && $(MVN) package

server-run:
	cd $(SERVER_DIR) && $(MVN) spring-boot:run

preview-test:
	cd $(PREVIEW_DIR) && $(GO) test ./...

preview-build:
	cd $(PREVIEW_DIR) && mkdir -p build && $(GO) build -o build/preview-service ./cmd/preview-service

preview-run:
	cd $(PREVIEW_DIR) && $(GO) run ./cmd/preview-service
