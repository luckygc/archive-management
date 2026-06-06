comment on table SPRING_SESSION is 'Spring Session 会话主表';
comment on column SPRING_SESSION.PRIMARY_ID is '会话记录主键';
comment on column SPRING_SESSION.SESSION_ID is '对外会话 ID';
comment on column SPRING_SESSION.CREATION_TIME is '会话创建时间，Unix 毫秒';
comment on column SPRING_SESSION.LAST_ACCESS_TIME is '最后访问时间，Unix 毫秒';
comment on column SPRING_SESSION.MAX_INACTIVE_INTERVAL is '最大空闲间隔，单位秒';
comment on column SPRING_SESSION.EXPIRY_TIME is '会话过期时间，Unix 毫秒';
comment on column SPRING_SESSION.PRINCIPAL_NAME is '登录主体名称';

comment on table SPRING_SESSION_ATTRIBUTES is 'Spring Session 会话属性表';
comment on column SPRING_SESSION_ATTRIBUTES.SESSION_PRIMARY_ID is '关联 SPRING_SESSION.PRIMARY_ID';
comment on column SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_NAME is '会话属性名';
comment on column SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_BYTES is '序列化后的会话属性值';

comment on table QRTZ_JOB_DETAILS is 'Quartz 作业定义表';
comment on column QRTZ_JOB_DETAILS.SCHED_NAME is '调度器名称';
comment on column QRTZ_JOB_DETAILS.JOB_NAME is '作业名称';
comment on column QRTZ_JOB_DETAILS.JOB_GROUP is '作业分组';
comment on column QRTZ_JOB_DETAILS.DESCRIPTION is '作业描述';
comment on column QRTZ_JOB_DETAILS.JOB_CLASS_NAME is '作业实现类名';
comment on column QRTZ_JOB_DETAILS.IS_DURABLE is '是否持久作业';
comment on column QRTZ_JOB_DETAILS.IS_NONCONCURRENT is '是否禁止并发执行';
comment on column QRTZ_JOB_DETAILS.IS_UPDATE_DATA is '执行后是否更新 JobDataMap';
comment on column QRTZ_JOB_DETAILS.REQUESTS_RECOVERY is '失败后是否请求恢复执行';
comment on column QRTZ_JOB_DETAILS.JOB_DATA is '作业数据';

comment on table QRTZ_TRIGGERS is 'Quartz 触发器主表';
comment on column QRTZ_TRIGGERS.SCHED_NAME is '调度器名称';
comment on column QRTZ_TRIGGERS.TRIGGER_NAME is '触发器名称';
comment on column QRTZ_TRIGGERS.TRIGGER_GROUP is '触发器分组';
comment on column QRTZ_TRIGGERS.JOB_NAME is '关联作业名称';
comment on column QRTZ_TRIGGERS.JOB_GROUP is '关联作业分组';
comment on column QRTZ_TRIGGERS.DESCRIPTION is '触发器描述';
comment on column QRTZ_TRIGGERS.NEXT_FIRE_TIME is '下次触发时间，Unix 毫秒';
comment on column QRTZ_TRIGGERS.PREV_FIRE_TIME is '上次触发时间，Unix 毫秒';
comment on column QRTZ_TRIGGERS.PRIORITY is '触发器优先级';
comment on column QRTZ_TRIGGERS.TRIGGER_STATE is '触发器状态';
comment on column QRTZ_TRIGGERS.TRIGGER_TYPE is '触发器类型';
comment on column QRTZ_TRIGGERS.START_TIME is '开始时间，Unix 毫秒';
comment on column QRTZ_TRIGGERS.END_TIME is '结束时间，Unix 毫秒';
comment on column QRTZ_TRIGGERS.CALENDAR_NAME is '关联日历名称';
comment on column QRTZ_TRIGGERS.MISFIRE_INSTR is '错过触发处理策略';
comment on column QRTZ_TRIGGERS.JOB_DATA is '触发器作业数据';

comment on table QRTZ_SIMPLE_TRIGGERS is 'Quartz 简单触发器表';
comment on column QRTZ_SIMPLE_TRIGGERS.SCHED_NAME is '调度器名称';
comment on column QRTZ_SIMPLE_TRIGGERS.TRIGGER_NAME is '触发器名称';
comment on column QRTZ_SIMPLE_TRIGGERS.TRIGGER_GROUP is '触发器分组';
comment on column QRTZ_SIMPLE_TRIGGERS.REPEAT_COUNT is '重复次数';
comment on column QRTZ_SIMPLE_TRIGGERS.REPEAT_INTERVAL is '重复间隔，单位毫秒';
comment on column QRTZ_SIMPLE_TRIGGERS.TIMES_TRIGGERED is '已触发次数';

comment on table QRTZ_CRON_TRIGGERS is 'Quartz Cron 触发器表';
comment on column QRTZ_CRON_TRIGGERS.SCHED_NAME is '调度器名称';
comment on column QRTZ_CRON_TRIGGERS.TRIGGER_NAME is '触发器名称';
comment on column QRTZ_CRON_TRIGGERS.TRIGGER_GROUP is '触发器分组';
comment on column QRTZ_CRON_TRIGGERS.CRON_EXPRESSION is 'Cron 表达式';
comment on column QRTZ_CRON_TRIGGERS.TIME_ZONE_ID is '时区 ID';

comment on table QRTZ_SIMPROP_TRIGGERS is 'Quartz 简单属性触发器表';
comment on column QRTZ_SIMPROP_TRIGGERS.SCHED_NAME is '调度器名称';
comment on column QRTZ_SIMPROP_TRIGGERS.TRIGGER_NAME is '触发器名称';
comment on column QRTZ_SIMPROP_TRIGGERS.TRIGGER_GROUP is '触发器分组';
comment on column QRTZ_SIMPROP_TRIGGERS.STR_PROP_1 is '字符串属性 1';
comment on column QRTZ_SIMPROP_TRIGGERS.STR_PROP_2 is '字符串属性 2';
comment on column QRTZ_SIMPROP_TRIGGERS.STR_PROP_3 is '字符串属性 3';
comment on column QRTZ_SIMPROP_TRIGGERS.INT_PROP_1 is '整数属性 1';
comment on column QRTZ_SIMPROP_TRIGGERS.INT_PROP_2 is '整数属性 2';
comment on column QRTZ_SIMPROP_TRIGGERS.LONG_PROP_1 is '长整数属性 1';
comment on column QRTZ_SIMPROP_TRIGGERS.LONG_PROP_2 is '长整数属性 2';
comment on column QRTZ_SIMPROP_TRIGGERS.DEC_PROP_1 is '数值属性 1';
comment on column QRTZ_SIMPROP_TRIGGERS.DEC_PROP_2 is '数值属性 2';
comment on column QRTZ_SIMPROP_TRIGGERS.BOOL_PROP_1 is '布尔属性 1';
comment on column QRTZ_SIMPROP_TRIGGERS.BOOL_PROP_2 is '布尔属性 2';

comment on table QRTZ_BLOB_TRIGGERS is 'Quartz BLOB 触发器表';
comment on column QRTZ_BLOB_TRIGGERS.SCHED_NAME is '调度器名称';
comment on column QRTZ_BLOB_TRIGGERS.TRIGGER_NAME is '触发器名称';
comment on column QRTZ_BLOB_TRIGGERS.TRIGGER_GROUP is '触发器分组';
comment on column QRTZ_BLOB_TRIGGERS.BLOB_DATA is 'BLOB 触发器数据';

comment on table QRTZ_CALENDARS is 'Quartz 日历表';
comment on column QRTZ_CALENDARS.SCHED_NAME is '调度器名称';
comment on column QRTZ_CALENDARS.CALENDAR_NAME is '日历名称';
comment on column QRTZ_CALENDARS.CALENDAR is '序列化日历数据';

comment on table QRTZ_PAUSED_TRIGGER_GRPS is 'Quartz 暂停触发器分组表';
comment on column QRTZ_PAUSED_TRIGGER_GRPS.SCHED_NAME is '调度器名称';
comment on column QRTZ_PAUSED_TRIGGER_GRPS.TRIGGER_GROUP is '触发器分组';

comment on table QRTZ_FIRED_TRIGGERS is 'Quartz 已触发触发器运行表';
comment on column QRTZ_FIRED_TRIGGERS.SCHED_NAME is '调度器名称';
comment on column QRTZ_FIRED_TRIGGERS.ENTRY_ID is '运行记录 ID';
comment on column QRTZ_FIRED_TRIGGERS.TRIGGER_NAME is '触发器名称';
comment on column QRTZ_FIRED_TRIGGERS.TRIGGER_GROUP is '触发器分组';
comment on column QRTZ_FIRED_TRIGGERS.INSTANCE_NAME is '调度器实例名称';
comment on column QRTZ_FIRED_TRIGGERS.FIRED_TIME is '实际触发时间，Unix 毫秒';
comment on column QRTZ_FIRED_TRIGGERS.SCHED_TIME is '计划触发时间，Unix 毫秒';
comment on column QRTZ_FIRED_TRIGGERS.PRIORITY is '触发优先级';
comment on column QRTZ_FIRED_TRIGGERS.STATE is '运行状态';
comment on column QRTZ_FIRED_TRIGGERS.JOB_NAME is '作业名称';
comment on column QRTZ_FIRED_TRIGGERS.JOB_GROUP is '作业分组';
comment on column QRTZ_FIRED_TRIGGERS.IS_NONCONCURRENT is '是否禁止并发执行';
comment on column QRTZ_FIRED_TRIGGERS.REQUESTS_RECOVERY is '是否请求恢复执行';

comment on table QRTZ_SCHEDULER_STATE is 'Quartz 调度器实例状态表';
comment on column QRTZ_SCHEDULER_STATE.SCHED_NAME is '调度器名称';
comment on column QRTZ_SCHEDULER_STATE.INSTANCE_NAME is '调度器实例名称';
comment on column QRTZ_SCHEDULER_STATE.LAST_CHECKIN_TIME is '最后心跳时间，Unix 毫秒';
comment on column QRTZ_SCHEDULER_STATE.CHECKIN_INTERVAL is '心跳间隔，单位毫秒';

comment on table QRTZ_LOCKS is 'Quartz 锁表';
comment on column QRTZ_LOCKS.SCHED_NAME is '调度器名称';
comment on column QRTZ_LOCKS.LOCK_NAME is '锁名称';
