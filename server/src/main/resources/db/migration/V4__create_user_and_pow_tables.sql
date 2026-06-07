create table am_auth_user
(
    id           bigserial primary key,
    username     varchar(100)  not null,
    password     varchar(500)  not null,
    display_name varchar(100)  not null,
    email        varchar(255),
    mobile_phone varchar(50),
    enabled      boolean       not null default true,
    created_at   timestamp     not null default localtimestamp,
    updated_at   timestamp     not null default localtimestamp
);

create unique index uk_am_auth_user_username on am_auth_user (username);
create index idx_am_auth_user_created_at on am_auth_user (created_at);

comment on table am_auth_user is '系统用户表';
comment on column am_auth_user.id is '主键';
comment on column am_auth_user.username is '登录账号';
comment on column am_auth_user.password is '密码密文；使用 Spring Security DelegatingPasswordEncoder 格式';
comment on column am_auth_user.display_name is '显示名称';
comment on column am_auth_user.email is '邮箱';
comment on column am_auth_user.mobile_phone is '手机号';
comment on column am_auth_user.enabled is '是否启用';
comment on column am_auth_user.created_at is '创建时间';
comment on column am_auth_user.updated_at is '更新时间';

create table am_auth_role
(
    id          bigserial primary key,
    role_name   varchar(100) not null,
    description varchar(500),
    enabled     boolean      not null default true,
    created_at  timestamp    not null default localtimestamp,
    updated_at  timestamp    not null default localtimestamp
);

create unique index uk_am_auth_role_name on am_auth_role (role_name);
create index idx_am_auth_role_enabled on am_auth_role (enabled);

comment on table am_auth_role is '系统角色表';
comment on column am_auth_role.id is '主键';
comment on column am_auth_role.role_name is '角色名称；写入 Spring Security 时自动添加 ROLE_ 前缀';
comment on column am_auth_role.description is '角色说明';
comment on column am_auth_role.enabled is '是否启用';
comment on column am_auth_role.created_at is '创建时间';
comment on column am_auth_role.updated_at is '更新时间';

create table am_auth_user_role_rel
(
    id         bigserial primary key,
    user_id    bigint       not null references am_auth_user (id),
    role_id    bigint       not null references am_auth_role (id),
    created_at timestamp    not null default localtimestamp
);

create unique index uk_am_auth_user_role_rel_user_role on am_auth_user_role_rel (user_id, role_id);
create index idx_am_auth_user_role_rel_user_id on am_auth_user_role_rel (user_id);
create index idx_am_auth_user_role_rel_role_id on am_auth_user_role_rel (role_id);

comment on table am_auth_user_role_rel is '系统用户角色关系表';
comment on column am_auth_user_role_rel.id is '主键';
comment on column am_auth_user_role_rel.user_id is '用户 ID';
comment on column am_auth_user_role_rel.role_id is '角色 ID';
comment on column am_auth_user_role_rel.created_at is '创建时间';

create table am_auth_cap_challenge
(
    token           varchar(50) primary key,
    challenge_count integer   not null,
    challenge_size  integer   not null,
    difficulty      integer   not null,
    expires_at      timestamp not null,
    created_at      timestamp not null default localtimestamp
);

create index idx_am_auth_cap_challenge_expires_at on am_auth_cap_challenge (expires_at);

comment on table am_auth_cap_challenge is 'Cap 登录工作量证明挑战表';
comment on column am_auth_cap_challenge.token is 'Cap challenge token，长度 25 字节随机十六进制';
comment on column am_auth_cap_challenge.challenge_count is 'Cap challenge.c，挑战数量';
comment on column am_auth_cap_challenge.challenge_size is 'Cap challenge.s，单个挑战 salt 长度';
comment on column am_auth_cap_challenge.difficulty is 'Cap challenge.d，目标前缀长度';
comment on column am_auth_cap_challenge.expires_at is 'challenge 过期时间';
comment on column am_auth_cap_challenge.created_at is '创建时间';

create table am_auth_cap_token
(
    token_key  varchar(81) primary key,
    expires_at timestamp not null,
    created_at timestamp not null default localtimestamp
);

create index idx_am_auth_cap_token_expires_at on am_auth_cap_token (expires_at);

comment on table am_auth_cap_token is 'Cap 登录工作量证明已兑换令牌表';
comment on column am_auth_cap_token.token_key is 'Cap token key，格式为 id:sha256(vertoken)';
comment on column am_auth_cap_token.expires_at is 'token 过期时间';
comment on column am_auth_cap_token.created_at is '创建时间';

insert into am_auth_user (username, password, display_name)
values ('admin', '{noop}admin', '系统管理员');

insert into am_auth_role (role_name, description)
values
    ('系统管理员', '系统默认管理员角色'),
    ('系统监控', '允许访问系统监控端点');

insert into am_auth_user_role_rel (user_id, role_id)
select auth_user.id, role.id
from am_auth_user auth_user
cross join am_auth_role role
where auth_user.username = 'admin'
  and role.role_name in ('系统管理员', '系统监控');
