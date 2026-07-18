# 领域文档

本仓库采用 single-context 布局。

## 探索代码前读取

- 根目录 `CONTEXT.md`
- `docs/adr/` 中与当前改动相关的 ADR

文件不存在时静默继续，不要求预先创建。`domain-modeling`、`grill-with-docs` 和 `improve-codebase-architecture` 会在真实术语或决策形成时按需创建。

## 文件布局

```text
/
├── CONTEXT.md
└── docs/
    └── adr/
        ├── 0001-<decision>.md
        └── 0002-<decision>.md
```

## 使用领域词汇

Issue 标题、重构提案、假设、测试名称和代码命名应使用 `CONTEXT.md` 定义的术语，避免使用其明确排除的同义词。

如果需要的概念尚未进入词汇表，应先判断它是错误用词还是领域模型确实存在缺口；真实缺口交由 `domain-modeling` 补充。

## ADR 冲突

若方案与现有 ADR 冲突，必须显式指出冲突及重新讨论的理由，不能静默覆盖已有决策。
