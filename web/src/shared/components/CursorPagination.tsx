import { Button, Select, Space } from "antd";

export const CURSOR_PAGE_SIZE_OPTIONS = [
    { label: "100 条", value: 100 },
    { label: "200 条", value: 200 },
    { label: "500 条", value: 500 },
    { label: "1000 条", value: 1000 },
];

export interface CursorPaginationProps {
    limit: number;
    prev?: string | null;
    next?: string | null;
    loading?: boolean;
    onLimitChange: (limit: number) => void;
    onPage: (cursor: string) => void;
}

export function CursorPagination({
    limit,
    prev,
    next,
    loading,
    onLimitChange,
    onPage,
}: CursorPaginationProps) {
    return (
        <Space>
            <Select
                aria-label="每页条数"
                disabled={loading}
                value={limit}
                style={{ width: 112 }}
                options={CURSOR_PAGE_SIZE_OPTIONS}
                onChange={onLimitChange}
            />
            <Button disabled={!prev || loading} onClick={() => prev && onPage(prev)}>
                上一页
            </Button>
            <Button disabled={!next || loading} onClick={() => next && onPage(next)}>
                下一页
            </Button>
        </Space>
    );
}
