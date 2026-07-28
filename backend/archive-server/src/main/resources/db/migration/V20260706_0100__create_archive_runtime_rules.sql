create table am_archive_runtime_definition
(
    id                      bigserial primary key,
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
    on am_archive_runtime_definition (definition_code)
    where deleted_flag = false;
create index idx_am_archive_runtime_definition_execution_active
    on am_archive_runtime_definition
        (trigger_point, scope_fonds_code, scope_category_code, scope_archive_level,
         priority, definition_code, id)
    where deleted_flag = false and status = 'PUBLISHED' and enabled = true;
create index idx_am_archive_runtime_definition_category_active
    on am_archive_runtime_definition
        (scope_category_code, trigger_point, id)
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
create index idx_am_archive_runtime_trace_trigger
    on am_archive_runtime_trace (trigger_point, created_at desc, id desc);

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
        new.definition_kind is distinct from old.definition_kind
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
begin
    if new.deleted_flag or new.status <> 'PUBLISHED' then
        return null;
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

insert into am_authorization_permission
    (permission_code, permission_name, module_code, description)
values
    ('archive:rule:manage', '管理档案运行时规则', 'archive', '维护运行时约束和规则');
