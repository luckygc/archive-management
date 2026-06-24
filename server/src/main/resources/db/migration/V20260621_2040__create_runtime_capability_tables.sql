create table am_runtime_job
(
    id                bigserial primary key,
    queue_name        varchar(100) not null,
    spec_version      varchar(10)  not null default '1.0',
    message_id        varchar(255) not null,
    message_source    varchar(255) not null,
    message_type      varchar(255) not null,
    message_subject   varchar(255),
    data_content_type varchar(100) not null default 'application/json',
    data_json         text         not null,
    message_time      timestamp    not null default localtimestamp,
    status            varchar(30)  not null default 'pending',
    attempts          integer      not null default 0,
    locked_by         varchar(100),
    locked_at         timestamp,
    lease_until       timestamp,
    available_at      timestamp    not null default localtimestamp,
    last_error        text,
    dead_letter_at    timestamp,
    completed_at      timestamp,
    created_at        timestamp    not null default localtimestamp,
    updated_at        timestamp    not null default localtimestamp
);

create index idx_am_runtime_job_claim
    on am_runtime_job (queue_name, status, available_at, id);
create index idx_am_runtime_job_lease
    on am_runtime_job (status, lease_until)
    where status = 'processing';
create index idx_am_runtime_job_message_id
    on am_runtime_job (queue_name, message_id);

comment on table am_runtime_job is '运行时数据库队列任务表';
comment on column am_runtime_job.id is '主键';
comment on column am_runtime_job.queue_name is '队列名称';
comment on column am_runtime_job.spec_version is 'CloudEvents 规范版本';
comment on column am_runtime_job.message_id is 'CloudEvents id，消息 ID';
comment on column am_runtime_job.message_source is 'CloudEvents source，消息来源';
comment on column am_runtime_job.message_type is 'CloudEvents type，消息类型';
comment on column am_runtime_job.message_subject is 'CloudEvents subject，消息主题';
comment on column am_runtime_job.data_content_type is 'CloudEvents datacontenttype，载荷内容类型';
comment on column am_runtime_job.data_json is '消息载荷 JSON 文本';
comment on column am_runtime_job.message_time is 'CloudEvents time，消息产生时间';
comment on column am_runtime_job.status is '任务状态：pending 待认领，processing 处理中，completed 已完成，dead_letter 死信';
comment on column am_runtime_job.attempts is '认领次数';
comment on column am_runtime_job.locked_by is '当前认领节点';
comment on column am_runtime_job.locked_at is '当前认领时间';
comment on column am_runtime_job.lease_until is '当前租约到期时间';
comment on column am_runtime_job.available_at is '可认领时间';
comment on column am_runtime_job.last_error is '最近一次失败信息';
comment on column am_runtime_job.dead_letter_at is '进入死信状态时间';
comment on column am_runtime_job.completed_at is '完成时间';
comment on column am_runtime_job.created_at is '创建时间';
comment on column am_runtime_job.updated_at is '更新时间';

create table am_runtime_lock
(
    lock_name    varchar(200) primary key,
    owner_id     varchar(100) not null,
    locked_until timestamp    not null,
    created_at   timestamp    not null default localtimestamp,
    updated_at   timestamp    not null default localtimestamp
);

create index idx_am_runtime_lock_locked_until on am_runtime_lock (locked_until);

comment on table am_runtime_lock is '运行时数据库锁表';
comment on column am_runtime_lock.lock_name is '锁名称';
comment on column am_runtime_lock.owner_id is '锁持有人节点';
comment on column am_runtime_lock.locked_until is '锁租约到期时间';
comment on column am_runtime_lock.created_at is '创建时间';
comment on column am_runtime_lock.updated_at is '更新时间';
