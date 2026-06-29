create table am_login_failure_limit
(
    username        varchar(100) primary key,
    failure_count   integer      not null,
    lock_level      integer      not null,
    first_failed_at timestamp    not null,
    last_failed_at  timestamp    not null,
    locked_until    timestamp,
    cleanup_after   timestamp    not null,
    created_at      timestamp    not null default localtimestamp,
    updated_at      timestamp    not null default localtimestamp
);

create index idx_am_login_failure_limit_cleanup_after
    on am_login_failure_limit (cleanup_after);

comment on table am_login_failure_limit is '登录失败限制状态表';
comment on column am_login_failure_limit.username is '登录名';
comment on column am_login_failure_limit.failure_count is '当前失败窗口内失败次数';
comment on column am_login_failure_limit.lock_level is '连续触发限制次数，用于指数退避';
comment on column am_login_failure_limit.first_failed_at is '当前失败窗口首次失败时间';
comment on column am_login_failure_limit.last_failed_at is '最近失败时间';
comment on column am_login_failure_limit.locked_until is '锁定截止时间';
comment on column am_login_failure_limit.cleanup_after is '可清理时间';
comment on column am_login_failure_limit.created_at is '创建时间';
comment on column am_login_failure_limit.updated_at is '更新时间';
