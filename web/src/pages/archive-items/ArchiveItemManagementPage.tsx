import {
    DeleteOutlined,
    DownloadOutlined,
    ExportOutlined,
    FileOutlined,
    HistoryOutlined,
    LinkOutlined,
    PlusOutlined,
    ReloadOutlined,
    UploadOutlined,
} from "@ant-design/icons";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Button,
    Card,
    Collapse,
    Drawer,
    Empty,
    Form,
    Input,
    InputNumber,
    Modal,
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
    bindArchiveItemElectronicFile,
    downloadArchiveImportTemplate,
    downloadArchiveItemElectronicFile,
    exportArchiveRecords,
    getCurrentUserPermissions,
    importArchiveRecords,
    listArchiveItemAudits,
    listArchiveItemElectronicFiles,
    listArchiveCategories,
    listArchiveFields,
    listArchiveRelatedFilterCategories,
    searchArchiveRecords,
    unbindArchiveItemElectronicFile,
} from "@/shared/api/archive";
import type {
    ArchiveFieldDto,
    ArchiveImportResult,
    ArchiveItemAuditDto,
    ArchiveItemElectronicFileDto,
    ArchiveRecordOrderBy,
    SearchArchiveRecordsRequest,
} from "@/shared/types/archive";

import {
    ArchiveAdvancedQueryPanel,
    type ArchiveQueryFormValues,
} from "@/pages/archive-library/ArchiveAdvancedQueryPanel";
import { ArchiveResultTable } from "@/pages/archive-library/ArchiveResultTable";
import { toSearchQuery } from "@/pages/archive-library/archiveQuery";

export function ArchiveItemManagementPage() {
    const queryClient = useQueryClient();
    const [form] = Form.useForm<ArchiveQueryFormValues>();
    const [fileForm] = Form.useForm<FileBindFormValues>();
    const [committedQuery, setCommittedQuery] = useState<SearchArchiveRecordsRequest>();
    const [orderBy, setOrderBy] = useState<ArchiveRecordOrderBy[]>([]);
    const [drawerState, setDrawerState] = useState<{
        archiveItemId: number;
        activeKey: "files" | "audits";
    }>();
    const previousCategoryIdRef = useRef<number | undefined>(undefined);
    const categoriesQuery = useQuery({
        queryKey: ["archive-categories", "enabled"],
        queryFn: () => listArchiveCategories(true),
    });
    const currentPermissionsQuery = useQuery({
        queryKey: ["me", "permissions"],
        queryFn: () => getCurrentUserPermissions(),
    });
    const permissionCodes = new Set(currentPermissionsQuery.data?.permissionCodes ?? []);
    const canRead = permissionCodes.has("archive:item:read");
    const canCreate = permissionCodes.has("archive:item:create");
    const canUpdate = permissionCodes.has("archive:item:update");
    const canBindFile = permissionCodes.has("archive:file:bind");
    const canDownloadFile = permissionCodes.has("archive:file:download");
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
        mutationFn: (query: SearchArchiveRecordsRequest) => searchArchiveRecords(query),
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
        mutationFn: async (query: SearchArchiveRecordsRequest) => exportArchiveRecords(query),
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
    const bindFileMutation = useMutation({
        mutationFn: (values: FileBindFormValues) =>
            bindArchiveItemElectronicFile(selectedArchiveItemId as number, {
                storageObjectId: values.storageObjectId as number,
                usageType: values.usageType,
                displayOrder: values.displayOrder,
            }),
        onSuccess: () => {
            void message.success("文件已绑定");
            fileForm.resetFields();
            void queryClient.invalidateQueries({
                queryKey: ["archive-item-electronic-files", selectedArchiveItemId],
            });
        },
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "绑定文件失败");
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

    function exportCurrentQuery() {
        const query =
            committedQuery ??
            ({
                ...toSearchQuery(form.getFieldsValue()),
                keyword: undefined,
            } satisfies SearchArchiveRecordsRequest);
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

    const result = searchMutation.data;
    const fields = fieldsQuery.data?.items ?? result?.fields ?? [];
    const drawerOpen = Boolean(drawerState);

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
                    <Button disabled={!canCreate} icon={<PlusOutlined />} type="primary">
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
                                <Button size="small" type="link">
                                    详情
                                </Button>
                                <Button disabled={!canUpdate} size="small" type="link">
                                    编辑
                                </Button>
                                <Button
                                    icon={<FileOutlined />}
                                    size="small"
                                    type="link"
                                    onClick={() => openDrawer(row, "files")}
                                >
                                    文件
                                </Button>
                                <Button
                                    disabled={!canRead}
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
                                    <Form<FileBindFormValues>
                                        form={fileForm}
                                        layout="inline"
                                        onFinish={(values) => bindFileMutation.mutate(values)}
                                    >
                                        <Form.Item
                                            name="storageObjectId"
                                            rules={[
                                                {
                                                    required: true,
                                                    message: "请选择文件记录",
                                                },
                                            ]}
                                        >
                                            <InputNumber
                                                disabled={!canBindFile}
                                                min={1}
                                                placeholder="文件记录 ID"
                                            />
                                        </Form.Item>
                                        <Form.Item name="usageType">
                                            <Input disabled={!canBindFile} placeholder="用途" />
                                        </Form.Item>
                                        <Form.Item name="displayOrder">
                                            <InputNumber
                                                disabled={!canBindFile}
                                                placeholder="顺序"
                                            />
                                        </Form.Item>
                                        <Button
                                            disabled={!canBindFile}
                                            htmlType="submit"
                                            icon={<LinkOutlined />}
                                            loading={bindFileMutation.isPending}
                                            type="primary"
                                        >
                                            绑定
                                        </Button>
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
                                                width: 150,
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
                                                            disabled={!canBindFile}
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
        </section>
    );
}

interface FileBindFormValues {
    storageObjectId?: number;
    usageType?: string;
    displayOrder?: number;
}

function openDownloadLink(href: string) {
    const anchor = document.createElement("a");
    anchor.href = href;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
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
