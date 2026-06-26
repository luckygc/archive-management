import type { ArchiveCategoryDto } from "../../shared/types/archive";

export interface CategoryTreeNode extends ArchiveCategoryDto {
    children?: CategoryTreeNode[];
}

export interface CategorySelectNode {
    value: number;
    label: string;
    children?: CategorySelectNode[];
}

export function buildCategoryTree(rows: ArchiveCategoryDto[]) {
    const nodeMap = new Map<number, CategoryTreeNode>();
    const roots: CategoryTreeNode[] = [];
    for (const row of rows) {
        nodeMap.set(row.id, { ...row, children: [] });
    }
    for (const row of rows) {
        const node = nodeMap.get(row.id);
        if (!node) {
            continue;
        }
        const parent = row.parentId ? nodeMap.get(row.parentId) : undefined;
        if (parent) {
            parent.children?.push(node);
        } else {
            roots.push(node);
        }
    }
    return roots;
}

export function buildCategorySelectTree(rows: ArchiveCategoryDto[], editingId?: number) {
    const excludedIds = editingId ? collectDescendantIds(rows, editingId) : new Set<number>();
    const availableRows = rows.filter((row) => !excludedIds.has(row.id));
    return buildCategoryTree(availableRows).map(toSelectNode);
}

function collectDescendantIds(rows: ArchiveCategoryDto[], parentId: number) {
    const childrenByParent = new Map<number, ArchiveCategoryDto[]>();
    for (const row of rows) {
        if (!row.parentId) {
            continue;
        }
        childrenByParent.set(row.parentId, [...(childrenByParent.get(row.parentId) ?? []), row]);
    }
    const ids = new Set<number>();
    const stack = [...(childrenByParent.get(parentId) ?? [])];
    while (stack.length > 0) {
        const current = stack.pop();
        if (!current) {
            continue;
        }
        ids.add(current.id);
        stack.push(...(childrenByParent.get(current.id) ?? []));
    }
    return ids;
}

function toSelectNode(row: CategoryTreeNode): CategorySelectNode {
    return {
        value: row.id,
        label: row.categoryName,
        children: row.children?.length ? row.children.map(toSelectNode) : undefined,
    };
}
