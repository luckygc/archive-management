import { useMutation, useQueries, useQuery } from "@tanstack/react-query";
import { Card, Collapse, Empty, Form, Typography, message } from "antd";
import { useEffect, useRef, useState } from "react";

import {
    discoverArchiveRecords,
    listArchiveCategories,
    listArchiveFields,
    listArchiveRelatedFilterCategories,
} from "@/shared/api/archive";
import type {
    ArchiveFieldDto,
    ArchiveRecordOrderBy,
    SearchArchiveRecordsQuery,
} from "@/shared/types/archive";

import {
    ArchiveAdvancedQueryPanel,
    type ArchiveQueryFormValues,
} from "./ArchiveAdvancedQueryPanel";
import { ArchiveResultTable } from "./ArchiveResultTable";
import { toSearchQuery } from "./archiveQuery";

export function ArchiveLibraryPage() {
    const [form] = Form.useForm<ArchiveQueryFormValues>();
    const [committedQuery, setCommittedQuery] = useState<SearchArchiveRecordsQuery>();
    const [orderBy, setOrderBy] = useState<ArchiveRecordOrderBy[]>([]);
    const previousCategoryIdRef = useRef<number | undefined>(undefined);
    const categoriesQuery = useQuery({
        queryKey: ["archive-categories", "enabled"],
        queryFn: () => listArchiveCategories(true),
    });
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
        mutationFn: (query: SearchArchiveRecordsQuery) => discoverArchiveRecords(query),
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "查询失败");
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
        setCommittedQuery(query);
        setOrderBy([]);
        searchMutation.mutate(query);
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

    const result = searchMutation.data;
    const fields = fieldsQuery.data?.items ?? result?.fields ?? [];

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>档案搜索</Typography.Title>
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
                                showKeyword
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
                        result={result}
                        loading={searchMutation.isPending}
                        orderBy={orderBy}
                        onOrderChange={orderResults}
                    />
                ) : (
                    <Empty description="选择分类并提交高级查询后显示结果" />
                )}
            </Card>
        </section>
    );
}

function uniqueCategoryIds(groups: Array<{ categoryId?: number }>) {
    return [...new Set(groups.map((group) => group.categoryId).filter(isNumber))];
}

function isNumber(value: unknown): value is number {
    return typeof value === "number";
}
