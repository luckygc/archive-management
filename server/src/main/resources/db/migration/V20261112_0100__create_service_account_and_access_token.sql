create table am_service_account
(
    id            bigserial primary key,
    name          varchar(100) not null,
    description   varchar(500),
    owner_user_id bigint       not null references am_authentication_user (id),
    status        varchar(32)  not null,
    deleted_flag  boolean      not null default false,
    deleted_at    timestamp,
    created_at    timestamp    not null default localtimestamp,
    created_by    bigint,
    updated_at    timestamp    not null default localtimestamp,
    updated_by    bigint,
    constraint ck_am_service_account_status check (status in ('ACTIVE', 'DISABLED'))
);

create unique index uk_am_service_account_name_active
    on am_service_account (name)
    where deleted_flag = false;
create index idx_am_service_account_owner on am_service_account (owner_user_id);
create index idx_am_service_account_status on am_service_account (status, deleted_flag);

comment on table am_service_account is '服务账号；非人类调用主体，通过绑定角色获得功能权限和档案数据范围';
comment on column am_service_account.name is '服务账号名称，未删除时唯一';
comment on column am_service_account.description is '用途说明';
comment on column am_service_account.owner_user_id is '负责人用户 ID，不继承该用户的权限或数据范围';
comment on column am_service_account.status is '状态：ACTIVE、DISABLED';
comment on column am_service_account.deleted_flag is '是否已软删除';
comment on column am_service_account.deleted_at is '软删除时间';
comment on column am_service_account.created_at is '创建时间';
comment on column am_service_account.created_by is '创建人用户 ID';
comment on column am_service_account.updated_at is '更新时间';
comment on column am_service_account.updated_by is '更新人用户 ID';

create table am_authorization_service_account_role
(
    id                 bigserial primary key,
    service_account_id bigint    not null references am_service_account (id),
    role_id            bigint    not null references am_authorization_role (id),
    created_at         timestamp not null default localtimestamp
);

create unique index uk_am_authorization_service_account_role
    on am_authorization_service_account_role (service_account_id, role_id);
create index idx_am_authorization_service_account_role_role
    on am_authorization_service_account_role (role_id);

comment on table am_authorization_service_account_role is '服务账号与授权角色关系；数据范围通过这些角色生效';
comment on column am_authorization_service_account_role.service_account_id is '服务账号 ID';
comment on column am_authorization_service_account_role.role_id is '角色 ID';
comment on column am_authorization_service_account_role.created_at is '创建时间';

create table am_access_token
(
    id                 bigserial primary key,
    token_type         varchar(32)  not null,
    service_account_id bigint references am_service_account (id),
    user_id            bigint references am_authentication_user (id),
    name               varchar(100) not null,
    token_prefix       varchar(16)  not null,
    token_hash         varchar(64)  not null,
    scopes             varchar(2000) not null,
    expires_at         timestamp    not null,
    revoked_at         timestamp,
    last_used_at       timestamp,
    created_at         timestamp    not null default localtimestamp,
    created_by         bigint,
    updated_at         timestamp    not null default localtimestamp,
    updated_by         bigint,
    constraint ck_am_access_token_type check (
        (token_type = 'SERVICE_ACCOUNT' and service_account_id is not null and user_id is null)
            or (token_type = 'USER' and user_id is not null and service_account_id is null))
);

create unique index uk_am_access_token_prefix on am_access_token (token_prefix);
create index idx_am_access_token_service_account on am_access_token (service_account_id, id desc);
create index idx_am_access_token_user on am_access_token (user_id, token_type, id desc);

comment on table am_access_token is '访问令牌；只保存前缀和 SHA-256 哈希，明文仅在签发时返回一次';
comment on column am_access_token.token_type is '令牌类型：SERVICE_ACCOUNT、USER';
comment on column am_access_token.service_account_id is '服务账号令牌所属服务账号';
comment on column am_access_token.user_id is '个人访问令牌所代表的用户';
comment on column am_access_token.name is '令牌名称';
comment on column am_access_token.token_prefix is '令牌明文中的定位前缀';
comment on column am_access_token.token_hash is '完整令牌明文的 SHA-256 十六进制摘要';
comment on column am_access_token.scopes is '逗号分隔的权限点子集';
comment on column am_access_token.expires_at is '过期时间';
comment on column am_access_token.revoked_at is '吊销时间';
comment on column am_access_token.last_used_at is '最后一次通过认证的时间';
comment on column am_access_token.created_at is '创建时间';
comment on column am_access_token.created_by is '创建人用户 ID';
comment on column am_access_token.updated_at is '更新时间';
comment on column am_access_token.updated_by is '更新人用户 ID';

create table am_access_token_call
(
    id                   bigserial primary key,
    token_id             bigint,
    token_prefix         varchar(16),
    caller_type          varchar(32)  not null,
    service_account_id   bigint,
    represented_user_id  bigint,
    http_method          varchar(16)  not null,
    request_path         varchar(512) not null,
    status_code          integer      not null,
    duration_ms          bigint       not null,
    source_ip            varchar(64),
    trace_id             varchar(128),
    failure_reason       varchar(32),
    created_at           timestamp    not null default localtimestamp,
    created_by           bigint,
    constraint ck_am_access_token_call_caller check (caller_type in ('SERVICE_ACCOUNT', 'USER', 'UNKNOWN'))
);

create index idx_am_access_token_call_created on am_access_token_call (created_at desc, id desc);
create index idx_am_access_token_call_service_account on am_access_token_call (service_account_id, id desc);
create index idx_am_access_token_call_token on am_access_token_call (token_id, id desc);

comment on table am_access_token_call is '访问令牌调用审计；不记录请求体、令牌明文或文件正文';
comment on column am_access_token_call.token_id is '已定位到的令牌 ID；无效令牌为空';
comment on column am_access_token_call.token_prefix is '可解析出的令牌前缀';
comment on column am_access_token_call.caller_type is '调用方类型：SERVICE_ACCOUNT、USER、UNKNOWN';
comment on column am_access_token_call.service_account_id is '服务账号 ID';
comment on column am_access_token_call.represented_user_id is '个人访问令牌所代表的用户 ID';
comment on column am_access_token_call.http_method is 'HTTP 方法';
comment on column am_access_token_call.request_path is '请求路径，不含查询串';
comment on column am_access_token_call.status_code is '响应状态码';
comment on column am_access_token_call.duration_ms is '处理耗时毫秒';
comment on column am_access_token_call.source_ip is '服务端看到的来源地址';
comment on column am_access_token_call.trace_id is '请求 traceId';
comment on column am_access_token_call.failure_reason is '认证失败原因；认证成功后的业务失败留空';
comment on column am_access_token_call.created_at is '调用发生时间';
comment on column am_access_token_call.created_by is '个人访问令牌对应的用户 ID；服务账号调用为空';

insert into am_authorization_permission
    (permission_code, permission_name, module_code, description)
values
    ('authentication:service-account:manage', '管理服务账号', 'authentication', '创建、停用和删除服务账号并绑定角色'),
    ('authentication:access-token:manage', '管理访问令牌', 'authentication', '为服务账号签发和吊销访问令牌'),
    ('authentication:api-call:read', '查询调用审计', 'authentication', '查询访问令牌调用审计');

insert into am_authorization_role_permission_rel (role_id, permission_id)
select r.id, p.id
from am_authorization_role r
         join am_authorization_permission p
              on p.permission_code in (
                  'authentication:service-account:manage',
                  'authentication:access-token:manage',
                  'authentication:api-call:read')
where r.role_name = '超级管理员';
