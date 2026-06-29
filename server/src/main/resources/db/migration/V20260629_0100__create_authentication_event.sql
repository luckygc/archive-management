create table am_authentication_event
(
    id                  bigserial primary key,
    event_type          varchar(30)  not null,
    user_id             bigint,
    username            varchar(100),
    display_name        varchar(100),
    session_id          varchar(100),
    operator_user_id    bigint,
    operator_username   varchar(100),
    failure_reason      varchar(500),
    remote_address      varchar(100),
    host                varchar(255),
    forwarded           varchar(1000),
    x_forwarded_for     varchar(1000),
    x_real_ip           varchar(100),
    user_agent          varchar(2000),
    browser_name        varchar(100),
    browser_version     varchar(100),
    os_name             varchar(100),
    os_version          varchar(100),
    device_type         varchar(50),
    occurred_at         timestamp    not null default localtimestamp
);

create index idx_am_authentication_event_event_type on am_authentication_event (event_type);
create index idx_am_authentication_event_username on am_authentication_event (username);
create index idx_am_authentication_event_session_id on am_authentication_event (session_id);
create index idx_am_authentication_event_occurred_at on am_authentication_event (occurred_at desc, id desc);

comment on table am_authentication_event is '认证事件审计表';
comment on column am_authentication_event.id is '主键';
comment on column am_authentication_event.event_type is '认证事件类型：login_success、login_failure、logout、kickout';
comment on column am_authentication_event.user_id is '事件目标用户 ID；登录失败时可为空';
comment on column am_authentication_event.username is '事件目标用户名';
comment on column am_authentication_event.display_name is '事件目标用户显示名';
comment on column am_authentication_event.session_id is '关联 Spring Session ID';
comment on column am_authentication_event.operator_user_id is '操作人用户 ID；管理员踢下线时记录';
comment on column am_authentication_event.operator_username is '操作人用户名；管理员踢下线时记录';
comment on column am_authentication_event.failure_reason is '失败原因或审计补充说明';
comment on column am_authentication_event.remote_address is '请求 remote address';
comment on column am_authentication_event.host is '请求 Host 头';
comment on column am_authentication_event.forwarded is '请求 Forwarded 头';
comment on column am_authentication_event.x_forwarded_for is '请求 X-Forwarded-For 头';
comment on column am_authentication_event.x_real_ip is '请求 X-Real-IP 头';
comment on column am_authentication_event.user_agent is '原始 User-Agent';
comment on column am_authentication_event.browser_name is '浏览器名称摘要';
comment on column am_authentication_event.browser_version is '浏览器版本摘要';
comment on column am_authentication_event.os_name is '操作系统名称摘要';
comment on column am_authentication_event.os_version is '操作系统版本摘要';
comment on column am_authentication_event.device_type is '设备类型摘要';
comment on column am_authentication_event.occurred_at is '事件发生时间';
