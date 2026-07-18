create table am_archive_governance_scheme
(
    id            bigserial primary key,
    scheme_code   varchar(100)  not null,
    scheme_name   varchar(255)  not null,
    description   varchar(1000),
    enabled       boolean       not null default true,
    sort_order    integer       not null default 0,
    deleted_flag  boolean       not null default false,
    version       integer       not null default 0,
    created_by    bigint,
    created_at    timestamp     not null default localtimestamp,
    updated_by    bigint,
    updated_at    timestamp     not null default localtimestamp,
    constraint ck_am_archive_governance_scheme_code_not_blank
        check (btrim(scheme_code) <> ''),
    constraint ck_am_archive_governance_scheme_name_not_blank
        check (btrim(scheme_name) <> '')
);

create unique index uk_am_archive_governance_scheme_code_active
    on am_archive_governance_scheme (scheme_code)
    where deleted_flag = false;
create index idx_am_archive_governance_scheme_sort_active
    on am_archive_governance_scheme (sort_order, id)
    where deleted_flag = false;

comment on table am_archive_governance_scheme is '档案治理方案表';

create table am_archive_governance_scheme_version
(
    id                  bigserial primary key,
    scheme_id           bigint        not null references am_archive_governance_scheme (id),
    version_code        varchar(100)  not null,
    version_description varchar(1000),
    status              varchar(30)   not null default 'DRAFT',
    published_by        bigint,
    published_at        timestamp,
    frozen_by           bigint,
    frozen_at           timestamp,
    retired_by          bigint,
    retired_at          timestamp,
    deleted_flag        boolean       not null default false,
    version             integer       not null default 0,
    created_by          bigint,
    created_at          timestamp     not null default localtimestamp,
    updated_by          bigint,
    updated_at          timestamp     not null default localtimestamp,
    constraint ck_am_archive_governance_version_code_not_blank
        check (btrim(version_code) <> ''),
    constraint ck_am_archive_governance_version_status
        check (status in ('DRAFT', 'PUBLISHED', 'FROZEN', 'RETIRED'))
);

create unique index uk_am_archive_governance_version_code_active
    on am_archive_governance_scheme_version (scheme_id, version_code)
    where deleted_flag = false;
create index idx_am_archive_governance_version_scheme_active
    on am_archive_governance_scheme_version (scheme_id, status, id)
    where deleted_flag = false;

comment on table am_archive_governance_scheme_version is '档案治理方案版本表';

create table am_archive_governance_scope
(
    id                bigserial primary key,
    scheme_version_id bigint       not null references am_archive_governance_scheme_version (id),
    scope_type        varchar(30)  not null,
    fonds_code        varchar(100),
    category_code     varchar(100),
    default_flag      boolean      not null default false,
    deleted_flag      boolean      not null default false,
    version           integer      not null default 0,
    created_by        bigint,
    created_at        timestamp    not null default localtimestamp,
    updated_by        bigint,
    updated_at        timestamp    not null default localtimestamp,
    constraint ck_am_archive_governance_scope_type
        check (scope_type in ('GLOBAL', 'FONDS', 'CATEGORY')),
    constraint ck_am_archive_governance_scope_shape
        check (
            (scope_type = 'GLOBAL' and fonds_code is null and category_code is null)
            or (scope_type = 'FONDS' and fonds_code is not null and category_code is null)
            or (scope_type = 'CATEGORY' and category_code is not null)
        )
);

create unique index uk_am_archive_governance_scope_default_active
    on am_archive_governance_scope
        (scope_type, coalesce(fonds_code, ''), coalesce(category_code, ''))
    where deleted_flag = false and default_flag = true;
create index idx_am_archive_governance_scope_version_active
    on am_archive_governance_scope (scheme_version_id, id)
    where deleted_flag = false;

comment on table am_archive_governance_scope is '档案治理方案适用范围表';

create table am_archive_governance_binding
(
    id                bigserial primary key,
    scheme_version_id bigint        not null references am_archive_governance_scheme_version (id),
    binding_type      varchar(50)   not null,
    target_type       varchar(100),
    target_id         bigint,
    target_code       varchar(100),
    binding_order     integer       not null default 0,
    deleted_flag      boolean       not null default false,
    version           integer       not null default 0,
    created_by        bigint,
    created_at        timestamp     not null default localtimestamp,
    updated_by        bigint,
    updated_at        timestamp     not null default localtimestamp,
    constraint ck_am_archive_governance_binding_type
        check (binding_type in ('CLASSIFICATION_SCHEME', 'DESCRIPTION_PROFILE', 'REFERENCE_CODE_RULE')),
    constraint ck_am_archive_governance_binding_target
        check (target_id is not null or btrim(coalesce(target_code, '')) <> '')
);

create index idx_am_archive_governance_binding_version_active
    on am_archive_governance_binding (scheme_version_id, binding_type, binding_order, id)
    where deleted_flag = false;

comment on table am_archive_governance_binding is '档案治理方案外部配置绑定表';

create table am_archive_runtime_definition
(
    id                      bigserial primary key,
    scheme_version_id       bigint        not null references am_archive_governance_scheme_version (id),
    definition_kind         varchar(20)   not null,
    definition_code         varchar(100)  not null,
    definition_name         varchar(255)  not null,
    trigger_point           varchar(50)   not null,
    scope_fonds_code        varchar(100),
    scope_category_code     varchar(100),
    scope_archive_level     varchar(30),
    priority                integer       not null default 0,
    condition_json          jsonb         not null default '{}'::jsonb,
    constraint_action       varchar(20),
    constraint_message      varchar(1000),
    status                  varchar(30)   not null default 'DRAFT',
    enabled                 boolean       not null default true,
    field_catalog_signature varchar(64),
    published_by            bigint,
    published_at            timestamp,
    deleted_flag            boolean       not null default false,
    version                 integer       not null default 0,
    created_by              bigint,
    created_at              timestamp     not null default localtimestamp,
    updated_by              bigint,
    updated_at              timestamp     not null default localtimestamp,
    constraint ck_am_archive_runtime_definition_kind
        check (definition_kind in ('CONSTRAINT', 'RULE')),
    constraint ck_am_archive_runtime_definition_code_not_blank
        check (btrim(definition_code) <> ''),
    constraint ck_am_archive_runtime_definition_name_not_blank
        check (btrim(definition_name) <> ''),
    constraint ck_am_archive_runtime_trigger_point
        check (trigger_point in (
            'ITEM_BEFORE_CREATE',
            'ITEM_BEFORE_UPDATE',
            'ITEM_BEFORE_DELETE',
            'VOLUME_BEFORE_CREATE',
            'VOLUME_BEFORE_ADD_ITEM',
            'FILE_BEFORE_UPLOAD',
            'EXPORT_BEFORE_CREATE'
        )),
    constraint ck_am_archive_runtime_scope_archive_level
        check (scope_archive_level is null or scope_archive_level in ('ITEM', 'VOLUME')),
    constraint ck_am_archive_runtime_condition_object
        check (jsonb_typeof(condition_json) = 'object'),
    constraint ck_am_archive_runtime_constraint_shape
        check (
            (definition_kind = 'CONSTRAINT'
                and constraint_action in ('REJECT', 'WARN')
                and btrim(coalesce(constraint_message, '')) <> '')
            or (definition_kind = 'RULE'
                and constraint_action is null
                and constraint_message is null)
        ),
    constraint ck_am_archive_runtime_definition_status
        check (status in ('DRAFT', 'PUBLISHED')),
    constraint ck_am_archive_runtime_field_catalog_signature
        check (field_catalog_signature is null or field_catalog_signature ~ '^[0-9a-f]{64}$')
);

create unique index uk_am_archive_runtime_definition_code_active
    on am_archive_runtime_definition (scheme_version_id, definition_code)
    where deleted_flag = false;
create index idx_am_archive_runtime_definition_execution_active
    on am_archive_runtime_definition
        (scheme_version_id, trigger_point, priority, definition_code, id)
    where deleted_flag = false and status = 'PUBLISHED' and enabled = true;
create index idx_am_archive_runtime_definition_category_active
    on am_archive_runtime_definition
        (scope_category_code, trigger_point, scheme_version_id, id)
    where deleted_flag = false;

comment on table am_archive_runtime_definition is '用户定义的档案运行时约束和规则';
comment on column am_archive_runtime_definition.definition_kind is '定义类型：CONSTRAINT 约束，RULE 规则';
comment on column am_archive_runtime_definition.trigger_point is '系统固定运行时触发点';
comment on column am_archive_runtime_definition.condition_json is '受控结构化条件或断言 AST';
comment on column am_archive_runtime_definition.constraint_action is '约束失败处理：REJECT 或 WARN';

create table am_archive_runtime_action
(
    id            bigserial primary key,
    definition_id bigint       not null references am_archive_runtime_definition (id),
    action_type   varchar(30)  not null,
    action_order  integer      not null default 0,
    action_params jsonb        not null default '{}'::jsonb,
    deleted_flag  boolean      not null default false,
    version       integer      not null default 0,
    created_by    bigint,
    created_at    timestamp    not null default localtimestamp,
    updated_by    bigint,
    updated_at    timestamp    not null default localtimestamp,
    constraint ck_am_archive_runtime_action_type
        check (action_type in ('REJECT', 'WARN', 'SET_FIELD')),
    constraint ck_am_archive_runtime_action_params_object
        check (jsonb_typeof(action_params) = 'object')
);

create index idx_am_archive_runtime_action_definition_active
    on am_archive_runtime_action (definition_id, action_order, id)
    where deleted_flag = false;

comment on table am_archive_runtime_action is '运行时规则使用的系统固定动作';

create table am_archive_runtime_trace
(
    id                bigserial primary key,
    scheme_version_id bigint        not null references am_archive_governance_scheme_version (id),
    trigger_point     varchar(50)   not null,
    object_type_code  varchar(100)  not null,
    object_id         bigint,
    definition_id     bigint references am_archive_runtime_definition (id),
    definition_code   varchar(100),
    definition_kind   varchar(20),
    matched_flag      boolean       not null default false,
    blocking_flag     boolean       not null default false,
    action_json       jsonb         not null default '[]'::jsonb,
    message           varchar(1000),
    severity          varchar(30),
    skipped_reason    varchar(1000),
    created_by        bigint,
    created_at        timestamp     not null default localtimestamp,
    constraint ck_am_archive_runtime_trace_trigger_point
        check (trigger_point in (
            'ITEM_BEFORE_CREATE',
            'ITEM_BEFORE_UPDATE',
            'ITEM_BEFORE_DELETE',
            'VOLUME_BEFORE_CREATE',
            'VOLUME_BEFORE_ADD_ITEM',
            'FILE_BEFORE_UPLOAD',
            'EXPORT_BEFORE_CREATE'
        )),
    constraint ck_am_archive_runtime_trace_definition_kind
        check (definition_kind is null or definition_kind in ('CONSTRAINT', 'RULE')),
    constraint ck_am_archive_runtime_trace_severity
        check (severity is null or severity in ('INFO', 'WARNING', 'ERROR')),
    constraint ck_am_archive_runtime_trace_action_array
        check (jsonb_typeof(action_json) = 'array')
);

create index idx_am_archive_runtime_trace_object
    on am_archive_runtime_trace (object_type_code, object_id, created_at desc, id desc);
create index idx_am_archive_runtime_trace_version
    on am_archive_runtime_trace (scheme_version_id, trigger_point, created_at desc, id desc);

comment on table am_archive_runtime_trace is '档案运行时约束和规则执行追踪';

create function am_archive_runtime_definition_immutable()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        if old.status = 'PUBLISHED' then
            raise exception using
                errcode = '23514',
                constraint = 'ck_am_archive_runtime_definition_published_immutable',
                message = '已发布运行时定义不可删除';
        end if;
        return old;
    end if;

    if old.status = 'PUBLISHED' and (
        new.scheme_version_id is distinct from old.scheme_version_id
        or new.definition_kind is distinct from old.definition_kind
        or new.definition_code is distinct from old.definition_code
        or new.definition_name is distinct from old.definition_name
        or new.trigger_point is distinct from old.trigger_point
        or new.scope_fonds_code is distinct from old.scope_fonds_code
        or new.scope_category_code is distinct from old.scope_category_code
        or new.scope_archive_level is distinct from old.scope_archive_level
        or new.priority is distinct from old.priority
        or new.condition_json is distinct from old.condition_json
        or new.constraint_action is distinct from old.constraint_action
        or new.constraint_message is distinct from old.constraint_message
        or new.status is distinct from old.status
        or new.field_catalog_signature is distinct from old.field_catalog_signature
        or new.deleted_flag is distinct from old.deleted_flag
    ) then
        raise exception using
            errcode = '23514',
            constraint = 'ck_am_archive_runtime_definition_published_immutable',
            message = '已发布运行时定义不可原地修改';
    end if;
    return new;
end;
$$;

create trigger trg_am_archive_runtime_definition_immutable
before update or delete on am_archive_runtime_definition
for each row execute function am_archive_runtime_definition_immutable();

create function am_archive_runtime_action_immutable()
returns trigger
language plpgsql
as $$
declare
    parent_id bigint;
    parent_status varchar(30);
begin
    parent_id := case when tg_op = 'DELETE' then old.definition_id else new.definition_id end;
    select status into parent_status
    from am_archive_runtime_definition
    where id = parent_id;

    if parent_status = 'PUBLISHED' then
        raise exception using
            errcode = '23514',
            constraint = 'ck_am_archive_runtime_action_published_immutable',
            message = '已发布运行时规则的动作不可修改';
    end if;
    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

create trigger trg_am_archive_runtime_action_immutable
before insert or update or delete on am_archive_runtime_action
for each row execute function am_archive_runtime_action_immutable();

create function am_archive_runtime_definition_consistent()
returns trigger
language plpgsql
as $$
declare
    active_actions bigint;
    governance_status varchar(30);
begin
    select status into governance_status
    from am_archive_governance_scheme_version
    where id = new.scheme_version_id;

    if not new.deleted_flag
        and governance_status <> 'DRAFT'
        and new.status <> 'PUBLISHED' then
        raise exception using
            errcode = '23514',
            constraint = 'ck_am_archive_governance_runtime_definitions_published',
            message = '非草稿治理版本只能包含已发布运行时定义';
    end if;

    if new.deleted_flag or new.status <> 'PUBLISHED' then
        return null;
    end if;
    if governance_status <> 'DRAFT' then
        raise exception using
            errcode = '23514',
            constraint = 'ck_am_archive_runtime_definition_governance_editable',
            message = '只能在草稿治理版本中发布运行时定义';
    end if;

    select count(*) into active_actions
    from am_archive_runtime_action
    where definition_id = new.id and deleted_flag = false;

    if new.definition_kind = 'RULE' and active_actions = 0 then
        raise exception using
            errcode = '23514',
            constraint = 'ck_am_archive_runtime_rule_requires_action',
            message = '已发布运行时规则至少需要一个动作';
    end if;
    if new.definition_kind = 'CONSTRAINT' and active_actions <> 0 then
        raise exception using
            errcode = '23514',
            constraint = 'ck_am_archive_runtime_constraint_forbids_action',
            message = '运行时约束不能包含规则动作';
    end if;
    return null;
end;
$$;

create constraint trigger trg_am_archive_runtime_definition_consistent
after insert or update on am_archive_runtime_definition
deferrable initially deferred
for each row execute function am_archive_runtime_definition_consistent();

create function am_archive_governance_runtime_consistent()
returns trigger
language plpgsql
as $$
begin
    if new.status <> 'DRAFT' and exists (
        select 1
        from am_archive_runtime_definition definition
        where definition.scheme_version_id = new.id
          and definition.deleted_flag = false
          and definition.status <> 'PUBLISHED'
    ) then
        raise exception using
            errcode = '23514',
            constraint = 'ck_am_archive_governance_runtime_definitions_published',
            message = '非草稿治理版本只能包含已发布运行时定义';
    end if;
    return null;
end;
$$;

create constraint trigger trg_am_archive_governance_runtime_consistent
after insert or update on am_archive_governance_scheme_version
deferrable initially deferred
for each row execute function am_archive_governance_runtime_consistent();

alter table am_archive_item
    add column governance_scheme_version_id bigint references am_archive_governance_scheme_version (id);
alter table am_archive_volume
    add column governance_scheme_version_id bigint references am_archive_governance_scheme_version (id);

with default_scheme as (
    insert into am_archive_governance_scheme
        (scheme_code, scheme_name, description, enabled, sort_order)
    values
        ('default_governance', '默认治理方案', '默认运行时约束和规则治理方案', true, 0)
    returning id
),
default_version as (
    insert into am_archive_governance_scheme_version
        (scheme_id, version_code, version_description, status, published_at)
    select id,
           'v1',
           '默认治理方案初始发布版本',
           'PUBLISHED',
           localtimestamp
    from default_scheme
    returning id
)
insert into am_archive_governance_scope
    (scheme_version_id, scope_type, default_flag)
select id, 'GLOBAL', true
from default_version;

update am_archive_item
set governance_scheme_version_id = (
        select version.id
        from am_archive_governance_scheme scheme
        join am_archive_governance_scheme_version version on version.scheme_id = scheme.id
        where scheme.scheme_code = 'default_governance'
          and version.version_code = 'v1'
    )
where governance_scheme_version_id is null;

update am_archive_volume
set governance_scheme_version_id = (
        select version.id
        from am_archive_governance_scheme scheme
        join am_archive_governance_scheme_version version on version.scheme_id = scheme.id
        where scheme.scheme_code = 'default_governance'
          and version.version_code = 'v1'
    )
where governance_scheme_version_id is null;

create index idx_am_archive_item_governance_version
    on am_archive_item (governance_scheme_version_id, id)
    where deleted_flag = false;
create index idx_am_archive_volume_governance_version
    on am_archive_volume (governance_scheme_version_id, id)
    where deleted_flag = false;

comment on column am_archive_item.governance_scheme_version_id is '档案采用的治理方案版本 ID';
comment on column am_archive_volume.governance_scheme_version_id is '案卷采用的治理方案版本 ID';

insert into am_authorization_permission
    (permission_code, permission_name, module_code, description)
values
    ('archive:governance:manage', '管理档案治理', 'archive', '维护治理方案和运行时约束规则');
