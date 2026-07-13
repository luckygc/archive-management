---
name: Archive Management
description: 面向机构内部档案业务的克制型企业管理系统设计规范
colors:
    primary:
        value: "#1f5eff"
        role: 主操作、选中态、关键状态提示
    primary-element:
        value: "#409eff"
        role: Element Plus 默认主色兼容色
    background:
        value: "#f5f7fb"
        role: PC 工作区背景
    surface:
        value: "#ffffff"
        role: 菜单、顶部栏、表格容器和表单区域
    text:
        value: "#172033"
        role: 主要文本
    text-secondary:
        value: "#596579"
        role: 次要文本、辅助说明
    border:
        value: "#e6eaf2"
        role: 分隔线、容器边界
    muted:
        value: "#edf1f7"
        role: 轻量 hover、页签关闭按钮反馈
typography:
    display:
        family: "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
        weight: 600
    body:
        family: "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
        weight: 400
rounded:
    sm: "6px"
    md: "8px"
spacing:
    page: "16px"
components:
    button:
        radius: "6px"
    card:
        radius: "8px"
    input:
        radius: "6px"
---

# Design System: Archive Management

## 1. Overview

**Creative North Star: "清晰的档案工作台"**

Archive Management 的设计系统服务于长期、高频、可审计的档案业务操作。它以 Element Plus 的企业级产品语言为主线，强调稳定结构、明确状态和可预期反馈，而不是营销展示、装饰叙事或炫技视觉。

PC 端是高密度工作台：侧边菜单、多页签、表格、筛选、动态表单和详情区是主要表达。

系统明确拒绝营销落地页、宣传站、深色炫技后台、大面积渐变、玻璃拟态、装饰卡片、插画堆叠和品牌 hero。组件外观优先交给 Element Plus，不另起一套平行组件库。

**Key Characteristics:**

- 克制、可信、清晰、务实。
- 信息密度服务业务操作，不服务视觉展示。
- 页面首屏直接进入筛选、表格、表单、详情、待办或配置内容。
- Web 入口共享品牌色、字体和登录能力。

## 2. Colors

色彩是低噪声的企业后台调色板：浅灰蓝工作区、白色内容表面、蓝色关键动作、深色正文。

### Primary

- **Archive Blue** (#1f5eff): 用于主按钮、当前选中态、关键数量和需要立即识别的状态。它不应用作大面积背景。
- **Element Blue** (#409eff): 保留给 Element Plus 默认组件状态和图标兼容场景，避免在局部覆盖组件库状态色。

### Neutral

- **Workbench Mist** (#f5f7fb): PC 内容区背景。它负责把工作区和白色控件表面区分开。
- **Panel White** (#ffffff): 菜单、顶部栏、表格容器、表单区域和弹层表面。
- **Ink Text** (#172033): 主要文本和页面对象名。
- **Secondary Slate** (#596579): 次要文本、占位状态和说明性辅助文字。
- **Divider Blue Gray** (#e6eaf2): 侧边栏、顶部栏、页签区和信息块边界。
- **Hover Wash** (#edf1f7): 低强度 hover 和轻量按钮反馈。

### Named Rules

**The Accent Rarity Rule.** 蓝色只用于动作、选中和关键状态；普通容器、标题区和说明块不得为了强调而铺蓝色。

**The Surface Discipline Rule.** 内容区默认使用 `#f5f7fb` 背景和 `#ffffff` 表面；不要用渐变、玻璃拟态或大面积深色制造层次。

## 3. Typography

**Display Font:** Inter with system fallbacks  
**Body Font:** Inter with system fallbacks  
**Label/Mono Font:** System sans-serif unless a component explicitly needs monospace

**Character:** 字体系统应像成熟后台一样稳定、清楚、低情绪。字号层级靠组件语义和信息密度控制，不做展示站式超大标题。

### Hierarchy

- **Display** (600, 24-28px, 1.2): 只用于登录页等少量需要强化身份感的界面。
- **Headline** (600, 22px, 32px): 用于 PC 页面对象名，避免每个页面增加大段说明。
- **Title** (600, 16-18px, 1.4): 用于卡片、表格分组、详情面板和表单分组。
- **Body** (400, 14px, 1.5715): 用于表格、表单、描述列表和业务正文。
- **Label** (500, 12-14px, 0, normal case): 用于字段名、筛选标签、状态辅助信息。

### Named Rules

**The Workbench Type Rule.** 管理界面内不使用 hero-scale 字号；页面身份由菜单、页签、面包屑和必要标题承担。

## 4. Elevation

系统以边界线、背景分层和组件库弹层阴影表达层级。常驻工作区表面尽量保持平，阴影只用于 Modal、Drawer、Dropdown、Tooltip、Popover 等浮层或组件库已有状态。

### Shadow Vocabulary

- **Component Default Shadow** (`Element Plus default`): 弹层和浮动反馈使用组件库默认阴影，不额外重写。
- **Flat Surface** (`none`): 表格容器、筛选区和表单区默认无阴影，用边框和背景区分。

### Named Rules

**The Flat Work Surface Rule.** 工作区内容默认平铺，不用阴影堆叠卡片；只有浮层、明确边界的工具面板和重复项卡片可以获得视觉边界。

## 5. Components

组件应直接继承 Element Plus 的行为、状态和可访问性。自定义样式只负责布局、尺寸、滚动区域和必要间距。

### Buttons

- **Shape:** 默认 6px 圆角。
- **Primary:** `#1f5eff` 或 Element Plus CSS 变量 `--el-color-primary`，用于页面主动作和确认动作。
- **Hover / Focus:** 使用组件库默认状态；图标按钮必须有可访问名称。
- **Secondary / Ghost / Tertiary:** 使用组件库默认按钮类型，不自绘新按钮。

### Chips

- **Style:** 状态和分类优先用 `Tag`；筛选型选择可用 `Segmented` 或 `Tabs`。
- **State:** selected / unselected 状态必须可见，不能只靠颜色表达。

### Cards / Containers

- **Corner Style:** 业务容器默认 6-8px。
- **Background:** 工作区 `#f5f7fb`，容器 `#ffffff`。
- **Shadow Strategy:** 默认无阴影，按 Elevation 规则处理。
- **Border:** 使用 `#e6eaf2` 或组件库 token。
- **Internal Padding:** 内容区 16px。

### Inputs / Fields

- **Style:** 使用 `Form`、`Input`、`Select`、`DatePicker`、`InputNumber` 等组件库原生形态。
- **Focus:** 使用组件库默认 focus ring，不手写覆盖 `.ant-*` 内部 DOM 类。
- **Error / Disabled:** 错误、禁用和提交中状态必须通过组件状态表达；复杂禁用原因补最小提示。

### Navigation

PC 使用左侧 `Menu`、顶部用户区和多页签表达结构。

## 6. Do's and Don'ts

### Do:

- **Do** 通过 Element Plus CSS 变量、组件属性和少量布局 CSS 控制主题。
- **Do** 让首屏直接呈现表格、筛选、表单、详情、状态面板或待办内容。
- **Do** 覆盖 Loading、Empty、Error、Disabled、Submitting 等状态。
- **Do** 保持 PC 管理界面高密度。
- **Do** 让图标按钮具备 `aria-label` 或可见文本。

### Don't:

- **Don't** 做成营销落地页、宣传站、深色炫技后台。
- **Don't** 使用大面积渐变、玻璃拟态、装饰卡片、插画堆叠或品牌 hero。
- **Don't** 重度覆盖 `.ant-*` 内部 DOM 类名来改变基础视觉。
- **Don't** 为普通业务操作发明非标准表单、弹窗、滚动条或按钮形态。
- **Don't** 每个页面都放标题说明块、欢迎语、功能介绍或空洞引导文案。
