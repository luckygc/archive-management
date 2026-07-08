import {
    DeleteOutlined,
    DownloadOutlined,
    EditOutlined,
    EyeOutlined,
    ExportOutlined,
    FileOutlined,
    HistoryOutlined,
    PlusOutlined,
    ReloadOutlined,
    UploadOutlined,
} from "@ant-design/icons";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Button,
    Card,
    Collapse,
    Descriptions,
    Drawer,
    Empty,
    Form,
    Input,
    InputNumber,
    Modal,
    Select,
    Space,
    Table,
    Tabs,
    Typography,
    Upload,
    message,
} from "antd";
import type { UploadProps } from "antd";
import { useEffect, useRef, useState } from "react";

import {
    createArchiveRecord,
    downloadArchiveImportTemplate,
    downloadArchiveItemElectronicFile,
    exportArchiveRecords,
    getArchiveRecord,
    getCurrentUserPermissions,
    importArchiveRecords,
    listArchiveFonds,
    listArchiveItemAudits,
    listArchiveItemElectronicFiles,
    listArchiveCategories,
    listArchiveFields,
    listArchiveRelatedFilterCategories,
    searchArchiveRecords,
    unbindArchiveItemElectronicFile,
    updateArchiveRecord,
    uploadArchiveItemElectronicFile,
} from "@/shared/api/archive";
import type {
    ArchiveCategoryDto,
    ArchiveElectronicStatus,
    ArchiveFieldDto,
    ArchiveImportResult,
    ArchiveItemAuditDto,
    ArchiveItemElectronicFileDto,
    ArchiveRecordDetailDto,
    ArchiveRecordOrderBy,
    CreateArchiveRecordRequest,
    SearchArchiveRecordsQuery,
    UpdateArchiveRecordRequest,
} from "@/shared/types/archive";

import {
    ArchiveAdvancedQueryPanel,
    type ArchiveQueryFormValues,
} from "@/pages/archive-library/ArchiveAdvancedQueryPanel";
import {
    DynamicArchiveFields,
    normalizeArchiveRecordFormValues,
} from "@/pages/archive-library/DynamicArchiveFields";
import { ArchiveResultTable } from "@/pages/archive-library/ArchiveResultTable";
import { toSearchQuery } from "@/pages/archive-library/archiveQuery";

export function ArchiveItemManagementPage() {
    const queryClient = useQueryClient();
    const [form] = Form.useForm<ArchiveQueryFormValues>();
    const [fileForm] = Form.useForm<FileUploadFormValues>();
    const [editorForm] = Form.useForm<RecordEditorFormValues>();
    const [committedQuery, setCommittedQuery] = useState<SearchArchiveRecordsQuery>();
    const [orderBy, setOrderBy] = useState<ArchiveRecordOrderBy[]>([]);
    const [drawerState, setDrawerState] = useState<{
        archiveItemId: number;
        activeKey: "files" | "audits";
    }>();
    const [editorState, setEditorState] = useState<RecordEditorState>();
    const previousCategoryIdRef = useRef<number | undefined>(undefined);
    const categoriesQuery = useQuery({
        queryKey: ["archive-categories", "enabled"],
        queryFn: () => listArchiveCategories(true),
    });
    const fondsQuery = useQuery({
        queryKey: ["archive-fonds", "enabled"],
        queryFn: () => listArchiveFonds(true),
    });
    const currentPermissionsQuery = useQuery({
        queryKey: ["me", "permissions"],
        queryFn: () => getCurrentUserPermissions(),
    });
    const permissionCodes = new Set(currentPermissionsQuery.data?.permissionCodes ?? []);
    const canRead = permissionCodes.has("archive:item:read");
    const canCreate = permissionCodes.has("archive:item:create");
    const canUpdate = permissionCodes.has("archive:item:update");
    const canCreateElectronicFile = canCreate || canUpdate;
    const canDeleteElectronicFile = permissionCodes.has("archive:item:delete");
    const canDownloadFile = permissionCodes.has("archive:item:download-electronic-file");
    const canReadAudit = currentPermissionsQuery.data?.superAdmin === true;
    const canImport = canCreate || canUpdate;
    const canExport = permissionCodes.has("archive:export");
    const categoryId = Form.useWatch("categoryId", form);
    const fieldsQuery = useQuery({
        enabled: typeof categoryId === "number",
        queryKey: ["archive-fields", categoryId, "ITEM"],
        queryFn: () => listArchiveFields(categoryId as number, "ITEM"),
    });
    const relatedCategoriesQuery = useQuery({
        enabled: typeof categoryId === "number",
        queryKey: ["archive-related-filter-categories", categoryId],
        queryFn: () => listArchiveRelatedFilterCategories(categoryId as number),
    });
    const relatedCategories = relatedCategoriesQuery.data?.items ?? [];
    const relatedFieldQueries = useQueries({
        queries: uniqueCategoryIds(relatedCategories).map((id) => ({
            enabled: typeof id === "number",
            queryKey: ["archive-fields", id, "ITEM"],
            queryFn: () => listArchiveFields(id, "ITEM"),
        })),
    });
    const relatedFieldsByCategory = new Map<number, ArchiveFieldDto[]>();
    uniqueCategoryIds(relatedCategories).forEach((id, index) => {
        relatedFieldsByCategory.set(id, relatedFieldQueries[index]?.data?.items ?? []);
    });

    const searchMutation = useMutation({
        mutationFn: (query: SearchArchiveRecordsQuery) => searchArchiveRecords(query),
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "查询失败");
        },
    });
    const downloadTemplateMutation = useMutation({
        mutationFn: async (selectedCategoryId: number) =>
            downloadArchiveImportTemplate(selectedCategoryId),
        onSuccess: (link) => {
            openDownloadLink(link.href);
        },
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "下载模板失败");
        },
    });
    const importMutation = useMutation({
        mutationFn: ({ file, selectedCategoryId }: { file: File; selectedCategoryId: number }) =>
            importArchiveRecords(selectedCategoryId, file),
        onSuccess: (result) => {
            if (result.errors.length > 0) {
                showImportErrors(result);
                return;
            }
            void message.success(`已导入 ${result.importedCount} 条档案`);
            refresh();
        },
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "导入失败");
        },
    });
    const exportMutation = useMutation({
        mutationFn: async (query: SearchArchiveRecordsQuery) => exportArchiveRecords(query),
        onSuccess: (link) => {
            openDownloadLink(link.href);
        },
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "导出失败");
        },
    });
    const selectedArchiveItemId = drawerState?.archiveItemId;
    const filesQuery = useQuery({
        enabled: typeof selectedArchiveItemId === "number" && drawerState?.activeKey === "files",
        queryKey: ["archive-item-electronic-files", selectedArchiveItemId],
        queryFn: () => listArchiveItemElectronicFiles(selectedArchiveItemId as number),
    });
    const auditsQuery = useQuery({
        enabled: typeof selectedArchiveItemId === "number" && drawerState?.activeKey === "audits",
        queryKey: ["archive-item-audits", selectedArchiveItemId],
        queryFn: () =>
            listArchiveItemAudits({
                archiveItemId: selectedArchiveItemId as number,
                limit: 20,
                requestTotal: true,
            }),
    });
    const editorDetailQuery = useQuery({
        enabled:
            typeof editorState?.archiveItemId === "number" &&
            (editorState.mode === "detail" || editorState.mode === "edit"),
        queryKey: ["archive-item-detail", editorState?.archiveItemId, editorState?.mode],
        queryFn: () =>
            getArchiveRecord(
                editorState?.archiveItemId as number,
                editorState?.mode === "detail" ? "DETAIL" : "EDIT",
            ),
    });
    const uploadFileMutation = useMutation({
        mutationFn: ({ file, values }: { file: File; values: FileUploadFormValues }) =>
            uploadArchiveItemElectronicFile(selectedArchiveItemId as number, file, {
                usageType: values.usageType,
                displayOrder: values.displayOrder,
            }),
        onSuccess: () => {
            void message.success("附件已上传");
            fileForm.resetFields();
            void queryClient.invalidateQueries({
                queryKey: ["archive-item-electronic-files", selectedArchiveItemId],
            });
        },
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "上传附件失败");
        },
    });
    const createRecordMutation = useMutation({
        mutationFn: (payload: CreateArchiveRecordRequest) => createArchiveRecord(payload),
        onSuccess: () => {
            void message.success("档案已创建");
            setEditorState(undefined);
            refresh();
        },
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "创建档案失败");
        },
    });
    const updateRecordMutation = useMutation({
        mutationFn: ({ id, payload }: { id: number; payload: UpdateArchiveRecordRequest }) =>
            updateArchiveRecord(id, payload),
        onSuccess: () => {
            void message.success("档案已更新");
            setEditorState(undefined);
            refresh();
        },
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "更新档案失败");
        },
    });
    const unbindFileMutation = useMutation({
        mutationFn: (electronicFileId: number) =>
            unbindArchiveItemElectronicFile(selectedArchiveItemId as number, electronicFileId),
        onSuccess: () => {
            void message.success("文件已解绑");
            void queryClient.invalidateQueries({
                queryKey: ["archive-item-electronic-files", selectedArchiveItemId],
            });
        },
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "解绑文件失败");
        },
    });
    const downloadFileMutation = useMutation({
        mutationFn: (electronicFileId: number) =>
            downloadArchiveItemElectronicFile(selectedArchiveItemId as number, electronicFileId),
        onSuccess: (link) => {
            openDownloadLink(link.href);
        },
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "下载文件失败");
        },
    });
    useEffect(() => {
        if (typeof categoryId !== "number") {
            previousCategoryIdRef.current = undefined;
            return;
        }

        const previousCategoryId = previousCategoryIdRef.current;
        previousCategoryIdRef.current = categoryId;
        if (previousCategoryId === undefined || previousCategoryId === categoryId) {
            return;
        }

        form.resetFields(["conditions", "fondsCode", "keyword", "relatedGroups"]);
        form.setFieldsValue({ categoryId, conditions: [], relatedGroups: [] });
        setCommittedQuery(undefined);
        setOrderBy([]);
        searchMutation.reset();
    }, [categoryId, form, searchMutation]);

    useEffect(() => {
        if (!editorDetailQuery.data || !editorState) {
            return;
        }
        editorForm.setFieldsValue(recordDetailToFormValues(editorDetailQuery.data));
    }, [editorDetailQuery.data, editorForm, editorState]);

    function submit(values: ArchiveQueryFormValues) {
        const query = toSearchQuery(values);
        const managementQuery = { ...query, keyword: undefined };
        setCommittedQuery(managementQuery);
        setOrderBy([]);
        searchMutation.mutate(managementQuery);
    }

    function refresh() {
        if (committedQuery) {
            searchMutation.mutate({
                ...committedQuery,
                orderBy: orderBy.length > 0 ? orderBy : undefined,
            });
            return;
        }
        form.submit();
    }

    function orderResults(nextOrderBy: ArchiveRecordOrderBy[]) {
        if (!committedQuery) {
            return;
        }
        setOrderBy(nextOrderBy);
        searchMutation.mutate({
            ...committedQuery,
            orderBy: nextOrderBy.length > 0 ? nextOrderBy : undefined,
        });
    }

    function reset() {
        form.resetFields();
        setCommittedQuery(undefined);
        setOrderBy([]);
        searchMutation.reset();
    }

    function downloadTemplate() {
        if (typeof categoryId !== "number") {
            void message.warning("请先选择档案分类");
            return;
        }
        downloadTemplateMutation.mutate(categoryId);
    }

    function importFile(option: Parameters<NonNullable<UploadProps["customRequest"]>>[0]) {
        if (typeof categoryId !== "number") {
            option.onError?.(new Error("请先选择档案分类"));
            void message.warning("请先选择档案分类");
            return;
        }
        const file = option.file;
        if (!(file instanceof File)) {
            option.onError?.(new Error("请选择有效文件"));
            return;
        }
        importMutation.mutate(
            { file, selectedCategoryId: categoryId },
            {
                onSuccess: () => option.onSuccess?.("ok"),
                onError: (error) =>
                    option.onError?.(error instanceof Error ? error : new Error("导入失败")),
            },
        );
    }

    function uploadElectronicFile(
        option: Parameters<NonNullable<UploadProps["customRequest"]>>[0],
    ) {
        if (typeof selectedArchiveItemId !== "number") {
            option.onError?.(new Error("请先选择档案"));
            void message.warning("请先选择档案");
            return;
        }
        const file = option.file;
        if (!(file instanceof File)) {
            option.onError?.(new Error("请选择有效文件"));
            return;
        }
        uploadFileMutation.mutate(
            { file, values: fileForm.getFieldsValue() },
            {
                onSuccess: () => option.onSuccess?.("ok"),
                onError: (error) =>
                    option.onError?.(error instanceof Error ? error : new Error("上传附件失败")),
            },
        );
    }

    function exportCurrentQuery() {
        const query =
            committedQuery ??
            ({
                ...toSearchQuery(form.getFieldsValue()),
                keyword: undefined,
            } satisfies SearchArchiveRecordsQuery);
        exportMutation.mutate({
            ...query,
            orderBy: orderBy.length > 0 ? orderBy : undefined,
        });
    }

    function openDrawer(row: Record<string, unknown>, activeKey: "files" | "audits") {
        const archiveItemId = rowId(row);
        if (archiveItemId === undefined) {
            void message.warning("当前行缺少档案 ID");
            return;
        }
        setDrawerState({ archiveItemId, activeKey });
    }

    function openCreateEditor() {
        if (typeof categoryId !== "number") {
            void message.warning("请先选择档案分类");
            return;
        }
        editorForm.resetFields();
        editorForm.setFieldsValue({
            categoryId,
            archiveYear: new Date().getFullYear(),
            electronicStatus: "DRAFT",
            dynamicFields: {},
        });
        setEditorState({ mode: "create" });
    }

    function openRecordEditor(row: Record<string, unknown>, mode: "detail" | "edit") {
        const archiveItemId = rowId(row);
        if (archiveItemId === undefined) {
            void message.warning("当前行缺少档案 ID");
            return;
        }
        editorForm.resetFields();
        setEditorState({ mode, archiveItemId });
    }

    function submitEditor(values: RecordEditorFormValues) {
        if (!editorState || editorState.mode === "detail") {
            return;
        }
        const normalized = normalizeArchiveRecordFormValues(values);
        const commonPayload = {
            volumeId: values.volumeId,
            fondsCode: values.fondsCode as string,
            archiveNo: trimToUndefined(values.archiveNo),
            archiveYear: values.archiveYear,
            electronicStatus: values.electronicStatus,
            securityLevelId: values.securityLevelId,
            retentionPeriodId: values.retentionPeriodId,
            physicalFields: {},
            dynamicFields: normalized.dynamicFields,
        };
        if (editorState.mode === "create") {
            createRecordMutation.mutate({
                ...commonPayload,
                categoryId: values.categoryId as number,
            });
            return;
        }
        updateRecordMutation.mutate({
            id: editorState.archiveItemId,
            payload: commonPayload,
        });
    }

    const result = searchMutation.data;
    const fields = fieldsQuery.data?.items ?? result?.fields ?? [];
    const drawerOpen = Boolean(drawerState);
    const editorOpen = Boolean(editorState);
    const editorFields =
        editorState?.mode === "create" ? fields : (editorDetailQuery.data?.fields ?? []);

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>档案管理</Typography.Title>
                <Space>
                    <Button icon={<ReloadOutlined />} onClick={refresh}>
                        刷新
                    </Button>
                    <Button
                        disabled={typeof categoryId !== "number" || !canImport}
                        icon={<DownloadOutlined />}
                        loading={downloadTemplateMutation.isPending}
                        onClick={downloadTemplate}
                    >
                        导入模板
                    </Button>
                    <Upload
                        accept=".xlsx"
                        customRequest={importFile}
                        maxCount={1}
                        showUploadList={false}
                    >
                        <Button
                            disabled={typeof categoryId !== "number" || !canImport}
                            icon={<UploadOutlined />}
                            loading={importMutation.isPending}
                        >
                            导入
                        </Button>
                    </Upload>
                    <Button
                        disabled={!canExport}
                        icon={<ExportOutlined />}
                        loading={exportMutation.isPending}
                        onClick={exportCurrentQuery}
                    >
                        导出
                    </Button>
                    <Button
                        disabled={!canCreate || typeof categoryId !== "number"}
                        icon={<PlusOutlined />}
                        type="primary"
                        onClick={openCreateEditor}
                    >
                        新建档案
                    </Button>
                </Space>
            </div>
            <Collapse
                className="am-page__filter am-query-collapse"
                defaultActiveKey={["query"]}
                items={[
                    {
                        key: "query",
                        label: "高级筛选条件",
                        children: (
                            <ArchiveAdvancedQueryPanel
                                categories={categoriesQuery.data?.items ?? []}
                                fields={fields}
                                form={form}
                                relatedCategories={relatedCategories}
                                relatedFieldsByCategory={relatedFieldsByCategory}
                                showKeyword={false}
                                submitting={searchMutation.isPending}
                                onReset={reset}
                                onSubmit={submit}
                            />
                        ),
                    },
                ]}
            />
            <Card className="am-page__result">
                {result ? (
                    <ArchiveResultTable
                        actionColumn={(row) => (
                            <Space size={8}>
                                <Button
                                    icon={<EyeOutlined />}
                                    size="small"
                                    type="link"
                                    onClick={() => openRecordEditor(row, "detail")}
                                >
                                    详情
                                </Button>
                                <Button
                                    disabled={!canUpdate}
                                    icon={<EditOutlined />}
                                    size="small"
                                    type="link"
                                    onClick={() => openRecordEditor(row, "edit")}
                                >
                                    编辑
                                </Button>
                                <Button
                                    disabled={!canRead}
                                    icon={<FileOutlined />}
                                    size="small"
                                    type="link"
                                    onClick={() => openDrawer(row, "files")}
                                >
                                    文件
                                </Button>
                                <Button
                                    disabled={!canReadAudit}
                                    icon={<HistoryOutlined />}
                                    size="small"
                                    type="link"
                                    onClick={() => openDrawer(row, "audits")}
                                >
                                    审计
                                </Button>
                            </Space>
                        )}
                        result={result}
                        showLockColumn
                        loading={searchMutation.isPending}
                        orderBy={orderBy}
                        onOrderChange={orderResults}
                    />
                ) : (
                    <Empty description="选择分类并提交高级查询后显示管理列表" />
                )}
            </Card>
            <Drawer
                destroyOnClose
                open={drawerOpen}
                size="large"
                title={drawerState ? `档案 ${drawerState.archiveItemId}` : undefined}
                onClose={() => setDrawerState(undefined)}
            >
                <Tabs
                    activeKey={drawerState?.activeKey}
                    items={[
                        {
                            key: "files",
                            label: "电子文件",
                            children: (
                                <Space direction="vertical" size={16} style={{ width: "100%" }}>
                                    <Form<FileUploadFormValues> form={fileForm} layout="inline">
                                        <Form.Item name="usageType">
                                            <Input
                                                disabled={!canCreateElectronicFile}
                                                placeholder="用途"
                                            />
                                        </Form.Item>
                                        <Form.Item name="displayOrder">
                                            <InputNumber
                                                disabled={!canCreateElectronicFile}
                                                placeholder="顺序"
                                            />
                                        </Form.Item>
                                        <Upload
                                            customRequest={uploadElectronicFile}
                                            maxCount={1}
                                            showUploadList={false}
                                        >
                                            <Button
                                                disabled={!canCreateElectronicFile}
                                                icon={<UploadOutlined />}
                                                loading={uploadFileMutation.isPending}
                                                type="primary"
                                            >
                                                上传附件
                                            </Button>
                                        </Upload>
                                    </Form>
                                    <Table<ArchiveItemElectronicFileDto>
                                        columns={[
                                            {
                                                title: "文件名",
                                                dataIndex: "originalFilename",
                                                key: "originalFilename",
                                            },
                                            {
                                                title: "大小",
                                                dataIndex: "fileSize",
                                                key: "fileSize",
                                                width: 100,
                                                render: (value) => formatFileSize(Number(value)),
                                            },
                                            {
                                                title: "用途",
                                                dataIndex: "usageType",
                                                key: "usageType",
                                                width: 100,
                                            },
                                            {
                                                title: "操作",
                                                key: "actions",
                                                width: 100,
                                                render: (_, row) => (
                                                    <Space size={4}>
                                                        <Button
                                                            disabled={!canDownloadFile}
                                                            icon={<DownloadOutlined />}
                                                            loading={downloadFileMutation.isPending}
                                                            size="small"
                                                            type="link"
                                                            onClick={() =>
                                                                downloadFileMutation.mutate(row.id)
                                                            }
                                                        >
                                                            下载
                                                        </Button>
                                                        <Button
                                                            danger
                                                            disabled={!canDeleteElectronicFile}
                                                            icon={<DeleteOutlined />}
                                                            loading={unbindFileMutation.isPending}
                                                            size="small"
                                                            type="link"
                                                            onClick={() =>
                                                                unbindFileMutation.mutate(row.id)
                                                            }
                                                        >
                                                            解绑
                                                        </Button>
                                                    </Space>
                                                ),
                                            },
                                        ]}
                                        dataSource={filesQuery.data?.items ?? []}
                                        loading={filesQuery.isFetching}
                                        pagination={false}
                                        rowKey="id"
                                    />
                                </Space>
                            ),
                        },
                        {
                            key: "audits",
                            label: "审计记录",
                            children: (
                                <Table<ArchiveItemAuditDto>
                                    columns={[
                                        {
                                            title: "操作",
                                            dataIndex: "operationType",
                                            key: "operationType",
                                            width: 120,
                                        },
                                        {
                                            title: "原因",
                                            dataIndex: "operationReason",
                                            key: "operationReason",
                                        },
                                        {
                                            title: "操作人",
                                            dataIndex: "operatedBy",
                                            key: "operatedBy",
                                            width: 120,
                                        },
                                        {
                                            title: "时间",
                                            dataIndex: "operatedAt",
                                            key: "operatedAt",
                                            width: 180,
                                        },
                                    ]}
                                    dataSource={auditsQuery.data?.items ?? []}
                                    loading={auditsQuery.isFetching}
                                    pagination={false}
                                    rowKey="id"
                                />
                            ),
                        },
                    ]}
                    onChange={(activeKey) =>
                        setDrawerState((current) =>
                            current
                                ? {
                                      ...current,
                                      activeKey: activeKey as "files" | "audits",
                                  }
                                : current,
                        )
                    }
                />
            </Drawer>
            <Drawer
                destroyOnClose
                open={editorOpen}
                size="large"
                title={editorTitle(editorState)}
                onClose={() => setEditorState(undefined)}
            >
                {editorState?.mode === "detail" ? (
                    editorDetailQuery.data ? (
                        <Space direction="vertical" size={16} style={{ width: "100%" }}>
                            <Descriptions
                                bordered
                                column={2}
                                items={recordDescriptionItems(editorDetailQuery.data)}
                                size="small"
                            />
                            <Typography.Title level={5}>动态字段</Typography.Title>
                            <Descriptions
                                bordered
                                column={2}
                                items={dynamicDescriptionItems(editorDetailQuery.data)}
                                size="small"
                            />
                        </Space>
                    ) : (
                        <Empty description="正在加载档案详情" />
                    )
                ) : (
                    <Form<RecordEditorFormValues>
                        form={editorForm}
                        layout="vertical"
                        onFinish={submitEditor}
                    >
                        <Form.Item hidden name="categoryId">
                            <InputNumber />
                        </Form.Item>
                        <Form.Item label="档案分类">
                            <Input
                                disabled
                                value={editorCategoryName(
                                    editorState,
                                    editorDetailQuery.data,
                                    categoriesQuery.data?.items ?? [],
                                    categoryId,
                                )}
                            />
                        </Form.Item>
                        <Form.Item
                            label="全宗"
                            name="fondsCode"
                            rules={[{ required: true, message: "请选择全宗" }]}
                        >
                            <Select
                                disabled={editorState?.mode === "detail"}
                                loading={fondsQuery.isFetching}
                                options={(fondsQuery.data?.items ?? []).map((fonds) => ({
                                    value: fonds.fondsCode,
                                    label: `${fonds.fondsCode} ${fonds.fondsName}`,
                                }))}
                                placeholder="选择全宗"
                                showSearch
                                optionFilterProp="label"
                            />
                        </Form.Item>
                        <Space size={16} style={{ width: "100%" }} wrap>
                            <Form.Item label="档号" name="archiveNo">
                                <Input style={{ width: 240 }} />
                            </Form.Item>
                            <Form.Item label="年度" name="archiveYear">
                                <InputNumber min={1} style={{ width: 160 }} />
                            </Form.Item>
                            <Form.Item label="电子状态" name="electronicStatus">
                                <Select options={electronicStatusOptions} style={{ width: 160 }} />
                            </Form.Item>
                        </Space>
                        <DynamicArchiveFields fields={editorFields} />
                        <Space>
                            <Button onClick={() => setEditorState(undefined)}>取消</Button>
                            <Button
                                htmlType="submit"
                                loading={
                                    createRecordMutation.isPending || updateRecordMutation.isPending
                                }
                                type="primary"
                            >
                                保存
                            </Button>
                        </Space>
                    </Form>
                )}
            </Drawer>
        </section>
    );
}

const electronicStatusOptions: Array<{ value: ArchiveElectronicStatus; label: string }> = [
    { value: "DRAFT", label: "草稿" },
    { value: "ARCHIVED", label: "已归档" },
    { value: "BORROWED", label: "借出" },
];

type RecordEditorState =
    | { mode: "create" }
    | { mode: "detail"; archiveItemId: number }
    | { mode: "edit"; archiveItemId: number };

interface FileUploadFormValues {
    usageType?: string;
    displayOrder?: number;
}

interface RecordEditorFormValues {
    categoryId?: number;
    volumeId?: number;
    fondsCode?: string;
    archiveNo?: string;
    archiveYear?: number;
    electronicStatus?: ArchiveElectronicStatus;
    securityLevelId?: number;
    retentionPeriodId?: number;
    dynamicFields?: Record<string, unknown>;
}

function openDownloadLink(href: string) {
    const anchor = document.createElement("a");
    anchor.href = href;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
}

function editorTitle(state?: RecordEditorState) {
    if (!state) {
        return undefined;
    }
    if (state.mode === "create") {
        return "新建档案";
    }
    return state.mode === "detail"
        ? `档案 ${state.archiveItemId} 详情`
        : `编辑档案 ${state.archiveItemId}`;
}

function recordDetailToFormValues(detail: ArchiveRecordDetailDto): RecordEditorFormValues {
    return {
        categoryId: detail.category.id,
        volumeId: detail.item.volumeId,
        fondsCode: detail.item.fondsCode,
        archiveNo: detail.item.archiveNo,
        archiveYear: detail.item.archiveYear,
        electronicStatus: detail.item.electronicStatus,
        securityLevelId: detail.item.securityLevelId,
        retentionPeriodId: detail.item.retentionPeriodId,
        dynamicFields: detail.dynamicFields ?? {},
    };
}

function recordDescriptionItems(detail: ArchiveRecordDetailDto) {
    return [
        { key: "id", label: "档案 ID", children: detail.item.id },
        { key: "categoryName", label: "档案分类", children: detail.category.categoryName },
        { key: "fondsName", label: "全宗", children: detail.item.fondsName },
        { key: "archiveNo", label: "档号", children: detail.item.archiveNo || "-" },
        { key: "archiveYear", label: "年度", children: detail.item.archiveYear },
        { key: "electronicStatus", label: "电子状态", children: detail.item.electronicStatus },
        { key: "lockedFlag", label: "锁定", children: detail.item.lockedFlag ? "是" : "否" },
        { key: "lockReason", label: "锁定原因", children: detail.item.lockReason || "-" },
    ];
}

function dynamicDescriptionItems(detail: ArchiveRecordDetailDto) {
    const visibleFields = [...detail.fields]
        .filter((field) => field.enabled && field.detailVisible)
        .sort(
            (left, right) =>
                left.detailSortOrder - right.detailSortOrder || left.sortOrder - right.sortOrder,
        );
    if (visibleFields.length === 0) {
        return [{ key: "empty", label: "字段", children: "无可展示字段" }];
    }
    return visibleFields.map((field) => ({
        key: field.fieldCode,
        label: field.fieldName,
        children: formatDisplayValue(detail.dynamicFields?.[field.fieldCode]),
    }));
}

function editorCategoryName(
    state: RecordEditorState | undefined,
    detail: ArchiveRecordDetailDto | undefined,
    categories: ArchiveCategoryDto[],
    currentCategoryId: unknown,
) {
    if (state?.mode === "edit" && detail) {
        return detail.category.categoryName;
    }
    if (typeof currentCategoryId !== "number") {
        return "";
    }
    return categories.find((category) => category.id === currentCategoryId)?.categoryName ?? "";
}

function formatDisplayValue(value: unknown) {
    if (value === null || value === undefined || value === "") {
        return "-";
    }
    return String(value);
}

function trimToUndefined(value?: string) {
    const text = value?.trim();
    return text ? text : undefined;
}

function showImportErrors(result: ArchiveImportResult) {
    Modal.error({
        title: "导入校验未通过",
        width: 640,
        content: (
            <div>
                {result.errors.slice(0, 20).map((error) => (
                    <div key={`${error.rowNumber}-${error.fieldName}-${error.message}`}>
                        第 {error.rowNumber} 行 {error.fieldName}：{error.message}
                    </div>
                ))}
                {result.errors.length > 20 ? (
                    <Typography.Text type="secondary">
                        还有 {result.errors.length - 20} 条错误未显示
                    </Typography.Text>
                ) : null}
            </div>
        ),
    });
}

function uniqueCategoryIds(groups: Array<{ categoryId?: number }>) {
    return [...new Set(groups.map((group) => group.categoryId).filter(isNumber))];
}

function isNumber(value: unknown): value is number {
    return typeof value === "number";
}

function rowId(row: Record<string, unknown>) {
    const value = row.id;
    return typeof value === "number" ? value : undefined;
}

function formatFileSize(size: number) {
    if (!Number.isFinite(size)) {
        return "-";
    }
    if (size < 1024) {
        return `${size} B`;
    }
    if (size < 1024 * 1024) {
        return `${(size / 1024).toFixed(1)} KB`;
    }
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
}
