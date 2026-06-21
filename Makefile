SERVER_DIR := server
MVN := mvn
VP := vp

.PHONY: help frontend-install frontend-check frontend-check-fix frontend-lint frontend-lint-fix frontend-format-check frontend-format frontend-test frontend-build frontend-ready frontend-dev server-java server-deps server-format-check server-format server-compile server-test server-package server-run server-init

help:
	@echo "可用命令:"
	@echo "  make frontend-install   	 安装前端依赖"
	@echo "  make frontend-check     	 检查前端格式、lint 和类型"
	@echo "  make frontend-check-fix     自动修复前端格式和 lint 问题"
	@echo "  make frontend-lint          运行前端 lint"
	@echo "  make frontend-lint-fix      自动修复前端 lint 问题"
	@echo "  make frontend-format-check  检查前端格式"
	@echo "  make frontend-format   	 自动格式化前端"
	@echo "  make frontend-test     	 运行前端测试"
	@echo "  make frontend-build    	 构建前端"
	@echo "  make frontend-ready    	 前端检查并构建"
	@echo "  make frontend-dev      	 启动前端开发服务"
	@echo "  make server-java     	     打印 Java/Maven 版本"
	@echo "  make server-deps     		 下载 Maven 依赖"
	@echo "  make server-format-check    检查 server 格式"
	@echo "  make server-format   		 自动格式化 server"
	@echo "  make server-compile  		 编译 server"
	@echo "  make server-test     		 测试 server"
	@echo "  make server-package  		 打包 server"
	@echo "  make server-run      		 启动 server"

frontend-install:
	$(VP) install

frontend-check:
	$(VP) check

frontend-check-fix:
	$(VP) check --fix

frontend-lint:
	$(VP) lint

frontend-lint-fix:
	$(VP) lint --fix

frontend-format-check:
	$(VP) fmt --check

frontend-format:
	$(VP) fmt --write

frontend-test:
	$(VP) test run --passWithNoTests

frontend-build:
	$(VP) build

frontend-ready:
	$(VP) check
	$(VP) test run --passWithNoTests
	$(VP) build

frontend-dev:
	$(VP) dev

server-java:
	cd $(SERVER_DIR) && java -version
	cd $(SERVER_DIR) && $(MVN) -version

server-deps:
	cd $(SERVER_DIR) && $(MVN) -q dependency:resolve

server-format-check:
	cd $(SERVER_DIR) && $(MVN) spotless:check

server-format:
	cd $(SERVER_DIR) && $(MVN) spotless:apply

server-compile:
	cd $(SERVER_DIR) && $(MVN) compile

server-test:
	cd $(SERVER_DIR) && $(MVN) test

server-package:
	cd $(SERVER_DIR) && $(MVN) package

server-run:
	cd $(SERVER_DIR) && $(MVN) spring-boot:run
