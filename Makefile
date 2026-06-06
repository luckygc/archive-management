SERVER_DIR := server
MVNW := ./mvnw

.PHONY: help server-java server-deps server-compile server-test server-package server-run server-init

help:
	@echo "可用命令:"
	@echo "  make server-java     打印 Java/Maven 版本"
	@echo "  make server-deps     下载 Maven 依赖"
	@echo "  make server-compile  编译 server"
	@echo "  make server-test     测试 server"
	@echo "  make server-package  打包 server"
	@echo "  make server-run      启动 server"
	@echo "  make server-init     初始化 server 运行目录"

server-java:
	cd $(SERVER_DIR) && java -version
	cd $(SERVER_DIR) && $(MVNW) -version

server-deps:
	cd $(SERVER_DIR) && $(MVNW) -q dependency:resolve

server-compile:
	cd $(SERVER_DIR) && $(MVNW) compile

server-test:
	cd $(SERVER_DIR) && $(MVNW) test

server-package:
	cd $(SERVER_DIR) && $(MVNW) package

server-run:
	cd $(SERVER_DIR) && $(MVNW) spring-boot:run

server-init:
	cd $(SERVER_DIR) && $(MVNW) -q spring-boot:run -Dspring-boot.run.arguments=--init
