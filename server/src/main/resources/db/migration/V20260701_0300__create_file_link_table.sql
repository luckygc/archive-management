create table am_file_link
(
    id               bigserial primary key,
    code             varchar(64) not null,
    target_type      varchar(50) not null,
    target_parent_id bigint,
    target_id        bigint      not null,
    allowed_user_id  bigint,
    expires_at       timestamp   not null,
    revoked_at       timestamp,
    created_by       bigint,
    created_at       timestamp   not null default localtimestamp
);

create unique index uk_am_file_link_code on am_file_link (code);
create index idx_am_file_link_expires_at on am_file_link (expires_at);
create index idx_am_file_link_allowed_user on am_file_link (allowed_user_id)
    where allowed_user_id is not null;

comment on table am_file_link is '文件短链票据表';
comment on column am_file_link.id is '主键';
comment on column am_file_link.code is '短链短码，不包含业务信息';
comment on column am_file_link.target_type is '短链目标类型';
comment on column am_file_link.target_parent_id is '目标上下文 ID，例如档案条目 ID';
comment on column am_file_link.target_id is '目标 ID，例如存储对象 ID 或档案电子文件 ID';
comment on column am_file_link.allowed_user_id is '允许访问的用户 ID；为空表示公开短链';
comment on column am_file_link.expires_at is '短链过期时间';
comment on column am_file_link.revoked_at is '短链撤销时间';
comment on column am_file_link.created_by is '创建人用户 ID';
comment on column am_file_link.created_at is '创建时间';
