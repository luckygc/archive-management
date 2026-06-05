import "./style.css";

const tasks = [
  { title: "接收合同电子件", meta: "今日 10:30 · 12 件", status: "待接收" },
  { title: "库位盘点复核", meta: "今日 14:00 · A2 区", status: "进行中" },
  { title: "借阅归还确认", meta: "明日 09:00 · 3 份", status: "待确认" },
];

document.querySelector<HTMLDivElement>("#app")!.innerHTML = `
  <main class="phone-shell">
    <header class="hero">
      <div>
        <p>移动工作台</p>
        <h1>档案待办</h1>
      </div>
      <button type="button" aria-label="扫描档案">扫码</button>
    </header>
    <section class="summary" aria-label="今日概览">
      <article>
        <span>今日待办</span>
        <strong>18</strong>
      </article>
      <article>
        <span>已处理</span>
        <strong>11</strong>
      </article>
      <article>
        <span>预警</span>
        <strong>2</strong>
      </article>
    </section>
    <section class="quick-actions" aria-label="快捷入口">
      <button type="button">入库</button>
      <button type="button">移交</button>
      <button type="button">借阅</button>
      <button type="button">盘点</button>
    </section>
    <section class="task-list" aria-label="任务列表">
      <div class="section-head">
        <h2>当前任务</h2>
        <a href="#">全部</a>
      </div>
      ${tasks
        .map(
          (task) => `
            <article class="task-card">
              <div>
                <h3>${task.title}</h3>
                <p>${task.meta}</p>
              </div>
              <span>${task.status}</span>
            </article>
          `,
        )
        .join("")}
    </section>
    <nav class="tabbar" aria-label="底部导航">
      <a class="active" href="#">首页</a>
      <a href="#">任务</a>
      <a href="#">档案</a>
      <a href="#">我的</a>
    </nav>
  </main>
`;
