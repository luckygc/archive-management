import { DatePicker, Form, Input, InputNumber, Row, Col } from "antd";
import type { FormItemProps } from "antd";
import dayjs from "dayjs";
import type { Dayjs } from "dayjs";
import { z } from "zod";

import type { ArchiveFieldDto } from "@/shared/types/archive";

const archiveRecordFormValuesSchema = z.object({
    dynamicFields: z.record(z.string(), z.unknown()).default({}),
});

export interface DynamicArchiveFieldsProps {
    fields: ArchiveFieldDto[];
    disabled?: boolean;
}

export function DynamicArchiveFields({ fields, disabled = false }: DynamicArchiveFieldsProps) {
    const visibleFields = [...fields]
        .filter((field) => field.enabled && field.editVisible)
        .sort(
            (left, right) =>
                left.editSortOrder - right.editSortOrder || left.sortOrder - right.sortOrder,
        );

    return (
        <Row gutter={16}>
            {visibleFields.map((field) => (
                <Col key={field.id} span={fieldColSpan(field)}>
                    <Form.Item
                        {...fieldValueBridge(field)}
                        label={field.fieldName}
                        name={["dynamicFields", field.fieldCode]}
                    >
                        {renderFieldControl(field, disabled)}
                    </Form.Item>
                </Col>
            ))}
        </Row>
    );
}

export function normalizeArchiveRecordFormValues(values: unknown) {
    return archiveRecordFormValuesSchema.parse(values);
}

function renderFieldControl(field: ArchiveFieldDto, disabled: boolean) {
    if (field.editControl === "textarea") {
        return <Input.TextArea disabled={disabled} maxLength={field.textLength} rows={3} />;
    }

    if (field.editControl === "number") {
        return (
            <InputNumber
                disabled={disabled}
                max={numberLimit(field)}
                precision={field.fieldType === "decimal" ? field.decimalScale : 0}
                style={{ width: "100%" }}
            />
        );
    }

    if (field.editControl === "date" || field.editControl === "datetime") {
        return (
            <DatePicker
                disabled={disabled}
                format={field.editControl === "datetime" ? "YYYY-MM-DD HH:mm:ss" : "YYYY-MM-DD"}
                showTime={field.editControl === "datetime"}
                style={{ width: "100%" }}
            />
        );
    }

    return <Input disabled={disabled} maxLength={field.textLength} />;
}

function fieldValueBridge(
    field: ArchiveFieldDto,
): Pick<FormItemProps, "getValueProps" | "normalize"> {
    if (field.editControl !== "date" && field.editControl !== "datetime") {
        return {};
    }

    const format = field.editControl === "datetime" ? "YYYY-MM-DD HH:mm:ss" : "YYYY-MM-DD";

    return {
        getValueProps: (value: unknown) => ({
            value: typeof value === "string" && value ? dayjs(value, format) : undefined,
        }),
        normalize: (value: Dayjs | null) => (value ? value.format(format) : undefined),
    };
}

function fieldColSpan(field: ArchiveFieldDto) {
    const colSpan = field.editColSpan || 1;
    return Math.min(24, Math.max(8, colSpan * 8));
}

function numberLimit(field: ArchiveFieldDto) {
    if (field.fieldType !== "integer" && field.fieldType !== "decimal") {
        return undefined;
    }

    const precision = field.decimalPrecision ?? 18;
    const scale = field.fieldType === "decimal" ? (field.decimalScale ?? 2) : 0;
    return Number("9".repeat(Math.max(1, precision - scale)));
}
