create table am_archive_item_search_rebuild_job
(
    id                     bigserial primary key,
    category_id            bigint       not null references am_archive_category (id),
    table_name             varchar(63)  not null,
    status                 varchar(20)  not null,
    total_count            integer      not null,
    processed_count        integer      not null default 0,
    max_item_id            bigint,
    last_processed_item_id bigint,
    requested_by           bigint       not null,
    error_code             varchar(100),
    error_message          varchar(1000),
    version                integer      not null default 0,
    created_at             timestamp    not null default localtimestamp,
    updated_at             timestamp    not null default localtimestamp,
    constraint ck_am_archive_item_search_rebuild_job_status
        check (status in ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    constraint ck_am_archive_item_search_rebuild_job_counts
        check (total_count >= 0 and processed_count >= 0)
);

create index idx_am_archive_item_search_rebuild_job_pending
    on am_archive_item_search_rebuild_job (status, id)
    where status in ('QUEUED', 'RUNNING');

comment on table am_archive_item_search_rebuild_job is '档案条目搜索投影重建任务';
comment on column am_archive_item_search_rebuild_job.id is '任务 ID';
comment on column am_archive_item_search_rebuild_job.category_id is '档案分类 ID';
comment on column am_archive_item_search_rebuild_job.table_name is '任务创建时的动态档案表名';
comment on column am_archive_item_search_rebuild_job.status is '任务状态';
comment on column am_archive_item_search_rebuild_job.total_count is '任务创建时待处理记录数';
comment on column am_archive_item_search_rebuild_job.processed_count is '已完成投影同步的记录数';
comment on column am_archive_item_search_rebuild_job.max_item_id is '任务创建时的最大档案条目 ID';
comment on column am_archive_item_search_rebuild_job.last_processed_item_id is '最近完成的档案条目 ID';
comment on column am_archive_item_search_rebuild_job.requested_by is '任务发起人';
comment on column am_archive_item_search_rebuild_job.error_code is '失败错误码';
comment on column am_archive_item_search_rebuild_job.error_message is '失败原因';
