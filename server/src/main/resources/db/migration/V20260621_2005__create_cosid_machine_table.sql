create table cosid_machine
(
    name            varchar(100) not null primary key,
    namespace       varchar(100) not null,
    machine_id      integer      not null default 0,
    last_timestamp  bigint       not null default 0,
    instance_id     varchar(100) not null default '',
    distribute_time bigint       not null default 0,
    revert_time     bigint       not null default 0
);

create index idx_cosid_machine_namespace on cosid_machine (namespace);
create index idx_cosid_machine_instance_id on cosid_machine (instance_id);

comment on table cosid_machine is 'CosId 机器号分配表';
comment on column cosid_machine.name is '命名空间与机器号组合键';
comment on column cosid_machine.namespace is 'CosId 命名空间';
comment on column cosid_machine.machine_id is '机器号';
comment on column cosid_machine.last_timestamp is '最近心跳时间戳';
comment on column cosid_machine.instance_id is '实例 ID';
comment on column cosid_machine.distribute_time is '分配时间戳';
comment on column cosid_machine.revert_time is '释放时间戳';
