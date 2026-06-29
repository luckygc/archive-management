# Tasks

- [x]   1. 更新 OpenSpec 主规格，固化 item/volume 分对象、item 明细子表和 item-to-item 关联合同。
- [x]   1. 更新 Flyway 迁移和样例数据，将 `archive_record` 相关对象改为 `archive_item`，新增 `archive_volume`、item relation、item line 定义和动态明细样例。
- [x]   1. 更新 Mapper XML 和 Mapper 接口，将条目主路径改为 `am_archive_item`，移除 item 查询中的 `archive_level` 条件，并使用 `volume_id` 表达组卷。
- [x]   1. 新增或调整 volume 服务入口，使用独立 `archive-volumes` API 管理案卷。
- [x]   1. 将 `archive-records` Controller/DTO/Service 命名和 URL 替换为 `archive-items`，DTO 字段使用 `itemId`、`volumeId`。
- [x]   1. 实现 item-to-item 关联的增删查接口，限制自关联、重复关联和嵌套深度。
- [x]   1. 实现 item 明细表定义和动态明细表建表最小闭环，并让 item 全文投影读取明细行文本。
- [x]   1. 运行 OpenSpec、Maven 编译和目标测试，修复回归。
