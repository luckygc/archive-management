<script setup lang="ts">
defineOptions({ name: "DashboardPage" });

const stats = [
  { label: "在库档案", value: "128,460", hint: "本月 +2.4%" },
  { label: "待归档", value: "436", hint: "待处理 +18" },
  { label: "借阅处理中", value: "72", hint: "较昨日 -6" },
  { label: "异常预警", value: "9", hint: "需复核" },
];

const tasks = [
  { title: "会计凭证批次", code: "KJ-2026-0605", status: "质检中", owner: "档案一组" },
  { title: "合同电子件移交", code: "HT-2026-0412", status: "待接收", owner: "业务协同组" },
  { title: "长期保管卷盒盘点", code: "PD-2026-0198", status: "进行中", owner: "库房管理组" },
  { title: "影像文件元数据补录", code: "YX-2026-0336", status: "待分派", owner: "数字化组" },
];
</script>

<template>
  <section class="dashboard-page">
    <div class="dashboard-page__toolbar">
      <div class="dashboard-page__actions">
        <el-button>新建移交</el-button>
        <el-button type="primary">档案入库</el-button>
      </div>
    </div>

    <div class="dashboard-page__stats">
      <article v-for="item in stats" :key="item.label" class="stat-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <em>{{ item.hint }}</em>
      </article>
    </div>

    <div class="dashboard-page__grid">
      <section class="panel panel--main">
        <header>
          <h2>待办队列</h2>
          <el-button>刷新</el-button>
        </header>
        <el-table :data="tasks" height="100%">
          <el-table-column prop="title" label="任务" min-width="160" />
          <el-table-column prop="code" label="编号" width="150" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="owner" label="责任组" width="140" />
        </el-table>
      </section>
      <section class="panel">
        <header>
          <h2>库房状态</h2>
        </header>
        <div class="storage-status">
          <div>
            <span>库位占用</span>
            <strong>76%</strong>
          </div>
          <el-progress :percentage="76" />
          <div>
            <span>温湿度巡检</span>
            <strong>正常</strong>
          </div>
          <div>
            <span>待复核目录</span>
            <strong>31 条</strong>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped lang="scss">
.dashboard-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: auto;
  padding: 20px;
}

.dashboard-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
}

.dashboard-page__actions {
  display: flex;
  gap: 10px;
}

.dashboard-page__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.stat-card,
.panel {
  border: 1px solid var(--am-border);
  border-radius: 8px;
  background: var(--am-bg-surface);
}

.stat-card {
  display: grid;
  gap: 8px;
  padding: 16px;

  span {
    color: var(--am-text-muted);
  }

  strong {
    font-size: 26px;
  }

  em {
    color: #166534;
    font-style: normal;
  }
}

.dashboard-page__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 14px;
  min-height: 360px;
  margin-top: 14px;
}

.panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 14px;

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
  }

  h2 {
    margin: 0;
    font-size: 16px;
  }
}

.panel--main {
  min-width: 0;
}

.storage-status {
  display: grid;
  gap: 18px;

  > div {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  span {
    color: var(--am-text-muted);
  }
}
</style>
