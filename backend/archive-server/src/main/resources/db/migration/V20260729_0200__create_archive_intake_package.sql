create table am_archive_intake_package
(
    id                 bigserial primary key,
    package_code       varchar(100),
    format_profile     varchar(50)  not null,
    original_file_name varchar(255) not null,
    content_length     bigint       not null,
    sha256             varchar(64)  not null,
    original_storage_object_id bigint not null references am_storage_object (id),
    status             varchar(20)  not null,
    item_count         integer      not null default 0,
    electronic_file_count integer   not null default 0,
    electronic_file_bytes bigint    not null default 0,
    validation_passed_count integer not null default 0,
    validation_warning_count integer not null default 0,
    validation_manual_count integer not null default 0,
    failure_reason     varchar(1000),
    received_by        bigint       not null,
    reviewed_by        bigint,
    review_remark      varchar(1000),
    source_fixity_confirmed boolean not null default false,
    content_readability_confirmed boolean not null default false,
    antivirus_passed boolean not null default false,
    carrier_safety_confirmed boolean not null default false,
    handover_completed boolean not null default false,
    processing_started_at timestamp,
    processing_completed_at timestamp,
    reviewed_at        timestamp,
    version            integer      not null default 0,
    created_by         bigint,
    created_at         timestamp    not null default localtimestamp,
    updated_by         bigint,
    updated_at         timestamp    not null default localtimestamp,
    constraint ck_am_archive_intake_package_status
        check (status in ('RECEIVED', 'CHECKING', 'PENDING_REVIEW', 'ACCEPTING',
                          'ACCEPTED', 'REJECTED', 'FAILED')),
    constraint ck_am_archive_intake_package_content_length
        check (content_length >= 0),
    constraint ck_am_archive_intake_package_item_count
        check (item_count >= 0),
    constraint ck_am_archive_intake_package_file_count
        check (electronic_file_count >= 0),
    constraint ck_am_archive_intake_package_file_bytes
        check (electronic_file_bytes >= 0),
    constraint ck_am_archive_intake_package_validation_counts
        check (validation_passed_count >= 0
            and validation_warning_count >= 0
            and validation_manual_count >= 0),
    constraint ck_am_archive_intake_package_acceptance
        check (status <> 'ACCEPTED'
            or (source_fixity_confirmed
                and content_readability_confirmed
                and antivirus_passed
                and carrier_safety_confirmed
                and handover_completed
                and reviewed_by is not null
                and reviewed_at is not null))
);

create index idx_am_archive_intake_package_receiver
    on am_archive_intake_package (received_by, created_at desc, id desc);

comment on table am_archive_intake_package is '档案信息包接收记录';
comment on column am_archive_intake_package.package_code is '信息包清单内的业务编码';
comment on column am_archive_intake_package.format_profile is '接收格式配置，例如 DAT93_ITEM';
comment on column am_archive_intake_package.sha256 is '上传 ZIP 的 SHA-256 摘要';
comment on column am_archive_intake_package.original_storage_object_id is '长期保留的原始接收包对象';
comment on column am_archive_intake_package.status is '接收状态：接收、检测、待复核、接收入库、已接收、已退回或失败';
comment on column am_archive_intake_package.failure_reason is '自动检测失败、入库失败或退回原因';
comment on column am_archive_intake_package.review_remark is '人工复核与交接备注';
comment on column am_archive_intake_package.source_fixity_confirmed is '已核验来源固化信息';
comment on column am_archive_intake_package.content_readability_confirmed is '已人工确认内容可读';
comment on column am_archive_intake_package.antivirus_passed is '已使用外部杀毒能力检测通过';
comment on column am_archive_intake_package.carrier_safety_confirmed is '已确认离线载体外观和读取安全';
comment on column am_archive_intake_package.handover_completed is '已完成移交接收登记与交接手续';

create table am_archive_intake_package_item
(
    id                bigserial primary key,
    intake_package_id bigint       not null references am_archive_intake_package (id) on delete cascade,
    archive_item_id   bigint       not null references am_archive_item (id),
    item_order        integer      not null,
    fonds_code        varchar(100) not null,
    category_code     varchar(100) not null,
    archive_no        varchar(255),
    electronic_file_count integer not null default 0,
    created_at        timestamp    not null default localtimestamp,
    constraint uk_am_archive_intake_package_item_order
        unique (intake_package_id, item_order),
    constraint uk_am_archive_intake_package_archive_item
        unique (intake_package_id, archive_item_id),
    constraint ck_am_archive_intake_package_item_order
        check (item_order >= 0)
);

create index idx_am_archive_intake_package_item_package
    on am_archive_intake_package_item (intake_package_id, item_order, id);

comment on table am_archive_intake_package_item is '档案信息包验收后生成的正式馆藏档案关联快照';
comment on column am_archive_intake_package_item.item_order is '清单中的零基顺序';

create table am_archive_intake_package_validation
(
    id                  bigserial primary key,
    intake_package_id   bigint       not null references am_archive_intake_package (id) on delete cascade,
    validation_code     varchar(30)  not null,
    validation_category varchar(20)  not null,
    outcome             varchar(20)  not null,
    message             varchar(500) not null,
    created_at          timestamp    not null default localtimestamp,
    constraint uk_am_archive_intake_package_validation
        unique (intake_package_id, validation_code),
    constraint ck_am_archive_intake_validation_category
        check (validation_category in ('AUTHENTICITY', 'INTEGRITY', 'USABILITY', 'SECURITY')),
    constraint ck_am_archive_intake_validation_outcome
        check (outcome in ('PASSED', 'WARNING', 'MANUAL_REVIEW'))
);

create index idx_am_archive_intake_package_validation_package
    on am_archive_intake_package_validation (intake_package_id, id);

comment on table am_archive_intake_package_validation is 'DA/T 70—2018 移交接收检测结果';
comment on column am_archive_intake_package_validation.validation_code is 'DA/T 70 检测编码或项目补充编码';
