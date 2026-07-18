import type { AuthorizationPermissionDto } from "@/shared/types/authorization";

const moduleNames: Record<string, string> = {
    archive: "档案",
    authorization: "授权",
    authentication: "认证",
    organization: "组织",
};
const resourceNames: Record<string, string> = {
    item: "条目",
    audit: "审计",
    export: "导出",
    metadata: "元数据",
    "data-scope": "数据范围",
    permission: "功能权限",
    role: "角色",
    session: "会话",
    user: "用户",
    department: "部门",
};
const actionNames: Record<string, string> = {
    read: "读取",
    create: "创建",
    update: "修改",
    delete: "删除",
    lock: "锁定",
    manage: "管理",
    "download-electronic-file": "下载电子文件",
};

export function permissionOptionLabel(permission: AuthorizationPermissionDto) {
    const parsed = parsePermissionCode(permission.permissionCode);
    return `${permission.permissionName}（${moduleName(permission.moduleCode)} · ${resourceName(parsed.resource)} · ${actionName(parsed.action)}）`;
}

export function parsePermissionCode(code: string) {
    const segments = code.split(":");
    return {
        module: segments[0] ?? "",
        resource: segments.slice(1, -1).join(":"),
        action: segments.at(-1) ?? "",
    };
}
export function moduleName(code: string) {
    return moduleNames[code] ?? code;
}
export function resourceName(code: string) {
    return resourceNames[code] ?? code;
}
export function actionName(code: string) {
    return actionNames[code] ?? code;
}
