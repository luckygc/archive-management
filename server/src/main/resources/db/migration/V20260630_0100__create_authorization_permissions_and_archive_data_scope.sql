create table am_authorization_permission
(
    id              bigserial primary key,
    permission_code varchar(120) not null,
    permission_name varchar(120) not null,
    module_code     varchar(80)  not null,
    description     varchar(500),
    enabled         boolean      not null default true,
    created_at      timestamp    not null default localtimestamp,
    updated_at      timestamp    not null default localtimestamp
);

create unique index uk_am_authorization_permission_code on am_authorization_permission (permission_code);
create index idx_am_authorization_permission_module on am_authorization_permission (module_code, permission_code);

comment on table am_authorization_permission is '系统功能权限点表';
comment on column am_authorization_permission.permission_code is '功能权限编码';
comment on column am_authorization_permission.permission_name is '功能权限名称';
comment on column am_authorization_permission.module_code is '所属模块编码';
comment on column am_authorization_permission.description is '权限说明';
comment on column am_authorization_permission.enabled is '是否启用';

create table am_authorization_role_permission_rel
(
    id            bigserial primary key,
    role_id       bigint    not null references am_authorization_role (id),
    permission_id bigint    not null references am_authorization_permission (id),
    created_at    timestamp not null default localtimestamp
);

create unique index uk_am_authorization_role_permission_rel_role_permission
    on am_authorization_role_permission_rel (role_id, permission_id);
create index idx_am_authorization_role_permission_rel_role_id
    on am_authorization_role_permission_rel (role_id);
create index idx_am_authorization_role_permission_rel_permission_id
    on am_authorization_role_permission_rel (permission_id);

comment on table am_authorization_role_permission_rel is '角色功能权限关系表';
comment on column am_authorization_role_permission_rel.role_id is '角色 ID';
comment on column am_authorization_role_permission_rel.permission_id is '功能权限 ID';

create table am_archive_data_scope
(
    id                     bigserial primary key,
    scope_code             varchar(100) not null,
    scope_name             varchar(120) not null,
    scope_type             varchar(30)  not null,
    dynamic_condition_json jsonb,
    enabled                boolean      not null default true,
    description            varchar(500),
    version                integer      not null default 0,
    created_at             timestamp    not null default localtimestamp,
    updated_at             timestamp    not null default localtimestamp
);

create unique index uk_am_archive_data_scope_code on am_archive_data_scope (scope_code);
create index idx_am_archive_data_scope_enabled on am_archive_data_scope (enabled, id);

comment on table am_archive_data_scope is '档案数据范围表';
comment on column am_archive_data_scope.scope_code is '数据范围编码；星号范围使用 *';
comment on column am_archive_data_scope.scope_name is '数据范围名称';
comment on column am_archive_data_scope.scope_type is '范围类型：ALL 任意范围，CONDITIONAL 条件范围';
comment on column am_archive_data_scope.dynamic_condition_json is '动态字段范围条件 JSON；只保存结构化条件，不保存 SQL';
comment on column am_archive_data_scope.enabled is '是否启用';

create table am_archive_data_scope_dimension
(
    id                  bigserial primary key,
    scope_id            bigint      not null references am_archive_data_scope (id),
    dimension_type      varchar(30) not null,
    target_id           bigint,
    target_code         varchar(120),
    include_descendants boolean     not null default false,
    sort_order          integer     not null default 0,
    created_at          timestamp   not null default localtimestamp
);

create unique index uk_am_archive_data_scope_dimension_target
    on am_archive_data_scope_dimension
        (scope_id, dimension_type, coalesce(target_id, -1), coalesce(target_code, ''), include_descendants);
create index idx_am_archive_data_scope_dimension_scope_id
    on am_archive_data_scope_dimension (scope_id, sort_order, id);

comment on table am_archive_data_scope_dimension is '档案数据范围固定维度条件表';
comment on column am_archive_data_scope_dimension.scope_id is '数据范围 ID';
comment on column am_archive_data_scope_dimension.dimension_type is '维度类型：FONDS、CATEGORY、SECURITY_LEVEL、RETENTION_PERIOD';
comment on column am_archive_data_scope_dimension.target_id is '目标 ID，例如分类 ID';
comment on column am_archive_data_scope_dimension.target_code is '目标编码，例如全宗编码或密级编码';
comment on column am_archive_data_scope_dimension.include_descendants is '是否包含树形子级';

create table am_archive_data_scope_subject_rel
(
    id            bigserial primary key,
    subject_type  varchar(30) not null,
    subject_id    bigint      not null,
    scope_id      bigint    not null references am_archive_data_scope (id),
    created_at    timestamp not null default localtimestamp
);

create unique index uk_am_archive_data_scope_subject_rel_subject_scope
    on am_archive_data_scope_subject_rel (subject_type, subject_id, scope_id);
create index idx_am_archive_data_scope_subject_rel_subject
    on am_archive_data_scope_subject_rel (subject_type, subject_id);
create index idx_am_archive_data_scope_subject_rel_scope_id
    on am_archive_data_scope_subject_rel (scope_id);

comment on table am_archive_data_scope_subject_rel is '档案数据范围授权主体关系表';
comment on column am_archive_data_scope_subject_rel.subject_type is '授权主体类型：ROLE、USER、DEPARTMENT';
comment on column am_archive_data_scope_subject_rel.subject_id is '授权主体 ID；组织主体由后续组织模块定义';
comment on column am_archive_data_scope_subject_rel.scope_id is '档案数据范围 ID';

alter table am_archive_field
    add column data_scope_filterable boolean not null default false;

comment on column am_archive_field.data_scope_filterable is '是否允许作为档案数据范围动态字段条件';

insert into am_authorization_permission
    (permission_code, permission_name, module_code, description)
values
    ('archive:item:read', '读取档案', 'archive', '读取档案列表、详情和全文发现结果'),
    ('archive:item:create', '创建档案', 'archive', '创建档案条目和案卷'),
    ('archive:item:update', '修改档案', 'archive', '修改档案条目和案卷'),
    ('archive:item:delete', '删除档案', 'archive', '逻辑删除档案条目和案卷'),
    ('archive:item:lock', '锁定档案', 'archive', '锁定和解锁档案条目'),
    ('archive:item:download-electronic-file', '下载档案电子文件', 'archive', '下载档案电子文件或创建下载短链'),
    ('archive:export', '导出档案', 'archive', '按查询条件导出档案数据'),
    ('archive:metadata:manage', '管理档案元数据', 'archive', '维护全宗、分类、字段、布局、密级和保管期限'),
    ('authorization:permission:manage', '管理功能权限', 'authorization', '查看权限点并维护角色功能权限'),
    ('authentication:session:manage', '管理登录会话', 'authentication', '查看登录会话并踢出其他会话'),
    ('authentication:audit:read', '查询认证审计', 'authentication', '查询登录、退出和踢下线审计事件'),
    ('archive:data-scope:manage', '管理档案数据范围', 'archive', '维护档案数据范围和主体范围绑定'),
    ('authentication:user:manage', '管理用户', 'authentication', '创建、编辑用户和分配角色'),
    ('authorization:role:manage', '管理角色', 'authorization', '创建、编辑和删除角色'),
    ('organization:department:manage', '管理组织架构', 'organization', '维护组织架构部门树');

insert into am_archive_data_scope (scope_code, scope_name, scope_type, description)
values ('*', '全部档案', 'ALL', '系统内置任意档案数据范围');

insert into am_authorization_role_permission_rel (role_id, permission_id)
select r.id, p.id
from am_authorization_role r
cross join am_authorization_permission p
where r.role_name = '超级管理员';

insert into am_archive_data_scope_subject_rel (subject_type, subject_id, scope_id)
select 'ROLE', r.id, s.id
from am_authorization_role r
cross join am_archive_data_scope s
where r.role_name = '超级管理员'
  and s.scope_code = '*';
