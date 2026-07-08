create table am_archive_governance_scheme
(
    id            bigserial primary key,
    scheme_code   varchar(100) not null,
    scheme_name   varchar(255) not null,
    description   varchar(1000),
    enabled       boolean      not null default true,
    sort_order    integer      not null default 0,
    deleted_flag  boolean      not null default false,
    version       integer      not null default 0,
    created_by    bigint,
    created_at    timestamp    not null default localtimestamp,
    updated_by    bigint,
    updated_at    timestamp    not null default localtimestamp
);

create unique index uk_am_archive_governance_scheme_code_active
    on am_archive_governance_scheme (scheme_code)
    where deleted_flag = false;
create index idx_am_archive_governance_scheme_sort_active
    on am_archive_governance_scheme (sort_order, id)
    where deleted_flag = false;

comment on table am_archive_governance_scheme is '档案治理方案表';
comment on column am_archive_governance_scheme.id is '主键';
comment on column am_archive_governance_scheme.scheme_code is '治理方案编码';
comment on column am_archive_governance_scheme.scheme_name is '治理方案名称';
comment on column am_archive_governance_scheme.description is '治理方案说明';
comment on column am_archive_governance_scheme.enabled is '是否启用';
comment on column am_archive_governance_scheme.sort_order is '排序字段';
comment on column am_archive_governance_scheme.deleted_flag is '删除标记';
comment on column am_archive_governance_scheme.version is '乐观锁版本号';
comment on column am_archive_governance_scheme.created_by is '创建人用户 ID';
comment on column am_archive_governance_scheme.created_at is '创建时间';
comment on column am_archive_governance_scheme.updated_by is '更新人用户 ID';
comment on column am_archive_governance_scheme.updated_at is '更新时间';

create table am_archive_governance_scheme_version
(
    id                  bigserial primary key,
    scheme_id           bigint       not null references am_archive_governance_scheme (id),
    version_code        varchar(100) not null,
    version_description varchar(1000),
    status              varchar(30)  not null default 'DRAFT',
    published_by        bigint,
    published_at        timestamp,
    frozen_by           bigint,
    frozen_at           timestamp,
    retired_by          bigint,
    retired_at          timestamp,
    deleted_flag        boolean      not null default false,
    version             integer      not null default 0,
    created_by          bigint,
    created_at          timestamp    not null default localtimestamp,
    updated_by          bigint,
    updated_at          timestamp    not null default localtimestamp
);

create unique index uk_am_archive_governance_version_code_active
    on am_archive_governance_scheme_version (scheme_id, version_code)
    where deleted_flag = false;
create index idx_am_archive_governance_version_scheme_active
    on am_archive_governance_scheme_version (scheme_id, status, id)
    where deleted_flag = false;

comment on table am_archive_governance_scheme_version is '档案治理方案版本表';
comment on column am_archive_governance_scheme_version.id is '主键';
comment on column am_archive_governance_scheme_version.scheme_id is '治理方案 ID';
comment on column am_archive_governance_scheme_version.version_code is '版本编码';
comment on column am_archive_governance_scheme_version.version_description is '版本说明';
comment on column am_archive_governance_scheme_version.status is '版本状态：DRAFT 草稿，PUBLISHED 已发布，FROZEN 已冻结，RETIRED 已退役';
comment on column am_archive_governance_scheme_version.published_by is '发布人用户 ID';
comment on column am_archive_governance_scheme_version.published_at is '发布时间';
comment on column am_archive_governance_scheme_version.frozen_by is '冻结人用户 ID';
comment on column am_archive_governance_scheme_version.frozen_at is '冻结时间';
comment on column am_archive_governance_scheme_version.retired_by is '退役人用户 ID';
comment on column am_archive_governance_scheme_version.retired_at is '退役时间';
comment on column am_archive_governance_scheme_version.deleted_flag is '删除标记';
comment on column am_archive_governance_scheme_version.version is '乐观锁版本号';
comment on column am_archive_governance_scheme_version.created_by is '创建人用户 ID';
comment on column am_archive_governance_scheme_version.created_at is '创建时间';
comment on column am_archive_governance_scheme_version.updated_by is '更新人用户 ID';
comment on column am_archive_governance_scheme_version.updated_at is '更新时间';

create table am_archive_governance_scope
(
    id                bigserial primary key,
    scheme_version_id bigint      not null references am_archive_governance_scheme_version (id),
    scope_type        varchar(30) not null,
    fonds_code        varchar(100),
    category_code     varchar(100),
    default_flag      boolean     not null default false,
    deleted_flag      boolean     not null default false,
    version           integer     not null default 0,
    created_by        bigint,
    created_at        timestamp   not null default localtimestamp,
    updated_by        bigint,
    updated_at        timestamp   not null default localtimestamp
);

create unique index uk_am_archive_governance_scope_default_active
    on am_archive_governance_scope (scope_type, coalesce(fonds_code, ''), coalesce(category_code, ''))
    where deleted_flag = false and default_flag = true;
create index idx_am_archive_governance_scope_version_active
    on am_archive_governance_scope (scheme_version_id, id)
    where deleted_flag = false;

comment on table am_archive_governance_scope is '档案治理方案适用范围表';
comment on column am_archive_governance_scope.id is '主键';
comment on column am_archive_governance_scope.scheme_version_id is '治理方案版本 ID';
comment on column am_archive_governance_scope.scope_type is '适用范围类型：GLOBAL 全局，FONDS 全宗，CATEGORY 分类';
comment on column am_archive_governance_scope.fonds_code is '适用全宗编码';
comment on column am_archive_governance_scope.category_code is '适用分类编码';
comment on column am_archive_governance_scope.default_flag is '是否默认版本';
comment on column am_archive_governance_scope.deleted_flag is '删除标记';
comment on column am_archive_governance_scope.version is '乐观锁版本号';
comment on column am_archive_governance_scope.created_by is '创建人用户 ID';
comment on column am_archive_governance_scope.created_at is '创建时间';
comment on column am_archive_governance_scope.updated_by is '更新人用户 ID';
comment on column am_archive_governance_scope.updated_at is '更新时间';

create table am_archive_governance_binding
(
    id                bigserial primary key,
    scheme_version_id bigint       not null references am_archive_governance_scheme_version (id),
    binding_type      varchar(50)  not null,
    target_type       varchar(100),
    target_id         bigint,
    target_code       varchar(100),
    binding_order     integer      not null default 0,
    deleted_flag      boolean      not null default false,
    version           integer      not null default 0,
    created_by        bigint,
    created_at        timestamp    not null default localtimestamp,
    updated_by        bigint,
    updated_at        timestamp    not null default localtimestamp
);

create index idx_am_archive_governance_binding_version_active
    on am_archive_governance_binding (scheme_version_id, binding_type, binding_order, id)
    where deleted_flag = false;

comment on table am_archive_governance_binding is '档案治理方案配置绑定表';
comment on column am_archive_governance_binding.id is '主键';
comment on column am_archive_governance_binding.scheme_version_id is '治理方案版本 ID';
comment on column am_archive_governance_binding.binding_type is '绑定类型';
comment on column am_archive_governance_binding.target_type is '目标类型';
comment on column am_archive_governance_binding.target_id is '目标 ID';
comment on column am_archive_governance_binding.target_code is '目标编码';
comment on column am_archive_governance_binding.binding_order is '绑定排序';
comment on column am_archive_governance_binding.deleted_flag is '删除标记';
comment on column am_archive_governance_binding.version is '乐观锁版本号';
comment on column am_archive_governance_binding.created_by is '创建人用户 ID';
comment on column am_archive_governance_binding.created_at is '创建时间';
comment on column am_archive_governance_binding.updated_by is '更新人用户 ID';
comment on column am_archive_governance_binding.updated_at is '更新时间';

create table am_archive_ontology_object_type
(
    id            bigserial primary key,
    type_code     varchar(100) not null,
    type_name     varchar(255) not null,
    description   varchar(1000),
    builtin_flag  boolean      not null default false,
    enabled       boolean      not null default true,
    deleted_flag  boolean      not null default false,
    version       integer      not null default 0,
    created_by    bigint,
    created_at    timestamp    not null default localtimestamp,
    updated_by    bigint,
    updated_at    timestamp    not null default localtimestamp
);

create unique index uk_am_archive_ontology_object_type_code_active
    on am_archive_ontology_object_type (type_code)
    where deleted_flag = false;
create index idx_am_archive_ontology_object_type_sort_active
    on am_archive_ontology_object_type (type_code, id)
    where deleted_flag = false;

comment on table am_archive_ontology_object_type is '档案本体对象类型表';
comment on column am_archive_ontology_object_type.id is '主键';
comment on column am_archive_ontology_object_type.type_code is '对象类型编码';
comment on column am_archive_ontology_object_type.type_name is '对象类型名称';
comment on column am_archive_ontology_object_type.description is '对象类型说明';
comment on column am_archive_ontology_object_type.builtin_flag is '是否内置类型';
comment on column am_archive_ontology_object_type.enabled is '是否启用';
comment on column am_archive_ontology_object_type.deleted_flag is '删除标记';
comment on column am_archive_ontology_object_type.version is '乐观锁版本号';
comment on column am_archive_ontology_object_type.created_by is '创建人用户 ID';
comment on column am_archive_ontology_object_type.created_at is '创建时间';
comment on column am_archive_ontology_object_type.updated_by is '更新人用户 ID';
comment on column am_archive_ontology_object_type.updated_at is '更新时间';

create table am_archive_ontology_attribute_type
(
    id                            bigserial primary key,
    attribute_code                varchar(100) not null,
    attribute_name                varchar(255) not null,
    object_type_id                bigint       not null references am_archive_ontology_object_type (id),
    data_type                     varchar(50)  not null,
    metadata_domain               varchar(50)  not null,
    cardinality                   varchar(30)  not null default 'SINGLE',
    exact_searchable              boolean      not null default false,
    sortable                      boolean      not null default false,
    description_participating     boolean      not null default false,
    reference_code_participating  boolean      not null default false,
    rule_fact_visible             boolean      not null default true,
    description                   varchar(1000),
    enabled                       boolean      not null default true,
    deleted_flag                  boolean      not null default false,
    version                       integer      not null default 0,
    created_by                    bigint,
    created_at                    timestamp    not null default localtimestamp,
    updated_by                    bigint,
    updated_at                    timestamp    not null default localtimestamp
);

create unique index uk_am_archive_ontology_attribute_type_code_active
    on am_archive_ontology_attribute_type (attribute_code)
    where deleted_flag = false;
create index idx_am_archive_ontology_attribute_type_object_active
    on am_archive_ontology_attribute_type (object_type_id, attribute_code)
    where deleted_flag = false;

comment on table am_archive_ontology_attribute_type is '档案本体属性类型表';
comment on column am_archive_ontology_attribute_type.id is '主键';
comment on column am_archive_ontology_attribute_type.attribute_code is '属性编码';
comment on column am_archive_ontology_attribute_type.attribute_name is '属性名称';
comment on column am_archive_ontology_attribute_type.object_type_id is '适用对象类型 ID';
comment on column am_archive_ontology_attribute_type.data_type is '属性数据类型';
comment on column am_archive_ontology_attribute_type.metadata_domain is '元数据域';
comment on column am_archive_ontology_attribute_type.cardinality is '基数：SINGLE 单值，MULTI 多值，REPEATED_ROW 可重复行';
comment on column am_archive_ontology_attribute_type.exact_searchable is '是否允许精确筛选';
comment on column am_archive_ontology_attribute_type.sortable is '是否允许排序';
comment on column am_archive_ontology_attribute_type.description_participating is '是否参与著录';
comment on column am_archive_ontology_attribute_type.reference_code_participating is '是否参与档号';
comment on column am_archive_ontology_attribute_type.rule_fact_visible is '是否作为规则事实可见';
comment on column am_archive_ontology_attribute_type.description is '属性说明';
comment on column am_archive_ontology_attribute_type.enabled is '是否启用';
comment on column am_archive_ontology_attribute_type.deleted_flag is '删除标记';
comment on column am_archive_ontology_attribute_type.version is '乐观锁版本号';
comment on column am_archive_ontology_attribute_type.created_by is '创建人用户 ID';
comment on column am_archive_ontology_attribute_type.created_at is '创建时间';
comment on column am_archive_ontology_attribute_type.updated_by is '更新人用户 ID';
comment on column am_archive_ontology_attribute_type.updated_at is '更新时间';

create table am_archive_ontology_attribute_mapping
(
    id                    bigserial primary key,
    attribute_type_id     bigint       not null references am_archive_ontology_attribute_type (id),
    mapping_kind          varchar(50)  not null,
    fixed_field_code      varchar(100),
    category_id           bigint references am_archive_category (id),
    archive_level         varchar(30),
    field_scope           varchar(30),
    dynamic_field_id      bigint references am_archive_field (id),
    line_table_id         bigint,
    line_field_id         bigint,
    component_field_code  varchar(100),
    process_field_code    varchar(100),
    deleted_flag          boolean      not null default false,
    version               integer      not null default 0,
    created_by            bigint,
    created_at            timestamp    not null default localtimestamp,
    updated_by            bigint,
    updated_at            timestamp    not null default localtimestamp
);

create index idx_am_archive_ontology_attribute_mapping_attribute_active
    on am_archive_ontology_attribute_mapping (attribute_type_id, mapping_kind, id)
    where deleted_flag = false;
create index idx_am_archive_ontology_attribute_mapping_dynamic_active
    on am_archive_ontology_attribute_mapping (category_id, archive_level, field_scope, dynamic_field_id)
    where deleted_flag = false;

comment on table am_archive_ontology_attribute_mapping is '档案本体属性物理字段映射表';
comment on column am_archive_ontology_attribute_mapping.id is '主键';
comment on column am_archive_ontology_attribute_mapping.attribute_type_id is '属性类型 ID';
comment on column am_archive_ontology_attribute_mapping.mapping_kind is '映射类型';
comment on column am_archive_ontology_attribute_mapping.fixed_field_code is '固定字段编码';
comment on column am_archive_ontology_attribute_mapping.category_id is '档案分类 ID';
comment on column am_archive_ontology_attribute_mapping.archive_level is '档案层级';
comment on column am_archive_ontology_attribute_mapping.field_scope is '字段域';
comment on column am_archive_ontology_attribute_mapping.dynamic_field_id is '动态字段 ID';
comment on column am_archive_ontology_attribute_mapping.line_table_id is '明细表 ID';
comment on column am_archive_ontology_attribute_mapping.line_field_id is '明细字段 ID';
comment on column am_archive_ontology_attribute_mapping.component_field_code is '文件组件字段编码';
comment on column am_archive_ontology_attribute_mapping.process_field_code is '过程字段编码';
comment on column am_archive_ontology_attribute_mapping.deleted_flag is '删除标记';
comment on column am_archive_ontology_attribute_mapping.version is '乐观锁版本号';
comment on column am_archive_ontology_attribute_mapping.created_by is '创建人用户 ID';
comment on column am_archive_ontology_attribute_mapping.created_at is '创建时间';
comment on column am_archive_ontology_attribute_mapping.updated_by is '更新人用户 ID';
comment on column am_archive_ontology_attribute_mapping.updated_at is '更新时间';

create table am_archive_ontology_relation_type
(
    id                    bigserial primary key,
    relation_code         varchar(100) not null,
    relation_name         varchar(255) not null,
    source_object_type_id bigint       not null references am_archive_ontology_object_type (id),
    target_object_type_id bigint       not null references am_archive_ontology_object_type (id),
    relation_direction    varchar(30)  not null,
    cardinality           varchar(30)  not null default 'MANY_TO_MANY',
    description           varchar(1000),
    enabled               boolean      not null default true,
    deleted_flag          boolean      not null default false,
    version               integer      not null default 0,
    created_by            bigint,
    created_at            timestamp    not null default localtimestamp,
    updated_by            bigint,
    updated_at            timestamp    not null default localtimestamp
);

create unique index uk_am_archive_ontology_relation_type_code_active
    on am_archive_ontology_relation_type (relation_code)
    where deleted_flag = false;

comment on table am_archive_ontology_relation_type is '档案本体关系类型表';
comment on column am_archive_ontology_relation_type.id is '主键';
comment on column am_archive_ontology_relation_type.relation_code is '关系编码';
comment on column am_archive_ontology_relation_type.relation_name is '关系名称';
comment on column am_archive_ontology_relation_type.source_object_type_id is '来源对象类型 ID';
comment on column am_archive_ontology_relation_type.target_object_type_id is '目标对象类型 ID';
comment on column am_archive_ontology_relation_type.relation_direction is '关系方向';
comment on column am_archive_ontology_relation_type.cardinality is '关系基数';
comment on column am_archive_ontology_relation_type.description is '关系说明';
comment on column am_archive_ontology_relation_type.enabled is '是否启用';
comment on column am_archive_ontology_relation_type.deleted_flag is '删除标记';
comment on column am_archive_ontology_relation_type.version is '乐观锁版本号';
comment on column am_archive_ontology_relation_type.created_by is '创建人用户 ID';
comment on column am_archive_ontology_relation_type.created_at is '创建时间';
comment on column am_archive_ontology_relation_type.updated_by is '更新人用户 ID';
comment on column am_archive_ontology_relation_type.updated_at is '更新时间';

create table am_archive_ontology_event_type
(
    id             bigserial primary key,
    event_code     varchar(100) not null,
    event_name     varchar(255) not null,
    object_type_id bigint       not null references am_archive_ontology_object_type (id),
    description    varchar(1000),
    enabled        boolean      not null default true,
    deleted_flag   boolean      not null default false,
    version        integer      not null default 0,
    created_by     bigint,
    created_at     timestamp    not null default localtimestamp,
    updated_by     bigint,
    updated_at     timestamp    not null default localtimestamp
);

create unique index uk_am_archive_ontology_event_type_code_active
    on am_archive_ontology_event_type (event_code)
    where deleted_flag = false;

comment on table am_archive_ontology_event_type is '档案本体事件类型表';
comment on column am_archive_ontology_event_type.id is '主键';
comment on column am_archive_ontology_event_type.event_code is '事件编码';
comment on column am_archive_ontology_event_type.event_name is '事件名称';
comment on column am_archive_ontology_event_type.object_type_id is '适用对象类型 ID';
comment on column am_archive_ontology_event_type.description is '事件说明';
comment on column am_archive_ontology_event_type.enabled is '是否启用';
comment on column am_archive_ontology_event_type.deleted_flag is '删除标记';
comment on column am_archive_ontology_event_type.version is '乐观锁版本号';
comment on column am_archive_ontology_event_type.created_by is '创建人用户 ID';
comment on column am_archive_ontology_event_type.created_at is '创建时间';
comment on column am_archive_ontology_event_type.updated_by is '更新人用户 ID';
comment on column am_archive_ontology_event_type.updated_at is '更新时间';

create table am_archive_rule_definition
(
    id                bigserial primary key,
    scheme_version_id bigint       not null references am_archive_governance_scheme_version (id),
    rule_code         varchar(100) not null,
    rule_name         varchar(255) not null,
    rule_type         varchar(50)  not null,
    trigger_code      varchar(100) not null,
    scope_fonds_code  varchar(100),
    scope_category_code varchar(100),
    scope_object_type_id bigint references am_archive_ontology_object_type (id),
    scope_archive_level varchar(30),
    scope_event_type_id bigint references am_archive_ontology_event_type (id),
    priority          integer      not null default 0,
    condition_json    jsonb        not null default '{}'::jsonb,
    status            varchar(30)  not null default 'DRAFT',
    enabled           boolean      not null default true,
    published_by      bigint,
    published_at      timestamp,
    deleted_flag      boolean      not null default false,
    version           integer      not null default 0,
    created_by        bigint,
    created_at        timestamp    not null default localtimestamp,
    updated_by        bigint,
    updated_at        timestamp    not null default localtimestamp
);

create unique index uk_am_archive_rule_definition_code_active
    on am_archive_rule_definition (scheme_version_id, rule_code)
    where deleted_flag = false;
create index idx_am_archive_rule_definition_scope_active
    on am_archive_rule_definition (scheme_version_id, trigger_code, enabled, priority, id)
    where deleted_flag = false;

comment on table am_archive_rule_definition is '档案本地规则定义表';
comment on column am_archive_rule_definition.id is '主键';
comment on column am_archive_rule_definition.scheme_version_id is '治理方案版本 ID';
comment on column am_archive_rule_definition.rule_code is '规则编码';
comment on column am_archive_rule_definition.rule_name is '规则名称';
comment on column am_archive_rule_definition.rule_type is '规则类型';
comment on column am_archive_rule_definition.trigger_code is '触发点编码';
comment on column am_archive_rule_definition.scope_fonds_code is '作用域全宗编码';
comment on column am_archive_rule_definition.scope_category_code is '作用域分类编码';
comment on column am_archive_rule_definition.scope_object_type_id is '作用域对象类型 ID';
comment on column am_archive_rule_definition.scope_archive_level is '作用域档案层级';
comment on column am_archive_rule_definition.scope_event_type_id is '作用域事件类型 ID';
comment on column am_archive_rule_definition.priority is '规则优先级';
comment on column am_archive_rule_definition.condition_json is '结构化条件树 JSON';
comment on column am_archive_rule_definition.status is '规则状态：DRAFT 草稿，PUBLISHED 已发布';
comment on column am_archive_rule_definition.enabled is '是否启用';
comment on column am_archive_rule_definition.published_by is '发布人用户 ID';
comment on column am_archive_rule_definition.published_at is '发布时间';
comment on column am_archive_rule_definition.deleted_flag is '删除标记';
comment on column am_archive_rule_definition.version is '乐观锁版本号';
comment on column am_archive_rule_definition.created_by is '创建人用户 ID';
comment on column am_archive_rule_definition.created_at is '创建时间';
comment on column am_archive_rule_definition.updated_by is '更新人用户 ID';
comment on column am_archive_rule_definition.updated_at is '更新时间';

create table am_archive_rule_effect
(
    id            bigserial primary key,
    rule_id       bigint      not null references am_archive_rule_definition (id),
    effect_type   varchar(50) not null,
    effect_order  integer     not null default 0,
    effect_params jsonb       not null default '{}'::jsonb,
    deleted_flag  boolean     not null default false,
    created_by    bigint,
    created_at    timestamp   not null default localtimestamp,
    updated_by    bigint,
    updated_at    timestamp   not null default localtimestamp
);

create index idx_am_archive_rule_effect_rule_active
    on am_archive_rule_effect (rule_id, effect_order, id)
    where deleted_flag = false;

comment on table am_archive_rule_effect is '档案本地规则效果表';
comment on column am_archive_rule_effect.id is '主键';
comment on column am_archive_rule_effect.rule_id is '规则 ID';
comment on column am_archive_rule_effect.effect_type is '效果类型';
comment on column am_archive_rule_effect.effect_order is '效果排序';
comment on column am_archive_rule_effect.effect_params is '效果参数 JSON';
comment on column am_archive_rule_effect.deleted_flag is '删除标记';
comment on column am_archive_rule_effect.created_by is '创建人用户 ID';
comment on column am_archive_rule_effect.created_at is '创建时间';
comment on column am_archive_rule_effect.updated_by is '更新人用户 ID';
comment on column am_archive_rule_effect.updated_at is '更新时间';

create table am_archive_rule_trace
(
    id                bigserial primary key,
    scheme_version_id bigint       not null references am_archive_governance_scheme_version (id),
    trigger_code      varchar(100) not null,
    object_type_code  varchar(100) not null,
    object_id         bigint,
    rule_id           bigint references am_archive_rule_definition (id),
    rule_code         varchar(100),
    rule_type         varchar(50),
    matched_flag      boolean      not null default false,
    blocking_flag     boolean      not null default false,
    effect_json       jsonb        not null default '[]'::jsonb,
    message           varchar(1000),
    severity          varchar(30),
    skipped_reason    varchar(1000),
    created_by        bigint,
    created_at        timestamp    not null default localtimestamp
);

create index idx_am_archive_rule_trace_object
    on am_archive_rule_trace (object_type_code, object_id, created_at desc, id desc);
create index idx_am_archive_rule_trace_version
    on am_archive_rule_trace (scheme_version_id, trigger_code, created_at desc, id desc);

comment on table am_archive_rule_trace is '档案本地规则执行追踪表';
comment on column am_archive_rule_trace.id is '主键';
comment on column am_archive_rule_trace.scheme_version_id is '治理方案版本 ID';
comment on column am_archive_rule_trace.trigger_code is '触发点编码';
comment on column am_archive_rule_trace.object_type_code is '对象类型编码';
comment on column am_archive_rule_trace.object_id is '对象 ID';
comment on column am_archive_rule_trace.rule_id is '规则 ID';
comment on column am_archive_rule_trace.rule_code is '规则编码';
comment on column am_archive_rule_trace.rule_type is '规则类型';
comment on column am_archive_rule_trace.matched_flag is '是否命中';
comment on column am_archive_rule_trace.blocking_flag is '是否阻断';
comment on column am_archive_rule_trace.effect_json is '规则效果 JSON';
comment on column am_archive_rule_trace.message is '规则消息';
comment on column am_archive_rule_trace.severity is '严重级别';
comment on column am_archive_rule_trace.skipped_reason is '跳过原因';
comment on column am_archive_rule_trace.created_by is '创建人用户 ID';
comment on column am_archive_rule_trace.created_at is '创建时间';

alter table am_archive_item
    add column governance_scheme_version_id bigint references am_archive_governance_scheme_version (id);
alter table am_archive_volume
    add column governance_scheme_version_id bigint references am_archive_governance_scheme_version (id);

with default_scheme as (
    insert into am_archive_governance_scheme
        (scheme_code, scheme_name, description, enabled, sort_order)
    values
        ('default_governance', '默认治理方案', '承接既有档案分类、字段和规则的默认治理方案', true, 0)
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
        select v.id
        from am_archive_governance_scheme s
        join am_archive_governance_scheme_version v on v.scheme_id = s.id
        where s.scheme_code = 'default_governance'
          and v.version_code = 'v1'
    )
where governance_scheme_version_id is null;

update am_archive_volume
set governance_scheme_version_id = (
        select v.id
        from am_archive_governance_scheme s
        join am_archive_governance_scheme_version v on v.scheme_id = s.id
        where s.scheme_code = 'default_governance'
          and v.version_code = 'v1'
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
    ('archive:governance:manage', '管理档案治理', 'archive', '维护治理方案、本体定义和本地规则');
