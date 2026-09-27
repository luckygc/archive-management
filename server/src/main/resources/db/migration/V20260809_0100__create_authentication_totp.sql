create table am_authentication_totp_credential
(
    user_id            bigint primary key references am_authentication_user (id) on delete cascade,
    encrypted_secret   varchar(1000) not null,
    last_accepted_step bigint        not null,
    version            integer       not null default 0,
    created_at         timestamp     not null default localtimestamp,
    updated_at         timestamp     not null default localtimestamp
);

comment on table am_authentication_totp_credential is '用户 TOTP 凭据表；行存在即表示用户已启用 TOTP';
comment on column am_authentication_totp_credential.user_id is '认证用户 ID，同时作为主键保证每个用户最多一条凭据';
comment on column am_authentication_totp_credential.encrypted_secret is '使用外部 AES-256-GCM 主密钥加密的 TOTP 共享密钥';
comment on column am_authentication_totp_credential.last_accepted_step is '最近一次成功接受的 TOTP 时间步，用于拒绝重放';
comment on column am_authentication_totp_credential.version is '乐观锁版本';
comment on column am_authentication_totp_credential.created_at is '创建时间';
comment on column am_authentication_totp_credential.updated_at is '更新时间';

create table am_authentication_totp_enrollment
(
    token_key        varchar(64) primary key,
    user_id          bigint        not null references am_authentication_user (id) on delete cascade,
    encrypted_secret varchar(1000) not null,
    expires_at       timestamp     not null,
    created_at       timestamp     not null default localtimestamp
);

create unique index uk_am_authentication_totp_enrollment_user_id
    on am_authentication_totp_enrollment (user_id);
create index idx_am_authentication_totp_enrollment_expires_at
    on am_authentication_totp_enrollment (expires_at);

comment on table am_authentication_totp_enrollment is '尚未确认的用户 TOTP enrollment';
comment on column am_authentication_totp_enrollment.token_key is '高熵 enrollment token 的 SHA-256 摘要';
comment on column am_authentication_totp_enrollment.user_id is '准备启用 TOTP 的用户 ID，同一用户最多一个 pending enrollment';
comment on column am_authentication_totp_enrollment.encrypted_secret is '使用外部 AES-256-GCM 主密钥加密的待确认 TOTP 共享密钥';
comment on column am_authentication_totp_enrollment.expires_at is 'enrollment 过期时间';
comment on column am_authentication_totp_enrollment.created_at is '创建时间';

create table am_authentication_totp_login_challenge
(
    token_key       varchar(64) primary key,
    user_id         bigint      not null references am_authentication_user (id) on delete cascade,
    failed_attempts integer     not null default 0,
    expires_at      timestamp   not null,
    created_at      timestamp   not null default localtimestamp,
    constraint ck_am_authentication_totp_login_challenge_failed_attempts
        check (failed_attempts >= 0)
);

create index idx_am_authentication_totp_login_challenge_user_id
    on am_authentication_totp_login_challenge (user_id);
create index idx_am_authentication_totp_login_challenge_expires_at
    on am_authentication_totp_login_challenge (expires_at);

comment on table am_authentication_totp_login_challenge is '密码认证成功后的短时效 TOTP 登录挑战';
comment on column am_authentication_totp_login_challenge.token_key is '高熵 challenge ID 的 SHA-256 摘要';
comment on column am_authentication_totp_login_challenge.user_id is '已通过密码认证的用户 ID';
comment on column am_authentication_totp_login_challenge.failed_attempts is '当前挑战已失败的 TOTP 验证次数';
comment on column am_authentication_totp_login_challenge.expires_at is '挑战过期时间';
comment on column am_authentication_totp_login_challenge.created_at is '创建时间';
