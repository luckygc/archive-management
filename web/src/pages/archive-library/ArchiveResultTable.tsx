import { LockOutlined, SettingOutlined } from "@ant-design/icons";
import { Button, Checkbox, Popover, Segmented, Space, Table, Tooltip } from "antd";
import type { ColumnsType, SorterResult, SortOrder } from "antd/es/table/interface";
import type { MouseEvent as ReactMouseEvent, ReactNode, ThHTMLAttributes } from "react";
import { useEffect, useMemo, useState } from "react";

import type {
    ArchiveRecordListDto,
    ArchiveRecordOrderBy,
    ArchiveRecordSortField,
} from "@/shared/types/archive";

type RecordRow = Record<string, unknown>;
type TableDensity = "large" | "middle" | "small";

interface ArchiveResultTableProps {
    result: ArchiveRecordListDto;
    loading?: boolean;
    orderBy?: ArchiveRecordOrderBy[];
    actionColumn?: ReactNode | ((row: RecordRow) => ReactNode);
    showLockColumn?: boolean;
    onOrderChange?: (orderBy: ArchiveRecordOrderBy[]) => void;
}

interface ResultColumn {
    key: string;
    title: ReactNode;
    dataIndex?: string;
    width: number;
    fixed?: "left" | "right";
    render?: (value: unknown, row: RecordRow) => ReactNode;
    sortField?: ArchiveRecordSortField;
    configurable?: boolean;
    defaultVisible?: boolean;
}

interface ResizeHeaderCellProps extends ThHTMLAttributes<HTMLTableCellElement> {
    width?: number;
    onResize?: (width: number) => void;
}

const MIN_COLUMN_WIDTH = 72;

export function ArchiveResultTable({
    actionColumn,
    loading,
    orderBy,
    result,
    showLockColumn,
    onOrderChange,
}: ArchiveResultTableProps) {
    const [density, setDensity] = useState<TableDensity>("middle");
    const baseColumns = useMemo(
        () => buildColumns(result, actionColumn, Boolean(showLockColumn)),
        [actionColumn, result, showLockColumn],
    );
    const configurableColumns = baseColumns.filter((column) => column.configurable !== false);
    const defaultVisibleKeys = useMemo(
        () =>
            baseColumns
                .filter(
                    (column) => column.configurable === false || column.defaultVisible !== false,
                )
                .map((column) => column.key),
        [baseColumns],
    );
    const allColumnKeys = useMemo(() => baseColumns.map((column) => column.key), [baseColumns]);
    const [visibleKeys, setVisibleKeys] = useState<string[]>(defaultVisibleKeys);
    const [columnWidths, setColumnWidths] = useState<Record<string, number>>({});

    useEffect(() => {
        setVisibleKeys((current) => {
            const retained = current.filter((key) => allColumnKeys.includes(key));
            const retainedSet = new Set(retained);
            const addedDefaults = defaultVisibleKeys.filter((key) => !retainedSet.has(key));
            return [...retained, ...addedDefaults];
        });
    }, [allColumnKeys, defaultVisibleKeys]);

    const visibleKeySet = new Set(visibleKeys);
    const tableColumns: ColumnsType<RecordRow> = baseColumns
        .filter((column) => column.configurable === false || visibleKeySet.has(column.key))
        .map((column) => {
            const width = columnWidths[column.key] ?? column.width;
            const firstOrder = orderBy?.[0];
            const sorted = firstOrder?.field === column.sortField ? firstOrder : undefined;
            const sortOrder: SortOrder =
                sorted?.direction === "ASC"
                    ? "ascend"
                    : sorted?.direction === "DESC"
                      ? "descend"
                      : null;
            return {
                title: column.title,
                dataIndex: column.dataIndex,
                key: column.key,
                width,
                fixed: column.fixed,
                render: column.render,
                sorter: Boolean(column.sortField),
                sortOrder,
                onHeaderCell: () =>
                    ({
                        width,
                        onResize:
                            column.fixed === "right"
                                ? undefined
                                : (nextWidth: number) => {
                                      setColumnWidths((current) => ({
                                          ...current,
                                          [column.key]: Math.max(MIN_COLUMN_WIDTH, nextWidth),
                                      }));
                                  },
                    }) as ResizeHeaderCellProps,
            };
        });

    return (
        <div className="am-result-table">
            <div className="am-result-table__toolbar">
                <Space size={8}>
                    <Popover
                        content={
                            <Checkbox.Group
                                options={configurableColumns.map((column) => ({
                                    label: column.title,
                                    value: column.key,
                                }))}
                                value={visibleKeys}
                                onChange={(values) => setVisibleKeys(values.map(String))}
                            />
                        }
                        placement="bottomLeft"
                        trigger="click"
                    >
                        <Button icon={<SettingOutlined />}>列设置</Button>
                    </Popover>
                    <Tooltip title="表格密度">
                        <Segmented<TableDensity>
                            aria-label="表格密度"
                            options={[
                                { label: "宽松", value: "large" },
                                { label: "默认", value: "middle" },
                                { label: "紧凑", value: "small" },
                            ]}
                            value={density}
                            onChange={setDensity}
                        />
                    </Tooltip>
                </Space>
            </div>
            <Table<RecordRow>
                className="am-result-table__table"
                columns={tableColumns}
                components={{
                    header: {
                        cell: ResizeHeaderCell,
                    },
                }}
                dataSource={result.items}
                loading={loading}
                pagination={false}
                rowKey={(row) => String(row.id)}
                scroll={{ x: totalWidth(tableColumns), y: 520 }}
                size={density}
                onChange={(_, __, sorter) => {
                    onOrderChange?.(toOrderBy(sorter));
                }}
            />
        </div>
    );
}

function buildColumns(
    result: ArchiveRecordListDto,
    actionColumn: ArchiveResultTableProps["actionColumn"],
    showLockColumn: boolean,
) {
    const columns: ResultColumn[] = [
        {
            title: "档号",
            dataIndex: "archive_no",
            key: "archive_no",
            width: 150,
            sortField: "archiveNo",
            configurable: true,
        },
        {
            title: "全宗",
            dataIndex: "fonds_name",
            key: "fonds_name",
            width: 160,
            sortField: "fondsCode",
            configurable: true,
        },
        {
            title: "分类",
            dataIndex: "category_name",
            key: "category_name",
            width: 150,
            sortField: "categoryCode",
            configurable: true,
        },
        {
            title: "年度",
            dataIndex: "archive_year",
            key: "archive_year",
            width: 90,
            sortField: "archiveYear",
            configurable: true,
        },
        {
            title: "状态",
            dataIndex: "electronic_status",
            key: "electronic_status",
            width: 120,
            sortField: "electronicStatus",
            configurable: true,
        },
    ];
    for (const field of result.fields.filter((item) => item.listVisible)) {
        columns.push({
            title: field.fieldName,
            dataIndex: field.columnName,
            key: field.fieldCode,
            width: field.listWidth ?? 160,
            sortField: field.exactSearchable ? field.fieldCode : undefined,
            configurable: true,
        });
    }
    if (showLockColumn) {
        columns.push({
            title: "锁定",
            dataIndex: "locked_flag",
            key: "locked_flag",
            width: 90,
            configurable: true,
            render: (value) => (value ? <LockOutlined aria-label="已锁定" /> : null),
        });
    }
    if (actionColumn) {
        columns.push({
            title: "操作",
            key: "actions",
            fixed: "right",
            width: 140,
            configurable: false,
            render: (_, row) =>
                typeof actionColumn === "function" ? actionColumn(row) : actionColumn,
        });
    }
    return columns;
}

function ResizeHeaderCell({ children, onResize, width, ...restProps }: ResizeHeaderCellProps) {
    function startResize(event: ReactMouseEvent<HTMLSpanElement>) {
        if (!width || !onResize) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        const startX = event.clientX;
        const startWidth = width;
        const handleMouseMove = (moveEvent: globalThis.MouseEvent) => {
            onResize(startWidth + moveEvent.clientX - startX);
        };
        const handleMouseUp = () => {
            document.removeEventListener("mousemove", handleMouseMove);
            document.removeEventListener("mouseup", handleMouseUp);
        };
        document.addEventListener("mousemove", handleMouseMove);
        document.addEventListener("mouseup", handleMouseUp);
    }

    return (
        <th {...restProps} style={{ ...restProps.style, width }}>
            {children}
            {onResize ? (
                <span
                    aria-label="调整列宽"
                    className="am-result-table__resize-handle"
                    onClick={(event) => event.stopPropagation()}
                    onMouseDown={startResize}
                    role="separator"
                />
            ) : null}
        </th>
    );
}

function toOrderBy(sorter: SorterResult<RecordRow> | SorterResult<RecordRow>[]) {
    const current = Array.isArray(sorter) ? sorter[0] : sorter;
    const sortField = sortFieldByColumnKey(String(current?.columnKey ?? current?.field ?? ""));
    if (!sortField || !current?.order) {
        return [];
    }
    return [
        {
            field: sortField,
            direction: current.order === "ascend" ? "ASC" : "DESC",
        },
    ] satisfies ArchiveRecordOrderBy[];
}

function sortFieldByColumnKey(columnKey: string): ArchiveRecordSortField | undefined {
    switch (columnKey) {
        case "archive_no":
            return "archiveNo";
        case "archive_year":
            return "archiveYear";
        case "fonds_name":
            return "fondsCode";
        case "category_name":
            return "categoryCode";
        case "electronic_status":
            return "electronicStatus";
        default:
            return columnKey || undefined;
    }
}

function totalWidth(columns: ColumnsType<RecordRow>) {
    return Math.max(
        960,
        columns.reduce((sum, column) => sum + columnWidth(column.width), 0),
    );
}

function columnWidth(width: string | number | undefined) {
    return typeof width === "number" ? width : 160;
}
