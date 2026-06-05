import "./style.css";

const stats = [
  { label: "在库档案", value: "128,460", trend: "+2.4%" },
  { label: "待归档", value: "436", trend: "+18" },
  { label: "借阅处理中", value: "72", trend: "-6" },
  { label: "异常预警", value: "9", trend: "待处理" },
];

const queues = [
  { title: "会计凭证批次", code: "KJ-2026-0605", status: "质检中", owner: "档案一组" },
  { title: "合同电子件移交", code: "HT-2026-0412", status: "待接收", owner: "业务协同组" },
  { title: "长期保管卷盒盘点", code: "PD-2026-0198", status: "进行中", owner: "库房管理组" },
  { title: "影像文件元数据补录", code: "YX-2026-0336", status: "待分派", owner: "数字化组" },
];

document.querySelector<HTMLDivElement>("#app")!.innerHTML = `
  <aside class="sidebar">
    <div class="brand">
      <span class="brand-mark">AM</span>
      <span>档案管理</span>
    </div>
    <nav class="nav" aria-label="主导航">
      <a class="active" href="#">工作台</a>
      <a href="#">档案库</a>
      <a href="#">移交接收</a>
      <a href="#">借阅利用</a>
      <a href="#">统计分析</a>
      <a href="#">系统配置</a>
    </nav>
  </aside>
  <main class="workspace">
    <header class="topbar">
      <div>
        <p class="eyebrow">PC 工作台</p>
        <h1>档案业务总览</h1>
      </div>
      <div class="actions" aria-label="快捷操作">
        <button type="button">新建移交</button>
        <button type="button" class="primary">档案入库</button>
      </div>
    </header>
    <section class="stats" aria-label="指标概览">
      ${stats
        .map(
          (item) => `
            <article class="stat">
              <span>${item.label}</span>
              <strong>${item.value}</strong>
              <em>${item.trend}</em>
            </article>
          `,
        )
        .join("")}
    </section>
    <section class="content-grid">
      <div class="panel queue-panel">
        <div class="panel-head">
          <h2>待办队列</h2>
          <button type="button" aria-label="刷新待办">刷新</button>
        </div>
        <div class="table" role="table" aria-label="待办任务">
          <div class="table-row table-header" role="row">
            <span>任务</span>
            <span>编号</span>
            <span>状态</span>
            <span>责任组</span>
          </div>
          ${queues
            .map(
              (item) => `
                <div class="table-row" role="row">
                  <span>${item.title}</span>
                  <span>${item.code}</span>
                  <span><mark>${item.status}</mark></span>
                  <span>${item.owner}</span>
                </div>
              `,
            )
            .join("")}
        </div>
      </div>
      <div class="panel side-panel">
        <div class="panel-head">
          <h2>库房状态</h2>
        </div>
        <div class="storage">
          <div>
            <span>库位占用</span>
            <strong>76%</strong>
          </div>
          <progress max="100" value="76">76%</progress>
          <div>
            <span>温湿度巡检</span>
            <strong>正常</strong>
          </div>
          <div>
            <span>待复核目录</span>
            <strong>31 条</strong>
          </div>
        </div>
      </div>
    </section>
  </main>
`;
