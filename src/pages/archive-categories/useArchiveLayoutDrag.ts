import { combine } from "@atlaskit/pragmatic-drag-and-drop/combine";
import {
    draggable,
    dropTargetForElements,
} from "@atlaskit/pragmatic-drag-and-drop/element/adapter";
import type { CleanupFn } from "@atlaskit/pragmatic-drag-and-drop/types";
import {
    attachInstruction,
    extractInstruction,
} from "@atlaskit/pragmatic-drag-and-drop-hitbox/list-item";
import type { ObjectDirective, Ref } from "vue";

import type { ArchiveFieldLayoutItemDto, ArchiveLayoutSurface } from "../../shared/types/archive";

type LayoutDragData = Record<string | symbol, unknown> & {
    type: "archive-layout-item";
    fieldId: number;
};

const layoutDragCleanupKey = Symbol("layoutDragCleanup");

type LayoutDragElement = HTMLElement & {
    [layoutDragCleanupKey]?: CleanupFn;
};

export function useArchiveLayoutDrag(
    layoutItems: Ref<ArchiveFieldLayoutItemDto[]>,
    activeLayoutSurface: Ref<ArchiveLayoutSurface>,
    draggingLayoutFieldId: Ref<number | undefined>,
) {
    const vLayoutDrag: ObjectDirective<LayoutDragElement, ArchiveFieldLayoutItemDto> = {
        mounted(element, binding) {
            bindLayoutDrag(element, binding.value);
        },
        updated(element, binding) {
            if (binding.oldValue?.fieldId !== binding.value.fieldId) {
                cleanupLayoutDrag(element);
                bindLayoutDrag(element, binding.value);
            }
        },
        beforeUnmount(element) {
            cleanupLayoutDrag(element);
        },
    };

    function bindLayoutDrag(element: LayoutDragElement, item: ArchiveFieldLayoutItemDto) {
        const dragHandle = element.querySelector(".archive-layout-config__drag");
        const getData = (): LayoutDragData => ({
            type: "archive-layout-item",
            fieldId: item.fieldId,
        });
        element[layoutDragCleanupKey] = combine(
            draggable({
                element,
                dragHandle: dragHandle ?? undefined,
                getInitialData: getData,
                onDragStart: () => {
                    draggingLayoutFieldId.value = item.fieldId;
                },
                onDrop: () => {
                    draggingLayoutFieldId.value = undefined;
                },
            }),
            dropTargetForElements({
                element,
                canDrop: ({ source }) =>
                    isLayoutDragData(source.data) && source.data.fieldId !== item.fieldId,
                getData: ({ input, element: target }) =>
                    attachInstruction(getData(), {
                        input,
                        element: target,
                        operations: {
                            "reorder-before": "available",
                            "reorder-after": "available",
                        },
                        axis: activeLayoutSurface.value === "table" ? "horizontal" : "vertical",
                    }),
                onDrop: ({ source, self }) => {
                    if (!isLayoutDragData(source.data)) {
                        return;
                    }
                    const instruction = extractInstruction(self.data);
                    if (!instruction || instruction.operation === "combine") {
                        return;
                    }
                    reorderLayoutItem(source.data.fieldId, item.fieldId, instruction.operation);
                },
            }),
        );
    }

    function reorderLayoutItem(
        sourceFieldId: number,
        targetFieldId: number,
        operation: "reorder-before" | "reorder-after",
    ) {
        const nextItems = [...layoutItems.value];
        const sourceIndex = nextItems.findIndex((item) => item.fieldId === sourceFieldId);
        if (sourceIndex < 0) {
            return;
        }
        const [movedItem] = nextItems.splice(sourceIndex, 1);
        const targetIndex = nextItems.findIndex((item) => item.fieldId === targetFieldId);
        if (!movedItem || targetIndex < 0) {
            return;
        }
        const insertIndex = operation === "reorder-after" ? targetIndex + 1 : targetIndex;
        nextItems.splice(insertIndex, 0, movedItem);
        layoutItems.value = nextItems;
    }

    return { vLayoutDrag };
}

function cleanupLayoutDrag(element: LayoutDragElement) {
    element[layoutDragCleanupKey]?.();
    element[layoutDragCleanupKey] = undefined;
}

function isLayoutDragData(data: Record<string | symbol, unknown>): data is LayoutDragData {
    return data.type === "archive-layout-item" && typeof data.fieldId === "number";
}
