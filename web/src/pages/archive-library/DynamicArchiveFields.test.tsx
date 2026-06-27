import { Button, Form } from "antd";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useEffect, useState } from "react";
import { afterEach, describe, expect, it } from "vite-plus/test";

import type { ArchiveFieldDto } from "@/shared/types/archive";

import { DynamicArchiveFields, normalizeArchiveRecordFormValues } from "./DynamicArchiveFields";

afterEach(() => {
    cleanup();
});

const fields: ArchiveFieldDto[] = [
    createField({ id: 1, fieldCode: "title", fieldName: "题名", editControl: "input" }),
    createField({
        id: 2,
        fieldCode: "amount",
        fieldName: "金额",
        fieldType: "decimal",
        editControl: "number",
    }),
    createField({ id: 3, fieldCode: "remark", fieldName: "备注", editControl: "textarea" }),
];

describe("DynamicArchiveFields", () => {
    it("supports batch assignment and submits dynamicFields by fieldCode", async () => {
        render(
            <DynamicArchiveFieldsHarness
                initialDynamicFields={{
                    amount: 1200.5,
                    remark: "批量回填备注",
                    title: "项目建设审批材料",
                }}
            />,
        );

        await waitFor(() => {
            expect(screen.getByLabelText("题名")).toHaveProperty("value", "项目建设审批材料");
            expect(screen.getByLabelText("金额")).toHaveProperty("value", "1200.50");
            expect(screen.getByLabelText("备注")).toHaveProperty("value", "批量回填备注");
        });

        fireEvent.change(screen.getByLabelText("题名"), {
            target: { value: "项目建设审批材料（修订）" },
        });
        fireEvent.click(screen.getByRole("button", { name: "保存" }));

        await waitFor(() => {
            expect(screen.getByLabelText("提交值").textContent).toContain(
                '"title":"项目建设审批材料（修订）"',
            );
            expect(screen.getByLabelText("提交值").textContent).toContain('"amount":1200.5');
            expect(screen.getByLabelText("提交值").textContent).toContain(
                '"remark":"批量回填备注"',
            );
        });
    });
});

function DynamicArchiveFieldsHarness({
    initialDynamicFields,
}: {
    initialDynamicFields: Record<string, unknown>;
}) {
    const [form] = Form.useForm();
    const [submitted, setSubmitted] = useState("");

    useEffect(() => {
        form.setFieldsValue({ dynamicFields: initialDynamicFields });
    }, [form, initialDynamicFields]);

    return (
        <Form
            form={form}
            layout="vertical"
            onFinish={(values) => {
                setSubmitted(JSON.stringify(normalizeArchiveRecordFormValues(values)));
            }}
        >
            <DynamicArchiveFields fields={fields} />
            <Button aria-label="保存" htmlType="submit">
                保存
            </Button>
            <output aria-label="提交值">{submitted}</output>
        </Form>
    );
}

function createField(field: Partial<ArchiveFieldDto>): ArchiveFieldDto {
    return {
        archiveLevel: "item",
        categoryId: 1,
        columnName: `f_${field.fieldCode ?? "field"}`,
        createdAt: "2026-06-27T00:00:00Z",
        decimalPrecision: 18,
        decimalScale: 2,
        detailColSpan: 1,
        detailSortOrder: field.id ?? 1,
        detailVisible: true,
        editColSpan: 1,
        editControl: "input",
        editSortOrder: field.id ?? 1,
        editVisible: true,
        enabled: true,
        exactSearchable: false,
        fieldCode: "field",
        fieldName: "字段",
        fieldType: "text",
        id: 1,
        listSortOrder: field.id ?? 1,
        listVisible: true,
        sortOrder: field.id ?? 1,
        textLength: 200,
        updatedAt: "2026-06-27T00:00:00Z",
        ...field,
    };
}
