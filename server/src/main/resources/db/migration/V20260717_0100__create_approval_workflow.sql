create table am_approval_workflow_definition
(
    id                   bigserial primary key,
    definition_code      varchar(100)  not null,
    definition_name      varchar(255)  not null,
    business_type        varchar(100)  not null,
    enabled              boolean       not null default true,
    draft_revision       integer       not null default 1,
    graph_json           jsonb         not null check (jsonb_typeof(graph_json) = 'object'),
    published_version_id bigint,
    deleted_flag         boolean       not null default false,
    version              integer       not null default 0,
    created_by           bigint,
    created_at           timestamp     not null default localtimestamp,
    updated_by           bigint,
    updated_at           timestamp     not null default localtimestamp,
    constraint ck_am_approval_definition_draft_revision check (draft_revision > 0),
    constraint ck_am_approval_definition_version check (version >= 0)
);

create unique index uk_am_approval_definition_code_active
    on am_approval_workflow_definition (definition_code)
    where deleted_flag = false;
create index idx_am_approval_definition_business_active
    on am_approval_workflow_definition (business_type, enabled, id desc)
    where deleted_flag = false;

create table am_approval_workflow_definition_version
(
    id                              bigserial primary key,
    definition_id                   bigint       not null references am_approval_workflow_definition (id),
    version_number                  integer      not null,
    graph_json                      jsonb        not null,
    flowable_deployment_id          varchar(100) not null,
    flowable_process_definition_id  varchar(150) not null,
    flowable_process_definition_key varchar(100) not null,
    published_by                    bigint       not null references am_authentication_user (id),
    published_at                    timestamp    not null,
    created_by                      bigint,
    created_at                      timestamp    not null default localtimestamp,
    constraint ck_am_approval_definition_version_number check (version_number > 0),
    constraint ck_am_approval_definition_version_graph check (jsonb_typeof(graph_json) = 'object')
);

create unique index uk_am_approval_definition_version
    on am_approval_workflow_definition_version (definition_id, version_number);
create index idx_am_approval_definition_version_latest
    on am_approval_workflow_definition_version (definition_id, version_number desc, id desc);

alter table am_approval_workflow_definition
    add constraint fk_am_approval_definition_published_version
        foreign key (published_version_id) references am_approval_workflow_definition_version (id);

create table am_approval_workflow_instance
(
    id                           bigserial primary key,
    definition_id                bigint       not null references am_approval_workflow_definition (id),
    definition_version_id        bigint       not null references am_approval_workflow_definition_version (id),
    business_type                varchar(100) not null,
    business_id                  varchar(200) not null,
    title                        varchar(255) not null,
    initiator_user_id            bigint       not null references am_authentication_user (id),
    flowable_process_instance_id varchar(100) not null,
    status                       varchar(30)  not null,
    current_node_code            varchar(100),
    current_node_name            varchar(255),
    completed_at                 timestamp,
    version                      integer      not null default 0,
    created_by                   bigint,
    created_at                   timestamp    not null default localtimestamp,
    updated_by                   bigint,
    updated_at                   timestamp    not null default localtimestamp,
    constraint ck_am_approval_instance_status
        check (status in ('RUNNING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'TERMINATED')),
    constraint ck_am_approval_instance_completion
        check ((status = 'RUNNING' and completed_at is null)
            or (status <> 'RUNNING' and completed_at is not null)),
    constraint ck_am_approval_instance_version check (version >= 0)
);

create unique index uk_am_approval_instance_flowable_id
    on am_approval_workflow_instance (flowable_process_instance_id);
create index idx_am_approval_instance_business
    on am_approval_workflow_instance (business_type, business_id, created_at desc, id desc);
create index idx_am_approval_instance_initiator
    on am_approval_workflow_instance (initiator_user_id, created_at desc, id desc);
create index idx_am_approval_instance_status
    on am_approval_workflow_instance (status, created_at desc, id desc);

create table am_unified_todo
(
    id               bigserial primary key,
    source_type      varchar(100) not null,
    source_task_id   varchar(150) not null,
    business_type    varchar(100) not null,
    business_id      varchar(200) not null,
    title            varchar(255) not null,
    node_name        varchar(255),
    assignee_user_id bigint       not null references am_authentication_user (id),
    status           varchar(30)  not null default 'PENDING',
    source_path      varchar(500) not null,
    completed_at     timestamp,
    version          integer      not null default 0,
    created_by       bigint,
    created_at       timestamp    not null default localtimestamp,
    updated_by       bigint,
    updated_at       timestamp    not null default localtimestamp,
    constraint ck_am_unified_todo_status
        check (status in ('PENDING', 'COMPLETED', 'CANCELLED')),
    constraint ck_am_unified_todo_completion
        check ((status = 'PENDING' and completed_at is null)
            or (status <> 'PENDING' and completed_at is not null)),
    constraint ck_am_unified_todo_assignee check (assignee_user_id > 0),
    constraint ck_am_unified_todo_source_path
        check (left(source_path, 1) = '/'
            and left(source_path, 2) <> '//'
            and position('://' in source_path) = 0),
    constraint ck_am_unified_todo_version check (version >= 0)
);

create unique index uk_am_unified_todo_source_assignee
    on am_unified_todo (source_type, source_task_id, assignee_user_id);
create index idx_am_unified_todo_assignee_status
    on am_unified_todo (assignee_user_id, status, created_at desc, id desc);
create index idx_am_unified_todo_source
    on am_unified_todo (source_type, source_task_id, id);

comment on table am_approval_workflow_definition is '审批流定义草稿表';
comment on table am_approval_workflow_definition_version is '审批流不可变发布版本表';
comment on table am_approval_workflow_instance is '审批流业务实例绑定和最终状态表';
comment on table am_unified_todo is '跨业务统一待办查询投影表';
comment on column am_approval_workflow_definition.graph_json is '可视化审批流程图草稿 JSON';
comment on column am_approval_workflow_definition_version.graph_json is '发布时冻结的可视化审批流程图 JSON';
comment on column am_approval_workflow_instance.status is '实例状态：RUNNING、APPROVED、REJECTED、WITHDRAWN、TERMINATED';
comment on column am_unified_todo.status is '投影状态：PENDING、COMPLETED、CANCELLED';
comment on column am_unified_todo.source_path is '来源业务站内绝对路径';

insert into am_authorization_permission
    (permission_code, permission_name, module_code, description)
values
    ('approval:definition:manage', '管理审批流定义', 'approval', '创建、修改、停用和发布审批流定义'),
    ('approval:instance:start', '发起审批流程', 'approval', '使用已发布审批流定义发起流程实例'),
    ('approval:instance:manage', '管理审批流程实例', 'approval', '查询和终止审批流程实例');

insert into am_authorization_role_permission_rel (role_id, permission_id)
select r.id, p.id
from am_authorization_role r
cross join am_authorization_permission p
where r.role_name = '超级管理员'
  and p.permission_code in (
      'approval:definition:manage',
      'approval:instance:start',
      'approval:instance:manage'
  );
