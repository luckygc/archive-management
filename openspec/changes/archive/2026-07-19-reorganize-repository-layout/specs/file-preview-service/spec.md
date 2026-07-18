## MODIFIED Requirements

### Requirement: 预览服务运行边界

系统 SHALL 在 `backend/preview-service/` 下提供可独立构建和运行的 Go 文件预览服务。

#### Scenario: 检查服务存活状态

- **WHEN** 客户端发送 `GET /healthz`
- **THEN** 服务 SHALL 返回 HTTP 200 和 JSON 状态响应体

#### Scenario: 查询预览能力

- **WHEN** 客户端发送 `GET /v1/capabilities`
- **THEN** 服务 SHALL 返回支持的预览策略和已知文件格式分组

### Requirement: 根任务命令集成

仓库 SHALL 通过根目录 `Taskfile.yml` 提供可重复执行的文件预览服务命令。

#### Scenario: 从仓库根目录运行预览测试

- **WHEN** 开发者运行 `task preview-test`
- **THEN** 该任务 SHALL 执行 `backend/preview-service/` 模块的 Go 测试

#### Scenario: 从仓库根目录构建预览服务

- **WHEN** 开发者运行 `task preview-build`
- **THEN** 该任务 SHALL 从 `backend/preview-service/cmd/preview-service` 构建预览服务二进制文件
