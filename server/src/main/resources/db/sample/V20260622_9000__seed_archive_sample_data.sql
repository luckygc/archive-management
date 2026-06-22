create or replace function seed_archive_field(
    input_category_id bigint,
    input_field_code varchar,
    input_field_name varchar,
    input_field_type varchar,
    input_column_name varchar,
    input_sort_order integer,
    input_list_visible boolean,
    input_exact_searchable boolean,
    input_full_text_searchable boolean,
    input_edit_control varchar default null,
    input_list_width integer default null,
    input_detail_visible boolean default true,
    input_detail_col_span integer default 1,
    input_edit_col_span integer default 1,
    input_list_sort_order integer default null,
    input_detail_sort_order integer default null,
    input_edit_visible boolean default true,
    input_edit_sort_order integer default null
) returns void
language plpgsql
as
$$
begin
    insert into am_archive_field
        (category_id, field_code, field_name, field_type, column_name, list_visible,
         exact_searchable, full_text_searchable, edit_control, list_width, list_sort_order,
         detail_visible, detail_col_span, detail_sort_order, edit_visible, edit_col_span,
         edit_sort_order, enabled, sort_order)
    select input_category_id,
           input_field_code,
           input_field_name,
           input_field_type,
           input_column_name,
           input_list_visible,
           input_exact_searchable,
           input_full_text_searchable,
           coalesce(input_edit_control, case
               when input_field_type in ('INTEGER', 'DECIMAL') then 'NUMBER'
               when input_field_type = 'DATE' then 'DATE'
               when input_field_type = 'DATETIME' then 'DATETIME'
               else 'INPUT'
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
          and field_code = input_field_code
          and deleted_flag = false
    );
end
$$;

create or replace function seed_archive_unique_rule(
    input_category_id bigint,
    input_table_name text,
    input_rule_code varchar,
    input_rule_name varchar,
    input_include_fonds boolean,
    input_field_codes varchar[]
) returns void
language plpgsql
as
$$
declare
    output_rule_id bigint;
    output_index_name varchar;
    output_field_id bigint;
    output_column_name varchar;
    output_index_columns text := '';
    output_field_order integer := 0;
    current_field_code varchar;
begin
    output_index_name := 'uk_seed_archive_' || input_category_id || '_' || input_rule_code;

    insert into am_archive_unique_rule
        (category_id, rule_code, rule_name, include_fonds, index_name, enabled)
    select input_category_id, input_rule_code, input_rule_name, input_include_fonds, output_index_name, true
    where not exists (
        select 1
        from am_archive_unique_rule
        where category_id = input_category_id
          and rule_code = input_rule_code
          and deleted_flag = false
    );

    select id
    into output_rule_id
    from am_archive_unique_rule
    where category_id = input_category_id
      and rule_code = input_rule_code
      and deleted_flag = false;

    delete from am_archive_unique_rule_field
    where rule_id = output_rule_id;

    if input_include_fonds then
        output_index_columns := 'fonds_code';
    end if;

    foreach current_field_code in array input_field_codes loop
        select id, column_name
        into output_field_id, output_column_name
        from am_archive_field
        where category_id = input_category_id
          and field_code = current_field_code
          and deleted_flag = false;

        if output_field_id is null then
            raise exception '示例唯一规则字段不存在：category_id=%, field_code=%',
                input_category_id, current_field_code;
        end if;

        insert into am_archive_unique_rule_field (rule_id, field_id, field_order)
        values (output_rule_id, output_field_id, output_field_order);

        update am_archive_field
        set exact_searchable = true,
            updated_at = localtimestamp
        where id = output_field_id;

        output_index_columns := concat_ws(', ', output_index_columns, output_column_name);
        output_field_order := output_field_order + 1;
    end loop;

    execute format(
        'create unique index if not exists %I on %I (%s) where deleted_flag = false',
        output_index_name,
        input_table_name,
        output_index_columns
    );
end
$$;

create or replace function seed_archive_category(
    input_category_code varchar,
    input_category_name varchar,
    input_sort_order integer
) returns bigint
language plpgsql
as
$$
declare
    output_category_id bigint;
    output_table_name text;
begin
    insert into am_archive_category (category_code, category_name, enabled, sort_order)
    select input_category_code, input_category_name, true, input_sort_order
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

    output_table_name := 'am_archive_data_c_' || output_category_id;

    update am_archive_category
    set record_table_name = output_table_name,
        table_status = 'BUILT',
        built_at = coalesce(built_at, localtimestamp),
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

do
$$
declare
    gw_category_id bigint;
    ht_category_id bigint;
    kj_category_id bigint;
    xm_category_id bigint;
    zp_category_id bigint;
    gw_table text;
    ht_table text;
    kj_table text;
    xm_table text;
    zp_table text;
    record_id bigint;
begin
    insert into am_archive_fonds (fonds_code, fonds_name, enabled, sort_order)
    values
        ('Z000', '集团全宗', true, 10),
        ('Z001', '总部全宗', true, 20),
        ('Z002', '华东分公司全宗', true, 30),
        ('Z003', '华南分公司全宗', true, 40),
        ('Z004', '研发中心全宗', true, 50)
    on conflict do nothing;

    gw_category_id := seed_archive_category('GW', '公文档案', 10);
    ht_category_id := seed_archive_category('HT', '合同档案', 20);
    kj_category_id := seed_archive_category('KJ', '会计凭证', 30);
    xm_category_id := seed_archive_category('XM', '项目档案', 40);
    zp_category_id := seed_archive_category('ZP', '照片档案', 50);

    gw_table := 'am_archive_data_c_' || gw_category_id;
    ht_table := 'am_archive_data_c_' || ht_category_id;
    kj_table := 'am_archive_data_c_' || kj_category_id;
    xm_table := 'am_archive_data_c_' || xm_category_id;
    zp_table := 'am_archive_data_c_' || zp_category_id;

    perform seed_archive_field(gw_category_id, 'title', '题名', 'TEXT', 'f_title', 10, true, true, true, 'INPUT', 280, true, 2, 2);
    perform seed_archive_field(gw_category_id, 'doc_no', '文号', 'TEXT', 'f_doc_no', 20, true, true, true, 'INPUT', 180, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'responsible_org', '责任机构', 'TEXT', 'f_responsible_org', 30, true, true, true, 'INPUT', 160, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'formed_date', '成文日期', 'DATE', 'f_formed_date', 40, true, true, false, 'DATE', 120, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'secret_level', '密级', 'TEXT', 'f_secret_level', 50, true, true, false, 'INPUT', 100, true, 1, 1);
    perform seed_archive_field(gw_category_id, 'summary', '内容摘要', 'TEXT', 'f_summary', 60, false, false, true, 'TEXTAREA', null, true, 2, 2);

    perform seed_archive_field(ht_category_id, 'contract_no', '合同编号', 'TEXT', 'f_contract_no', 10, true, true, true, 'INPUT', 170, true, 1, 1);
    perform seed_archive_field(ht_category_id, 'counterparty', '相对方', 'TEXT', 'f_counterparty', 20, true, true, true, 'INPUT', 240, true, 2, 2);
    perform seed_archive_field(ht_category_id, 'amount', '合同金额', 'DECIMAL', 'f_amount', 30, true, true, false, 'NUMBER', 140, true, 1, 1);
    perform seed_archive_field(ht_category_id, 'sign_date', '签订日期', 'DATE', 'f_sign_date', 40, true, true, false, 'DATE', 120, true, 1, 1);
    perform seed_archive_field(ht_category_id, 'owner_dept', '承办部门', 'TEXT', 'f_owner_dept', 50, true, true, false, 'INPUT', 160, true, 1, 1);
    perform seed_archive_field(ht_category_id, 'contract_scope', '合同范围', 'TEXT', 'f_contract_scope', 60, false, false, true, 'TEXTAREA', null, true, 2, 2);

    perform seed_archive_field(kj_category_id, 'voucher_no', '凭证号', 'TEXT', 'f_voucher_no', 10, true, true, true, 'INPUT', 150, true, 1, 1);
    perform seed_archive_field(kj_category_id, 'accounting_period', '会计期间', 'TEXT', 'f_accounting_period', 20, true, true, true, 'INPUT', 120, true, 1, 1);
    perform seed_archive_field(kj_category_id, 'summary', '摘要', 'TEXT', 'f_summary', 30, true, true, true, 'TEXTAREA', 280, true, 2, 2);
    perform seed_archive_field(kj_category_id, 'amount', '金额', 'DECIMAL', 'f_amount', 40, true, true, false, 'NUMBER', 120, true, 1, 1);
    perform seed_archive_field(kj_category_id, 'attachment_count', '附件张数', 'INTEGER', 'f_attachment_count', 50, true, true, false, 'NUMBER', 100, true, 1, 1);
    perform seed_archive_field(kj_category_id, 'remark', '备注', 'TEXT', 'f_remark', 60, false, false, true, 'TEXTAREA', null, true, 2, 2);

    perform seed_archive_field(xm_category_id, 'project_code', '项目编号', 'TEXT', 'f_project_code', 10, true, true, true, 'INPUT', 150, true, 1, 1);
    perform seed_archive_field(xm_category_id, 'project_name', '项目名称', 'TEXT', 'f_project_name', 20, true, true, true, 'INPUT', 260, true, 2, 2);
    perform seed_archive_field(xm_category_id, 'manager', '负责人', 'TEXT', 'f_manager', 30, true, true, true, 'INPUT', 120, true, 1, 1);
    perform seed_archive_field(xm_category_id, 'start_date', '启动日期', 'DATE', 'f_start_date', 40, true, true, false, 'DATE', 120, true, 1, 1);
    perform seed_archive_field(xm_category_id, 'budget', '预算金额', 'DECIMAL', 'f_budget', 50, true, true, false, 'NUMBER', 140, true, 1, 1);
    perform seed_archive_field(xm_category_id, 'milestone', '关键节点', 'TEXT', 'f_milestone', 60, false, false, true, 'TEXTAREA', null, true, 2, 2);

    perform seed_archive_field(zp_category_id, 'photo_title', '照片题名', 'TEXT', 'f_photo_title', 10, true, true, true, 'INPUT', 260, true, 2, 2);
    perform seed_archive_field(zp_category_id, 'shoot_time', '拍摄时间', 'DATETIME', 'f_shoot_time', 20, true, true, false, 'DATETIME', 170, true, 1, 1);
    perform seed_archive_field(zp_category_id, 'location', '拍摄地点', 'TEXT', 'f_location', 30, true, true, true, 'INPUT', 180, true, 1, 1);
    perform seed_archive_field(zp_category_id, 'photographer', '摄影者', 'TEXT', 'f_photographer', 40, true, true, true, 'INPUT', 120, true, 1, 1);
    perform seed_archive_field(zp_category_id, 'people_count', '人数', 'INTEGER', 'f_people_count', 50, true, true, false, 'NUMBER', 90, true, 1, 1);
    perform seed_archive_field(zp_category_id, 'scene_description', '场景说明', 'TEXT', 'f_scene_description', 60, false, false, true, 'TEXTAREA', null, true, 2, 2);

    execute format(
        'create table if not exists %I (
            id bigint primary key references am_archive_record (id),
            fonds_code varchar(100) not null,
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
    execute format('create index if not exists %I on %I (fonds_code) where deleted_flag = false', 'idx_' || gw_table || '_fonds', gw_table);

    execute format(
        'create table if not exists %I (
            id bigint primary key references am_archive_record (id),
            fonds_code varchar(100) not null,
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
    execute format('create index if not exists %I on %I (fonds_code) where deleted_flag = false', 'idx_' || ht_table || '_fonds', ht_table);

    execute format(
        'create table if not exists %I (
            id bigint primary key references am_archive_record (id),
            fonds_code varchar(100) not null,
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
    execute format('create index if not exists %I on %I (fonds_code) where deleted_flag = false', 'idx_' || kj_table || '_fonds', kj_table);

    execute format(
        'create table if not exists %I (
            id bigint primary key references am_archive_record (id),
            fonds_code varchar(100) not null,
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
    execute format('create index if not exists %I on %I (fonds_code) where deleted_flag = false', 'idx_' || xm_table || '_fonds', xm_table);

    execute format(
        'create table if not exists %I (
            id bigint primary key references am_archive_record (id),
            fonds_code varchar(100) not null,
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
    execute format('create index if not exists %I on %I (fonds_code) where deleted_flag = false', 'idx_' || zp_table || '_fonds', zp_table);

    perform seed_archive_unique_rule(gw_category_id, gw_table, 'doc_no_unique', '同一全宗文号唯一', true, array['doc_no']);
    perform seed_archive_unique_rule(ht_category_id, ht_table, 'contract_no_unique', '同一全宗合同编号唯一', true, array['contract_no']);
    perform seed_archive_unique_rule(kj_category_id, kj_table, 'voucher_period_unique', '同一全宗会计期间凭证号唯一', true, array['accounting_period', 'voucher_no']);
    perform seed_archive_unique_rule(xm_category_id, xm_table, 'project_code_unique', '同一全宗项目编号唯一', true, array['project_code']);
    perform seed_archive_unique_rule(zp_category_id, zp_table, 'photo_title_time_unique', '同一全宗照片题名拍摄时间唯一', true, array['photo_title', 'shoot_time']);

    if not exists (select 1 from am_archive_record where category_code = 'GW' and archive_no = 'GW-2026-001' and deleted_flag = false) then
        insert into am_archive_record (category_code, category_name, archive_no, archive_status, process_status, archive_year)
        values ('GW', '公文档案', 'GW-2026-001', 'DRAFT', 'NONE', 2026)
        returning id into record_id;
        execute format('insert into %I (id, fonds_code, f_title, f_doc_no, f_responsible_org, f_formed_date, f_secret_level, f_summary) values ($1, $2, $3, $4, $5, $6, $7, $8)', gw_table)
        using record_id, 'Z000', '关于年度档案管理工作的通知', '集团办〔2026〕1号', '集团办公室', date '2026-01-12', '内部', '明确年度档案整理、移交、保管和检查工作安排。';
        perform seed_archive_search(record_id, '关于年度档案管理工作的通知 集团办〔2026〕1号 集团办公室 明确年度档案整理、移交、保管和检查工作安排。');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'GW' and archive_no = 'GW-2026-002' and deleted_flag = false) then
        insert into am_archive_record (category_code, category_name, archive_no, archive_status, process_status, archive_year)
        values ('GW', '公文档案', 'GW-2026-002', 'ARCHIVED', 'NONE', 2026)
        returning id into record_id;
        execute format('insert into %I (id, fonds_code, f_title, f_doc_no, f_responsible_org, f_formed_date, f_secret_level, f_summary) values ($1, $2, $3, $4, $5, $6, $7, $8)', gw_table)
        using record_id, 'Z001', '关于上线电子档案借阅流程的批复', '总部办〔2026〕8号', '运营管理部', date '2026-03-06', '公开', '批准在总部和分公司试运行电子档案借阅审批流程。';
        perform seed_archive_search(record_id, '关于上线电子档案借阅流程的批复 总部办〔2026〕8号 运营管理部 电子档案借阅审批流程');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'HT' and archive_no = 'HT-2026-001' and deleted_flag = false) then
        insert into am_archive_record (category_code, category_name, archive_no, archive_status, process_status, archive_year)
        values ('HT', '合同档案', 'HT-2026-001', 'DRAFT', 'NONE', 2026)
        returning id into record_id;
        execute format('insert into %I (id, fonds_code, f_contract_no, f_counterparty, f_amount, f_sign_date, f_owner_dept, f_contract_scope) values ($1, $2, $3, $4, $5, $6, $7, $8)', ht_table)
        using record_id, 'Z001', 'HT-2026-001', '上海示例科技有限公司', 128000.00, date '2026-02-18', '信息技术部', '档案管理系统建设、上线支持和首年运维服务。';
        perform seed_archive_search(record_id, 'HT-2026-001 上海示例科技有限公司 档案管理系统建设 上线支持 首年运维服务');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'HT' and archive_no = 'HT-2026-002' and deleted_flag = false) then
        insert into am_archive_record (category_code, category_name, archive_no, archive_status, process_status, archive_year)
        values ('HT', '合同档案', 'HT-2026-002', 'ARCHIVED', 'NONE', 2026)
        returning id into record_id;
        execute format('insert into %I (id, fonds_code, f_contract_no, f_counterparty, f_amount, f_sign_date, f_owner_dept, f_contract_scope) values ($1, $2, $3, $4, $5, $6, $7, $8)', ht_table)
        using record_id, 'Z003', 'HT-2026-002', '广州南方数据服务有限公司', 86500.00, date '2026-04-09', '华南分公司综合部', '历史档案数字化扫描、目录著录和成果验收服务。';
        perform seed_archive_search(record_id, 'HT-2026-002 广州南方数据服务有限公司 历史档案数字化扫描 目录著录 成果验收');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'KJ' and archive_no = 'KJ-2026-001' and deleted_flag = false) then
        insert into am_archive_record (category_code, category_name, archive_no, archive_status, process_status, archive_year)
        values ('KJ', '会计凭证', 'KJ-2026-001', 'DRAFT', 'NONE', 2026)
        returning id into record_id;
        execute format('insert into %I (id, fonds_code, f_voucher_no, f_accounting_period, f_summary, f_amount, f_attachment_count, f_remark) values ($1, $2, $3, $4, $5, $6, $7, $8)', kj_table)
        using record_id, 'Z002', '记-2026-0001', '2026-01', '支付档案系统建设费用', 56000.00, 8, '含合同、验收单、发票和付款审批单。';
        perform seed_archive_search(record_id, '记-2026-0001 2026-01 支付档案系统建设费用 合同 验收单 发票 付款审批单');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'KJ' and archive_no = 'KJ-2026-002' and deleted_flag = false) then
        insert into am_archive_record (category_code, category_name, archive_no, archive_status, process_status, archive_year)
        values ('KJ', '会计凭证', 'KJ-2026-002', 'ARCHIVED', 'NONE', 2026)
        returning id into record_id;
        execute format('insert into %I (id, fonds_code, f_voucher_no, f_accounting_period, f_summary, f_amount, f_attachment_count, f_remark) values ($1, $2, $3, $4, $5, $6, $7, $8)', kj_table)
        using record_id, 'Z000', '记-2026-0036', '2026-02', '报销档案库房设备维护费', 12800.50, 5, '含维修清单和审批单。';
        perform seed_archive_search(record_id, '记-2026-0036 2026-02 报销档案库房设备维护费 维修清单 审批单');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'XM' and archive_no = 'XM-2026-001' and deleted_flag = false) then
        insert into am_archive_record (category_code, category_name, archive_no, archive_status, process_status, archive_year)
        values ('XM', '项目档案', 'XM-2026-001', 'DRAFT', 'NONE', 2026)
        returning id into record_id;
        execute format('insert into %I (id, fonds_code, f_project_code, f_project_name, f_manager, f_start_date, f_budget, f_milestone) values ($1, $2, $3, $4, $5, $6, $7, $8)', xm_table)
        using record_id, 'Z004', 'PRJ-ARCH-2026', '档案管理平台一期建设', '李明', date '2026-01-20', 350000.00, '完成元数据建模、档案库查询、全文检索和权限边界验证。';
        perform seed_archive_search(record_id, 'PRJ-ARCH-2026 档案管理平台一期建设 李明 元数据建模 档案库查询 全文检索 权限边界验证');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'XM' and archive_no = 'XM-2026-002' and deleted_flag = false) then
        insert into am_archive_record (category_code, category_name, archive_no, archive_status, process_status, archive_year)
        values ('XM', '项目档案', 'XM-2026-002', 'ARCHIVED', 'NONE', 2026)
        returning id into record_id;
        execute format('insert into %I (id, fonds_code, f_project_code, f_project_name, f_manager, f_start_date, f_budget, f_milestone) values ($1, $2, $3, $4, $5, $6, $7, $8)', xm_table)
        using record_id, 'Z002', 'PRJ-DIGI-2026', '华东历史档案数字化专项', '周宁', date '2026-02-14', 215000.00, '完成纸质档案清点、扫描质检、目录挂接和移交验收。';
        perform seed_archive_search(record_id, 'PRJ-DIGI-2026 华东历史档案数字化专项 周宁 纸质档案清点 扫描质检 目录挂接 移交验收');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'ZP' and archive_no = 'ZP-2026-001' and deleted_flag = false) then
        insert into am_archive_record (category_code, category_name, archive_no, archive_status, process_status, archive_year)
        values ('ZP', '照片档案', 'ZP-2026-001', 'DRAFT', 'NONE', 2026)
        returning id into record_id;
        execute format('insert into %I (id, fonds_code, f_photo_title, f_shoot_time, f_location, f_photographer, f_people_count, f_scene_description) values ($1, $2, $3, $4, $5, $6, $7, $8)', zp_table)
        using record_id, 'Z000', '集团档案室新库房启用仪式', timestamp '2026-03-18 09:30:00', '总部档案中心', '王岚', 26, '新库房启用、档案装具验收和库区安全巡检现场。';
        perform seed_archive_search(record_id, '集团档案室新库房启用仪式 总部档案中心 王岚 新库房启用 档案装具验收 库区安全巡检');
    end if;

    if not exists (select 1 from am_archive_record where category_code = 'ZP' and archive_no = 'ZP-2026-002' and deleted_flag = false) then
        insert into am_archive_record (category_code, category_name, archive_no, archive_status, process_status, archive_year)
        values ('ZP', '照片档案', 'ZP-2026-002', 'ARCHIVED', 'NONE', 2026)
        returning id into record_id;
        execute format('insert into %I (id, fonds_code, f_photo_title, f_shoot_time, f_location, f_photographer, f_people_count, f_scene_description) values ($1, $2, $3, $4, $5, $6, $7, $8)', zp_table)
        using record_id, 'Z003', '华南分公司档案培训现场', timestamp '2026-04-22 14:15:00', '广州培训室', '陈洁', 42, '分公司档案员参加电子归档、借阅审批和库房管理培训。';
        perform seed_archive_search(record_id, '华南分公司档案培训现场 广州培训室 陈洁 电子归档 借阅审批 库房管理培训');
    end if;
end
$$;

drop function seed_archive_field(bigint, varchar, varchar, varchar, varchar, integer, boolean, boolean, boolean, varchar, integer, boolean, integer, integer, integer, integer, boolean, integer);
drop function seed_archive_unique_rule(bigint, text, varchar, varchar, boolean, varchar[]);
drop function seed_archive_category(varchar, varchar, integer);
drop function seed_archive_search(bigint, text);
