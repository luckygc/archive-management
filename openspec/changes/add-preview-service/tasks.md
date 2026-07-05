## 1. Go 工程骨架

- [x] 1.1 新增 `preview/` Go module 和服务入口
- [x] 1.2 新增预览服务配置和 README
- [x] 1.3 新增预览服务 Dockerfile

## 2. 检测和预览核心

- [x] 2.1 实现 Magika 优先、fallback 兜底的文件类型检测
- [x] 2.2 实现预览策略和转换服务
- [x] 2.3 覆盖检测和转换核心测试

## 3. HTTP API

- [x] 3.1 实现健康检查和能力查询接口
- [x] 3.2 实现同步预览转换接口
- [x] 3.3 覆盖 HTTP handler 测试

## 4. 仓库入口和验证

- [x] 4.1 在根 `Makefile` 增加预览服务命令
- [x] 4.2 运行 `openspec validate add-preview-service --strict`
- [x] 4.3 运行预览服务 Go 测试和构建
