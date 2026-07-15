## 1. 存储合同和持久化模型

- [x] 1.1 删除存储类型、后端委派和本地目录实现，收敛 `FileStorageService` 及相关值对象
- [x] 1.2 调整 `StorageObject`、Service 和 Flyway 目标结构，删除 `storage_type`
- [x] 1.3 更新存储服务与业务服务单元测试

## 2. 配置和开发环境

- [x] 2.1 扁平化 `archive.storage.*` S3 兼容配置并更新默认开发值
- [x] 2.2 在开发 Compose 增加单节点 S3 端点
- [x] 2.3 更新开发、部署、架构和运维文档
- [x] 2.4 将原型阶段的开发数据库和对象存储改为无持久化临时存储
- [x] 2.5 删除模拟部署 Compose，将唯一 Compose 收敛为 PostgreSQL 和 RustFS 两个服务
- [x] 2.6 增加本地基础设施启停任务，并通过签名 S3 请求幂等创建开发 bucket

## 3. 规格和验证

- [x] 3.1 将统一 S3 合同同步到主 `file-storage` 规格
- [x] 3.2 运行 Java 格式检查、编译和相关测试
- [x] 3.3 复核不存在本地存储、adapter 或供应商存储类型残留
