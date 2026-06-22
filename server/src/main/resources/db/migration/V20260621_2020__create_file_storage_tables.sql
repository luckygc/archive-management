create table am_storage_object
(
    id                bigserial primary key,
    storage_type      varchar(20)   not null,
    bucket_name       varchar(255)  not null,
    object_key        varchar(1024) not null,
    original_filename varchar(255)  not null,
    file_size         bigint        not null,
    content_type      varchar(255),
    file_extension    varchar(50),
    checksum_sha256   varchar(64),
    checksum_md5      varchar(32),
    etag              varchar(255),
    created_by        bigint,
    created_at        timestamp     not null default localtimestamp,
    deleted_by        bigint,
    deleted_at        timestamp
);

create index idx_am_storage_object_created_at on am_storage_object (created_at);
create unique index uk_am_storage_object_location_active
    on am_storage_object (storage_type, bucket_name, object_key)
    where deleted_at is null;

comment on table am_storage_object is '文件存储对象信息表';
comment on column am_storage_object.id is '主键';
comment on column am_storage_object.storage_type is '存储类型：local 本地文件，s3 标准 S3，对象存储兼容服务默认类型，minio MinIO，cos 腾讯云 COS，oss 阿里云 OSS，obs 华为云 OBS';
comment on column am_storage_object.bucket_name is '存储桶名称；本地存储使用固定逻辑桶名';
comment on column am_storage_object.object_key is '对象存储键，由 ObjectKeys 生成';
comment on column am_storage_object.original_filename is '上传时原始文件名';
comment on column am_storage_object.file_size is '文件大小，单位字节';
comment on column am_storage_object.content_type is '文件 MIME 类型';
comment on column am_storage_object.file_extension is '文件扩展名';
comment on column am_storage_object.checksum_sha256 is '文件内容 SHA-256 指纹，默认主校验值';
comment on column am_storage_object.checksum_md5 is '文件内容 MD5 指纹，仅用于历史或外部系统兼容';
comment on column am_storage_object.etag is '对象存储返回的 ETag，仅作为存储侧元信息';
comment on column am_storage_object.created_by is '创建人用户 ID';
comment on column am_storage_object.created_at is '创建时间';
comment on column am_storage_object.deleted_by is '删除人用户 ID';
comment on column am_storage_object.deleted_at is '删除时间';
