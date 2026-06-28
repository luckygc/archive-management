create or replace function seed_archive_assert_identifier(input_identifier text) returns void
language plpgsql
as
$$
begin
    if input_identifier is null or input_identifier !~ '^[a-z][a-z0-9_]*$' then
        raise exception '示例数据包含非法 SQL 标识符：%', input_identifier;
    end if;
end
$$;

create or replace function seed_archive_stable_identifier(
    input_prefix text,
    input_stable_key text
) returns text
language plpgsql
as
$$
declare
    normalized_prefix text := lower(trim(input_prefix));
    readable text;
    direct_name text;
    hash_suffix text;
    readable_limit integer;
begin
    perform seed_archive_assert_identifier(normalized_prefix || 'x');
    readable := lower(trim(coalesce(input_stable_key, '')));
    readable := regexp_replace(readable, '[^a-z0-9_]+', '_', 'g');
    readable := regexp_replace(readable, '_+', '_', 'g');
    readable := trim(both '_' from readable);
    if readable = '' then
        readable := 'key';
    end if;
    if readable !~ '^[a-z]' then
        readable := 'k_' || readable;
    end if;

    hash_suffix := substring(md5(trim(coalesce(input_stable_key, ''))) from 1 for 12);
    readable_limit := 63 - length(normalized_prefix) - 1 - length(hash_suffix);
    if readable_limit <= 0 then
        raise exception '示例数据 SQL 标识符前缀过长：%', input_prefix;
    end if;
    readable := trim(trailing '_' from substring(readable from 1 for readable_limit));
    if readable = '' then
        readable := 'key';
    end if;
    return normalized_prefix || readable || '_' || hash_suffix;
end
$$;

create or replace function seed_archive_field(
    input_category_id bigint,
    input_archive_level varchar,
    input_field_code varchar,
    input_field_name varchar,
    input_field_type varchar,
    input_column_name varchar,
    input_sort_order integer,
    input_list_visible boolean,
    input_exact_searchable boolean,
    input_edit_control varchar default null,
    input_list_width integer default null,
    input_detail_visible boolean default true,
    input_detail_col_span integer default 1,
    input_edit_col_span integer default 1,
    input_list_sort_order integer default null,
    input_detail_sort_order integer default null,
    input_edit_visible boolean default true,
    input_edit_sort_order integer default null,
    input_field_scope varchar default 'metadata'
) returns void
language plpgsql
as
$$
begin
    insert into am_archive_field
        (category_id, archive_level, field_scope, field_code, field_name, field_type, column_name, list_visible,
         exact_searchable, edit_control, list_width, list_sort_order,
         detail_visible, detail_col_span, detail_sort_order, edit_visible, edit_col_span,
         edit_sort_order, enabled, sort_order)
    select input_category_id,
           input_archive_level,
           input_field_scope,
           input_field_code,
           input_field_name,
           input_field_type,
           input_column_name,
           input_list_visible,
           input_exact_searchable,
           coalesce(input_edit_control, case
               when input_field_type in ('integer', 'decimal') then 'number'
               when input_field_type = 'date' then 'date'
               when input_field_type = 'datetime' then 'datetime'
               else 'input'
           end),
           input_list_width,
           coalesce(input_list_sort_order, input_sort_order),
           input_detail_visible,
           input_detail_col_span,
           coalesce(input_detail_sort_order, input_sort_order),
           input_edit_visible,
           input_edit_col_span,
           coalesce(input_edit_sort_order, input_sort_order),
           true,
           input_sort_order
    where not exists (
        select 1
        from am_archive_field
        where category_id = input_category_id
          and archive_level = input_archive_level
          and field_scope = input_field_scope
          and field_code = input_field_code
          and deleted_flag = false
    );
end
$$;

create or replace function seed_archive_unique_constraint(
    input_category_id bigint,
    input_archive_level varchar,
    input_table_name text,
    input_constraint_code varchar,
    input_constraint_name varchar,
    input_field_codes varchar[]
) returns void
language plpgsql
as
$$
declare
    output_constraint_id bigint;
    output_index_name varchar;
    output_field_id bigint;
    output_column_name varchar;
    output_category_code varchar;
    output_index_columns text[] := array[]::text[];
    output_field_order integer := 0;
    current_field_code varchar;
begin
    select category_code
    into output_category_code
    from am_archive_category
    where id = input_category_id
      and deleted_flag = false;

    output_index_name := seed_archive_stable_identifier(
        'uk_am_archive_constraint_',
        output_category_code || '_' || lower(input_archive_level) || '_' || input_constraint_code
    );
    perform seed_archive_assert_identifier(output_index_name);
    perform seed_archive_assert_identifier(input_table_name);

    insert into am_archive_unique_constraint
        (category_id, archive_level, constraint_code, constraint_name, index_name, enabled)
    select input_category_id, input_archive_level, input_constraint_code, input_constraint_name, output_index_name, true
    where not exists (
        select 1
        from am_archive_unique_constraint
        where category_id = input_category_id
          and archive_level = input_archive_level
          and constraint_code = input_constraint_code
          and deleted_flag = false
    );

    select id
    into output_constraint_id
    from am_archive_unique_constraint
    where category_id = input_category_id
      and archive_level = input_archive_level
      and constraint_code = input_constraint_code
      and deleted_flag = false;

    delete from am_archive_unique_constraint_field
    where constraint_id = output_constraint_id;

    foreach current_field_code in array input_field_codes loop
        select id, column_name
        into output_field_id, output_column_name
        from am_archive_field
        where category_id = input_category_id
          and archive_level = input_archive_level
          and field_scope = 'metadata'
          and field_code = current_field_code
          and deleted_flag = false;

        if output_field_id is null then
            raise exception '示例唯一约束字段不存在：category_id=%, field_code=%',
                input_category_id, current_field_code;
        end if;

        insert into am_archive_unique_constraint_field (constraint_id, field_id, field_order)
        values (output_constraint_id, output_field_id, output_field_order);

        update am_archive_field
        set exact_searchable = true,
            updated_at = localtimestamp
        where id = output_field_id;

        perform seed_archive_assert_identifier(output_column_name);
        output_index_columns := output_index_columns || output_column_name;
        output_field_order := output_field_order + 1;
    end loop;

    execute format('drop index if exists %s', output_index_name);
    execute format(
        'create unique index %s on %s (%s) where deleted_flag = false',
        output_index_name,
        input_table_name,
        (select string_agg(column_name, ', ') from unnest(output_index_columns) as column_name)
    );
end
$$;

create or replace function seed_archive_category(
    input_category_code varchar,
    input_category_name varchar,
    input_parent_code varchar,
    input_management_mode varchar,
    input_build_table boolean,
    input_sort_order integer
) returns bigint
language plpgsql
as
$$
declare
    output_category_id bigint;
    output_parent_id bigint;
    output_table_name text;
begin
    if input_parent_code is not null then
        select id
        into output_parent_id
        from am_archive_category
        where category_code = input_parent_code
          and deleted_flag = false;

        if output_parent_id is null then
            raise exception '示例父分类不存在：category_code=%', input_parent_code;
        end if;
    end if;

    insert into am_archive_category (parent_id, category_code, category_name, management_mode, enabled, sort_order)
    select output_parent_id, input_category_code, input_category_name, input_management_mode, true, input_sort_order
    where not exists (
        select 1
        from am_archive_category
        where category_code = input_category_code
          and deleted_flag = false
    );

    select id
    into output_category_id
    from am_archive_category
    where category_code = input_category_code
      and deleted_flag = false;

    output_table_name := seed_archive_stable_identifier('am_archive_record_item_', input_category_code);

    update am_archive_category
    set parent_id = output_parent_id,
        category_name = input_category_name,
        management_mode = input_management_mode,
        item_table_name = case when input_build_table then output_table_name else null end,
        table_status = case when input_build_table then 'built' else 'not_built' end,
        built_at = case when input_build_table then coalesce(built_at, localtimestamp) else null end,
        updated_at = localtimestamp
    where id = output_category_id;

    return output_category_id;
end
$$;

create or replace function seed_archive_search(
    input_record_id bigint,
    input_search_text text
) returns void
language plpgsql
as
$$
begin
    insert into am_archive_record_search
        (archive_record_id, search_text, index_version)
    values
        (input_record_id, input_search_text, 1)
    on conflict (archive_record_id)
    do update set search_text = excluded.search_text,
                  updated_at = localtimestamp;
end
$$;

create or replace function seed_archive_record_id() returns bigint
language plpgsql
as
$$
declare
    next_id bigint;
begin
    select coalesce(max(id), 0) + 1
    into next_id
    from am_archive_record;
    return next_id;
end
$$;

do
$$
declare
    ws_category_id bigint;
    zy_category_id bigint;
    mt_category_id bigint;
    gw_category_id bigint;
    ht_category_id bigint;
    kj_category_id bigint;
    xm_category_id bigint;
    zp_category_id bigint;
    gw_table text;
    gw_volume_table text;
    gw_physical_table text;
    gw_volume_physical_table text;
    ht_table text;
    ht_physical_table text;
    kj_table text;
    xm_table text;
    zp_table text;
    record_id bigint;
    gw_volume_record_id bigint;
begin
    insert into am_archive_fonds (fonds_code, fonds_name, enabled, sort_order)
    values
        ('Z000', '集团全宗', true, 10),
        ('Z001', '总部全宗', true, 20),
        ('Z002', '华东分公司全宗', true, 30),
        ('Z003', '华南分公司全宗', true, 40),
        ('Z004', '研发中心全宗', true, 50)
    on conflict do nothing;

    ws_category_id := seed_archive_category('WS', '文书档案', null, 'item_only', false, 10);
    zy_category_id := seed_archive_category('ZY', '专业档案', null, 'item_only', false, 20);
    mt_category_id := seed_archive_category('MT', '声像档案', null, 'item_only', false, 30);
    gw_category_id := seed_archive_category('GW', '公文档案', 'WS', 'volume_item', true, 10);
    ht_category_id := seed_archive_category('HT', '合同档案', 'WS', 'item_only', true, 20);
    kj_category_id := seed_archive_category('KJ', '会计凭证', 'ZY', 'item_only', true, 10);
    xm_category_id := seed_archive_category('XM', '项目档案', 'ZY', 'item_only', true, 20);
    zp_category_id := seed_archive_category('ZP', '照片档案', 'MT', 'item_only', true, 10);

    gw_table := seed_archive_stable_identifier('am_archive_record_item_', 'GW');
    gw_volume_table := seed_archive_stable_identifier('am_archive_record_volume_', 'GW');
    gw_physical_table := seed_archive_stable_identifier('am_archive_record_item_physical_', 'GW');
    gw_volume_physical_table := seed_archive_stable_identifier('am_archive_record_volume_physical_', 'GW');
    ht_table := seed_archive_stable_identifier('am_archive_record_item_', 'HT');
    ht_physical_table := seed_archive_stable_identifier('am_archive_record_item_physical_', 'HT');
    kj_table := seed_archive_stable_identifier('am_archive_record_item_', 'KJ');
    xm_table := seed_archive_stable_identifier('am_archive_record_item_', 'XM');
    zp_table := seed_archive_stable_identifier('am_archive_record_item_', 'ZP');
    perform seed_archive_assert_identifier(gw_table);
    perform seed_archive_assert_identifier(gw_volume_table);
    perform seed_archive_assert_identifier(gw_physical_table);
    perform seed_archive_assert_identifier(gw_volume_physical_table);
    perform seed_archive_assert_identifier(ht_table);
    perform seed_archive_assert_identifier(ht_physical_table);
    perform seed_archive_assert_identifier(kj_table);
    perform seed_archive_assert_identifier(xm_table);
    perform seed_archive_assert_identifier(zp_table);

    perform seed_archive_field(gw_category_id, 'item', 'title', '题名', 'text', 'f_title', 10, true, true, 'input', 280, true, 2, 2);
    perform seed_archive_field(gw_category_id, 'item', 'doc_no', '文号', 'text', 'f_doc_no', 20, true, true, 'input', 180, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'item', 'responsible_org', '责任机构', 'text', 'f_responsible_org', 30, true, true, 'input', 160, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'item', 'formed_date', '成文日期', 'date', 'f_formed_date', 40, true, true, 'date', 120, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'item', 'secret_level', '密级', 'text', 'f_secret_level', 50, true, true, 'input', 100, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'item', 'summary', '内容摘要', 'text', 'f_summary', 60, false, false, 'textarea', null, true, 2, 2);
    perform seed_archive_field(gw_category_id, 'volume', 'volume_no', '案卷号', 'text', 'f_volume_no', 10, true, true, 'input', 150, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'volume', 'volume_title', '案卷题名', 'text', 'f_volume_title', 20, true, true, 'input', 280, true, 2, 2);
    perform seed_archive_field(gw_category_id, 'volume', 'start_date', '起始日期', 'date', 'f_start_date', 30, true, true, 'date', 120, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'volume', 'end_date', '终止日期', 'date', 'f_end_date', 40, true, true, 'date', 120, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'volume', 'retention_period', '保管期限', 'text', 'f_retention_period', 50, true, true, 'input', 120, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'item', 'box_no', '盒号', 'text', 'f_box_no', 10, true, true, 'input', 120, true, 1, 1, null, null, true, null, 'physical');
    perform seed_archive_field(gw_category_id, 'item', 'item_location_no', '卷内库位号', 'text', 'f_item_location_no', 20, true, true, 'input', 160, true, 1, 1, null, null, true, null, 'physical');
    perform seed_archive_field(gw_category_id, 'volume', 'volume_box_no', '案卷盒号', 'text', 'f_volume_box_no', 10, true, true, 'input', 120, true, 1, 1, null, null, true, null, 'physical');
    perform seed_archive_field(gw_category_id, 'volume', 'shelf_no', '案卷架位号', 'text', 'f_shelf_no', 20, true, true, 'input', 160, true, 1, 1, null, null, true, null, 'physical');

    perform seed_archive_field(ht_category_id, 'item', 'contract_no', '合同编号', 'text', 'f_contract_no', 10, true, true, 'input', 170, true, 1, 1);
    perform seed_archive_field(ht_category_id, 'item', 'counterparty', '相对方', 'text', 'f_counterparty', 20, true, true, 'input', 240, true, 2, 2);
    perform seed_archive_field(ht_category_id, 'item', 'amount', '合同金额', 'decimal', 'f_amount', 30, true, true, 'number', 140, true, 1, 1);
    perform seed_archive_field(ht_category_id, 'item', 'sign_date', '签订日期', 'date', 'f_sign_date', 40, true, true, 'date', 120, true, 1, 1);
    perform seed_archive_field(ht_category_id, 'item', 'owner_dept', '承办部门', 'text', 'f_owner_dept', 50, true, true, 'input', 160, true, 1, 1);
    perform seed_archive_field(ht_category_id, 'item', 'contract_scope', '合同范围', 'text', 'f_contract_scope', 60, false, false, 'textarea', null, true, 2, 2);
    perform seed_archive_field(ht_category_id, 'item', 'storage_box_no', '合同盒号', 'text', 'f_storage_box_no', 10, true, true, 'input', 120, true, 1, 1, null, null, true, null, 'physical');
    perform seed_archive_field(ht_category_id, 'item', 'location_no', '合同库位号', 'text', 'f_location_no', 20, true, true, 'input', 160, true, 1, 1, null, null, true, null, 'physical');

    perform seed_archive_field(kj_category_id, 'item', 'voucher_no', '凭证号', 'text', 'f_voucher_no', 10, true, true, 'input', 150, true, 1, 1);
    perform seed_archive_field(kj_category_id, 'item', 'accounting_period', '会计期间', 'text', 'f_accounting_period', 20, true, true, 'input', 120, true, 1, 1);
    perform seed_archive_field(kj_category_id, 'item', 'summary', '摘要', 'text', 'f_summary', 30, true, true, 'textarea', 280, true, 2, 2);
    perform seed_archive_field(kj_category_id, 'item', 'amount', '金额', 'decimal', 'f_amount', 40, true, true, 'number', 120, true, 1, 1);
    perform seed_archive_field(kj_category_id, 'item', 'attachment_count', '附件张数', 'integer', 'f_attachment_count', 50, true, true, 'number', 100, true, 1, 1);
    perform seed_archive_field(kj_category_id, 'item', 'remark', '备注', 'text', 'f_remark', 60, false, false, 'textarea', null, true, 2, 2);

    perform seed_archive_field(xm_category_id, 'item', 'project_code', '项目编号', 'text', 'f_project_code', 10, true, true, 'input', 150, true, 1, 1);
    perform seed_archive_field(xm_category_id, 'item', 'project_name', '项目名称', 'text', 'f_project_name', 20, true, true, 'input', 260, true, 2, 2);
    perform seed_archive_field(xm_category_id, 'item', 'manager', '负责人', 'text', 'f_manager', 30, true, true, 'input', 120, true, 1, 1);
    perform seed_archive_field(xm_category_id, 'item', 'start_date', '启动日期', 'date', 'f_start_date', 40, true, true, 'date', 120, true, 1, 1);
    perform seed_archive_field(xm_category_id, 'item', 'budget', '预算金额', 'decimal', 'f_budget', 50, true, true, 'number', 140, true, 1, 1);
    perform seed_archive_field(xm_category_id, 'item', 'milestone', '关键节点', 'text', 'f_milestone', 60, false, false, 'textarea', null, true, 2, 2);

    perform seed_archive_field(zp_category_id, 'item', 'photo_title', '照片题名', 'text', 'f_photo_title', 10, true, true, 'input', 260, true, 2, 2);
    perform seed_archive_field(zp_category_id, 'item', 'shoot_time', '拍摄时间', 'datetime', 'f_shoot_time', 20, true, true, 'datetime', 170, true, 1, 1);
    perform seed_archive_field(zp_category_id, 'item', 'location', '拍摄地点', 'text', 'f_location', 30, true, true, 'input', 180, true, 1, 1);
    perform seed_archive_field(zp_category_id, 'item', 'photographer', '摄影者', 'text', 'f_photographer', 40, true, true, 'input', 120, true, 1, 1);
    perform seed_archive_field(zp_category_id, 'item', 'people_count', '人数', 'integer', 'f_people_count', 50, true, true, 'number', 90, true, 1, 1);
    perform seed_archive_field(zp_category_id, 'item', 'scene_description', '场景说明', 'text', 'f_scene_description', 60, false, false, 'textarea', null, true, 2, 2);

    update am_archive_category
    set volume_table_name = gw_volume_table,
        item_physical_table_name = gw_physical_table,
        volume_physical_table_name = gw_volume_physical_table,
        updated_at = localtimestamp
    where id = gw_category_id;

    update am_archive_category
    set item_physical_table_name = ht_physical_table,
        updated_at = localtimestamp
    where id = ht_category_id;

    execute format(
        'create table if not exists %s (
            id bigint primary key references am_archive_record (id),
            f_volume_no varchar(500),
            f_volume_title varchar(500),
            f_start_date date,
            f_end_date date,
            f_retention_period varchar(500),
            deleted_flag boolean not null default false,
            created_at timestamp not null default localtimestamp,
            updated_at timestamp not null default localtimestamp
        )',
        gw_volume_table
    );
    execute format(
        'create table if not exists %s (
            id bigint primary key references am_archive_record (id),
            f_volume_box_no varchar(500),
            f_shelf_no varchar(500),
            deleted_flag boolean not null default false,
            created_at timestamp not null default localtimestamp,
            updated_at timestamp not null default localtimestamp
        )',
        gw_volume_physical_table
    );

    execute format(
        'create table if not exists %s (
            id bigint primary key references am_archive_record (id),
            f_title varchar(500),
            f_doc_no varchar(500),
            f_responsible_org varchar(500),
            f_formed_date date,
            f_secret_level varchar(500),
            f_summary varchar(500),
            deleted_flag boolean not null default false,
            created_at timestamp not null default localtimestamp,
            updated_at timestamp not null default localtimestamp
        )',
        gw_table
    );
    execute format(
        'create table if not exists %s (
            id bigint primary key references am_archive_record (id),
            f_box_no varchar(500),
            f_item_location_no varchar(500),
            deleted_flag boolean not null default false,
            created_at timestamp not null default localtimestamp,
            updated_at timestamp not null default localtimestamp
        )',
        gw_physical_table
    );
    execute format(
        'create table if not exists %s (
            id bigint primary key references am_archive_record (id),
            f_contract_no varchar(500),
            f_counterparty varchar(500),
            f_amount numeric(18, 2),
            f_sign_date date,
            f_owner_dept varchar(500),
            f_contract_scope varchar(500),
            deleted_flag boolean not null default false,
            created_at timestamp not null default localtimestamp,
            updated_at timestamp not null default localtimestamp
        )',
        ht_table
    );
    execute format(
        'create table if not exists %s (
            id bigint primary key references am_archive_record (id),
            f_storage_box_no varchar(500),
            f_location_no varchar(500),
            deleted_flag boolean not null default false,
            created_at timestamp not null default localtimestamp,
            updated_at timestamp not null default localtimestamp
        )',
        ht_physical_table
    );
    execute format(
        'create table if not exists %s (
            id bigint primary key references am_archive_record (id),
            f_voucher_no varchar(500),
            f_accounting_period varchar(500),
            f_summary varchar(500),
            f_amount numeric(18, 2),
            f_attachment_count integer,
            f_remark varchar(500),
            deleted_flag boolean not null default false,
            created_at timestamp not null default localtimestamp,
            updated_at timestamp not null default localtimestamp
        )',
        kj_table
    );
    execute format(
        'create table if not exists %s (
            id bigint primary key references am_archive_record (id),
            f_project_code varchar(500),
            f_project_name varchar(500),
            f_manager varchar(500),
            f_start_date date,
            f_budget numeric(18, 2),
            f_milestone varchar(500),
            deleted_flag boolean not null default false,
            created_at timestamp not null default localtimestamp,
            updated_at timestamp not null default localtimestamp
        )',
        xm_table
    );
    execute format(
        'create table if not exists %s (
            id bigint primary key references am_archive_record (id),
            f_photo_title varchar(500),
            f_shoot_time timestamp,
            f_location varchar(500),
            f_photographer varchar(500),
            f_people_count integer,
            f_scene_description varchar(500),
            deleted_flag boolean not null default false,
            created_at timestamp not null default localtimestamp,
            updated_at timestamp not null default localtimestamp
        )',
        zp_table
    );
    perform seed_archive_unique_constraint(gw_category_id, 'item', gw_table, 'doc_no_unique', '文号唯一', array['doc_no']);
    perform seed_archive_unique_constraint(gw_category_id, 'volume', gw_volume_table, 'volume_no_unique', '案卷号唯一', array['volume_no']);
    perform seed_archive_unique_constraint(ht_category_id, 'item', ht_table, 'owner_dept_contract_no_unique', '部门合同编号唯一', array['owner_dept', 'contract_no']);
    perform seed_archive_unique_constraint(kj_category_id, 'item', kj_table, 'voucher_period_unique', '会计期间凭证号唯一', array['accounting_period', 'voucher_no']);
    perform seed_archive_unique_constraint(xm_category_id, 'item', xm_table, 'project_code_unique', '项目编号唯一', array['project_code']);
    perform seed_archive_unique_constraint(zp_category_id, 'item', zp_table, 'photo_title_time_unique', '照片题名拍摄时间唯一', array['photo_title', 'shoot_time']);

    if not exists (select 1 from am_archive_record where category_code = 'GW' and archive_no = 'GW-2026-AV-001' and deleted_flag = false) then
        insert into am_archive_record (id, archive_level, fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year)
        values (seed_archive_record_id(), 'volume', 'Z000', '集团全宗', 'GW', '公文档案', 'GW-2026-AV-001', 'ARCHIVED', 2026)
        returning id into gw_volume_record_id;
        execute format('insert into %s (id, f_volume_no, f_volume_title, f_start_date, f_end_date, f_retention_period) values ($1, $2, $3, $4, $5, $6)', gw_volume_table)
        using gw_volume_record_id, 'AV-2026-001', '2026 年档案管理制度与流程案卷', date '2026-01-01', date '2026-12-31', '长期';
        execute format('insert into %s (id, f_volume_box_no, f_shelf_no) values ($1, $2, $3)', gw_volume_physical_table)
        using gw_volume_record_id, 'GW-J-001', 'A1-00-01';
    else
        select id
        into gw_volume_record_id
        from am_archive_record
        where category_code = 'GW'
          and archive_no = 'GW-2026-AV-001'
          and deleted_flag = false;
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'GW' and archive_no = 'GW-2026-001' and deleted_flag = false) then
        insert into am_archive_record (id, archive_level, parent_id, fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year, display_order)
        values (seed_archive_record_id(), 'item', gw_volume_record_id, 'Z000', '集团全宗', 'GW', '公文档案', 'GW-2026-001', 'DRAFT', 2026, 10)
        returning id into record_id;
        execute format('insert into %s (id, f_title, f_doc_no, f_responsible_org, f_formed_date, f_secret_level, f_summary) values ($1, $2, $3, $4, $5, $6, $7)', gw_table)
        using record_id, '关于年度档案管理工作的通知', '集团办〔2026〕1号', '集团办公室', date '2026-01-12', '内部', '明确年度档案整理、移交、保管和检查工作安排。';
        execute format('insert into %s (id, f_box_no, f_item_location_no) values ($1, $2, $3)', gw_physical_table)
        using record_id, 'GW-H-001', 'A1-01-01';
        perform seed_archive_search(record_id, '关于年度档案管理工作的通知 集团办〔2026〕1号 集团办公室 明确年度档案整理、移交、保管和检查工作安排。');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'GW' and archive_no = 'GW-2026-002' and deleted_flag = false) then
        insert into am_archive_record (id, archive_level, parent_id, fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year, display_order)
        values (seed_archive_record_id(), 'item', gw_volume_record_id, 'Z001', '总部全宗', 'GW', '公文档案', 'GW-2026-002', 'ARCHIVED', 2026, 20)
        returning id into record_id;
        execute format('insert into %s (id, f_title, f_doc_no, f_responsible_org, f_formed_date, f_secret_level, f_summary) values ($1, $2, $3, $4, $5, $6, $7)', gw_table)
        using record_id, '关于上线电子档案借阅流程的批复', '总部办〔2026〕8号', '运营管理部', date '2026-03-06', '公开', '批准在总部和分公司试运行电子档案借阅流程。';
        execute format('insert into %s (id, f_box_no, f_item_location_no) values ($1, $2, $3)', gw_physical_table)
        using record_id, 'GW-H-002', 'A1-01-02';
        perform seed_archive_search(record_id, '关于上线电子档案借阅流程的批复 总部办〔2026〕8号 运营管理部 电子档案借阅审批流程');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'HT' and archive_no = 'HT-2026-001' and deleted_flag = false) then
        insert into am_archive_record (id, archive_level, fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year)
        values (seed_archive_record_id(), 'item', 'Z001', '总部全宗', 'HT', '合同档案', 'HT-2026-001', 'DRAFT', 2026)
        returning id into record_id;
        execute format('insert into %s (id, f_contract_no, f_counterparty, f_amount, f_sign_date, f_owner_dept, f_contract_scope) values ($1, $2, $3, $4, $5, $6, $7)', ht_table)
        using record_id, 'HT-2026-001', '上海示例科技有限公司', 128000.00, date '2026-02-18', '信息技术部', '档案管理系统建设、上线支持和首年运维服务。';
        execute format('insert into %s (id, f_storage_box_no, f_location_no) values ($1, $2, $3)', ht_physical_table)
        using record_id, 'HT-H-001', 'B2-03-01';
        perform seed_archive_search(record_id, 'HT-2026-001 上海示例科技有限公司 档案管理系统建设 上线支持 首年运维服务');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'HT' and archive_no = 'HT-2026-002' and deleted_flag = false) then
        insert into am_archive_record (id, archive_level, fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year)
        values (seed_archive_record_id(), 'item', 'Z003', '华南分公司全宗', 'HT', '合同档案', 'HT-2026-002', 'ARCHIVED', 2026)
        returning id into record_id;
        execute format('insert into %s (id, f_contract_no, f_counterparty, f_amount, f_sign_date, f_owner_dept, f_contract_scope) values ($1, $2, $3, $4, $5, $6, $7)', ht_table)
        using record_id, 'HT-2026-002', '广州南方数据服务有限公司', 86500.00, date '2026-04-09', '华南分公司综合部', '历史档案数字化扫描、目录著录和成果验收服务。';
        execute format('insert into %s (id, f_storage_box_no, f_location_no) values ($1, $2, $3)', ht_physical_table)
        using record_id, 'HT-H-002', 'B2-03-02';
        perform seed_archive_search(record_id, 'HT-2026-002 广州南方数据服务有限公司 历史档案数字化扫描 目录著录 成果验收');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'KJ' and archive_no = 'KJ-2026-001' and deleted_flag = false) then
        insert into am_archive_record (id, archive_level, fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year)
        values (seed_archive_record_id(), 'item', 'Z002', '华东分公司全宗', 'KJ', '会计凭证', 'KJ-2026-001', 'DRAFT', 2026)
        returning id into record_id;
        execute format('insert into %s (id, f_voucher_no, f_accounting_period, f_summary, f_amount, f_attachment_count, f_remark) values ($1, $2, $3, $4, $5, $6, $7)', kj_table)
        using record_id, '记-2026-0001', '2026-01', '支付档案系统建设费用', 56000.00, 8, '含合同、验收单、发票和付款审批单。';
        perform seed_archive_search(record_id, '记-2026-0001 2026-01 支付档案系统建设费用 合同 验收单 发票 付款审批单');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'KJ' and archive_no = 'KJ-2026-002' and deleted_flag = false) then
        insert into am_archive_record (id, archive_level, fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year)
        values (seed_archive_record_id(), 'item', 'Z000', '集团全宗', 'KJ', '会计凭证', 'KJ-2026-002', 'ARCHIVED', 2026)
        returning id into record_id;
        execute format('insert into %s (id, f_voucher_no, f_accounting_period, f_summary, f_amount, f_attachment_count, f_remark) values ($1, $2, $3, $4, $5, $6, $7)', kj_table)
        using record_id, '记-2026-0036', '2026-02', '报销档案库房设备维护费', 12800.50, 5, '含维修清单和审批单。';
        perform seed_archive_search(record_id, '记-2026-0036 2026-02 报销档案库房设备维护费 维修清单 审批单');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'XM' and archive_no = 'XM-2026-001' and deleted_flag = false) then
        insert into am_archive_record (id, archive_level, fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year)
        values (seed_archive_record_id(), 'item', 'Z004', '研发中心全宗', 'XM', '项目档案', 'XM-2026-001', 'DRAFT', 2026)
        returning id into record_id;
        execute format('insert into %s (id, f_project_code, f_project_name, f_manager, f_start_date, f_budget, f_milestone) values ($1, $2, $3, $4, $5, $6, $7)', xm_table)
        using record_id, 'PRJ-ARCH-2026', '档案管理平台一期建设', '李明', date '2026-01-20', 350000.00, '完成元数据建模、档案库查询、全文检索和权限边界验证。';
        perform seed_archive_search(record_id, 'PRJ-ARCH-2026 档案管理平台一期建设 李明 元数据建模 档案库查询 全文检索 权限边界验证');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'XM' and archive_no = 'XM-2026-002' and deleted_flag = false) then
        insert into am_archive_record (id, archive_level, fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year)
        values (seed_archive_record_id(), 'item', 'Z002', '华东分公司全宗', 'XM', '项目档案', 'XM-2026-002', 'ARCHIVED', 2026)
        returning id into record_id;
        execute format('insert into %s (id, f_project_code, f_project_name, f_manager, f_start_date, f_budget, f_milestone) values ($1, $2, $3, $4, $5, $6, $7)', xm_table)
        using record_id, 'PRJ-DIGI-2026', '华东历史档案数字化专项', '周宁', date '2026-02-14', 215000.00, '完成纸质档案清点、扫描质检、目录挂接和移交验收。';
        perform seed_archive_search(record_id, 'PRJ-DIGI-2026 华东历史档案数字化专项 周宁 纸质档案清点 扫描质检 目录挂接 移交验收');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'ZP' and archive_no = 'ZP-2026-001' and deleted_flag = false) then
        insert into am_archive_record (id, archive_level, fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year)
        values (seed_archive_record_id(), 'item', 'Z000', '集团全宗', 'ZP', '照片档案', 'ZP-2026-001', 'DRAFT', 2026)
        returning id into record_id;
        execute format('insert into %s (id, f_photo_title, f_shoot_time, f_location, f_photographer, f_people_count, f_scene_description) values ($1, $2, $3, $4, $5, $6, $7)', zp_table)
        using record_id, '集团档案室新库房启用仪式', timestamp '2026-03-18 09:30:00', '总部档案中心', '王岚', 26, '新库房启用、档案装具验收和库区安全巡检现场。';
        perform seed_archive_search(record_id, '集团档案室新库房启用仪式 总部档案中心 王岚 新库房启用 档案装具验收 库区安全巡检');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'ZP' and archive_no = 'ZP-2026-002' and deleted_flag = false) then
        insert into am_archive_record (id, archive_level, fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year)
        values (seed_archive_record_id(), 'item', 'Z003', '华南分公司全宗', 'ZP', '照片档案', 'ZP-2026-002', 'ARCHIVED', 2026)
        returning id into record_id;
        execute format('insert into %s (id, f_photo_title, f_shoot_time, f_location, f_photographer, f_people_count, f_scene_description) values ($1, $2, $3, $4, $5, $6, $7)', zp_table)
        using record_id, '华南分公司档案培训现场', timestamp '2026-04-22 14:15:00', '广州培训室', '陈洁', 42, '分公司档案员参加电子归档、借阅审批和库房管理培训。';
        perform seed_archive_search(record_id, '华南分公司档案培训现场 广州培训室 陈洁 电子归档 借阅审批 库房管理培训');
    end if;
end
$$;

drop function seed_archive_field(bigint, varchar, varchar, varchar, varchar, varchar, integer, boolean, boolean, varchar, integer, boolean, integer, integer, integer, integer, boolean, integer, varchar);
drop function seed_archive_unique_constraint(bigint, varchar, text, varchar, varchar, varchar[]);
drop function seed_archive_category(varchar, varchar, varchar, varchar, boolean, integer);
drop function seed_archive_search(bigint, text);
drop function seed_archive_record_id();
drop function seed_archive_stable_identifier(text, text);
drop function seed_archive_assert_identifier(text);
