# 测试 JVM 退出超时定位

## 结论与范围

2026-10-09 按“提交，定位有问题的地方”要求完成只读排查和单类复现。问题位于 `server/src/test/java/github/luckygc/am/module/authentication/RequiredTotpLoginIntegrationTests.java`：该类是当前继承 `PostgreSqlContainerTest` 的数据库测试中唯一缺少 `@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)` 的类，共同基类也未声明该标记。

Testcontainers 在测试类结束后停止 PostgreSQL 容器，Spring 却将该类的应用上下文缓存到 JVM 退出。关闭上下文时，Spring Modulith 事件登记组件和 Flowable 流程引擎仍需要访问数据库，出现连接断开或等待连接。Surefire 在 `System.exit(0)` 后等待 30 秒，最终强制结束测试 JVM。

这是测试生命周期问题。测试断言及 Maven 退出码均通过，不能据此声称测试进程正常退出；也没有证据表明这是生产关闭故障。本次保留既有测试代码，只定位和记录，不实施修复。

## 复现与证据

- 完整验证使用 `mise.exe run verify`，779 项后端测试零失败、零错误、零跳过，仍产生退出超时；此前 746 项基线和本轮另一次 778 项完整验证都有相同提示。
- 单类复现使用真实 Maven 测试入口：`mise.exe exec -- mvn -f server/pom.xml -Dtest=RequiredTotpLoginIntegrationTests test`。1 项测试通过，退出码 0，仍复现退出后 30 秒强制终止。日志保存于执行机器临时目录 `archive-shutdown-required-totp.log`，不纳入 Git。
- 完整运行的 `server/target/surefire-reports/2026-10-09T18-36-16_648-jvmRun1.dump` 第 3–18 行显示主线程在 `System.exit` 的关闭钩子中等待。第 457–491 行显示 `SpringApplicationShutdownHook` 阻塞在 `DefaultEventPublicationRegistry.destroy → findIncompletePublications → DataSourceTransactionManager.doBegin → HikariPool.getConnection → ConcurrentBag.borrow`。
- 单类日志先记录 `eventPublicationRegistry` 销毁查询的 PostgreSQL I/O 错误及回滚失败；随后提示池中数据库连接已关闭。单类运行的 `2026-10-09T19-02-22_168-jvmRun1.dump` 第 341–379 行显示同一关闭钩子此时阻塞于 `ProcessEngineFactoryBean.destroy → ProcessEngineImpl.close → SpringTransactionInterceptor → HikariPool.getConnection → ConcurrentBag.borrow`。两次采样停在不同 Bean，共同指向关闭上下文时数据库已不可用。
- 两份 dumpstream 均记录 Surefire 在 `System.exit(0)` 后 30 秒强制结束测试 JVM。完整转储另保存于执行机器临时目录 `archive-shutdown-full.dump`；目标目录内原始转储可能被后续 clean 删除。

## 生命周期与实际版本核对

从本轮 Surefire 报告的实际 classpath 确认：Spring Boot 4.1.0、Spring Test 7.0.8、Spring Modulith 2.1.0、HikariCP 7.0.2、Testcontainers JUnit Jupiter 2.0.5。核对实际版本源码，而非本机其他已安装版本：

1. Testcontainers `afterAll()` 发出类结束信号；注册于测试类 ExtensionContext Store 的 `StoreAdapter.close()` 调用 `container.stop()`。
2. Spring `SpringExtension.afterAll()` 调用 `TestContextManager.afterTestClass()`；有 AFTER_CLASS 标记时，`DirtiesContextTestExecutionListener` 清理缓存并同步关闭应用上下文。在测试类 Store 清理前关闭上下文，数据库仍可用于 Bean 销毁。
3. Modulith `DefaultEventPublicationRegistry.destroy()` 第 268 行无条件读取未完成事件，不会因日志级别或启动时重发布开关而跳过该查询。
4. Boot 关闭钩子同步执行 `context.close()`。Hikari 等待连接使用 `server/src/main/resources/application.yaml` 中的 30000 毫秒连接超时；这与 Surefire 的 30 秒退出期限相撞。

## 最小修正方向与后续验收

为 `RequiredTotpLoginIntegrationTests` 补齐其他 PostgreSQL 集成测试已有的 AFTER_CLASS 上下文清理标记，使依赖数据库的 Bean 在容器停止前正常销毁。不修改认证业务、数据库连接等待时间、Surefire 退出期限或事件登记机制。

实施后应先再次执行该单类测试，确认测试通过且连接池关闭完成、无数据库销毁错误或 JVM 强制终止；随后重新执行完整 `mise.exe run verify`，确认全部测试报告齐全、零跳过且整个测试进程正常退出。本次尚未实施这项修复，因此不宣称退出超时已解决。
