# API 问题类型

项目自有 API 错误使用 `application/problem+json`。`type` 是客户端识别问题类型的稳定标识；`title` 和 `detail` 用于展示，不作为分支判断依据。HTTP 状态仍由响应状态码表达。

字段错误使用 `errors: [{detail, pointer}]`。`pointer` 对应请求输入字段；嵌套字段使用 JSON Pointer 路径，数组索引使用数字路径段。查询参数和请求头的错误以其字段名作为顶层路径段。客户端应将 `~1`、`~0` 分别还原为 `/`、`~`。

问题类型 URI 指向本文件中的小写连字符锚点。以下类型适用于当前接口；具体字段校验信息由对应问题的响应提供。

## invalid-argument

请求参数、请求体或字段值不合法。HTTP 状态为 `400`。

## unauthenticated

请求没有有效的认证会话。HTTP 状态为 `401`。

## permission-denied

当前用户无权执行操作。HTTP 状态为 `403`。

## not-found

目标资源不存在或当前用户不可见。HTTP 状态为 `404`。

## already-exists

请求与已有资源或当前状态冲突。通常为 HTTP `409`。

## failed-precondition

操作未满足当前状态要求。

## resource-exhausted

请求超过当前允许的资源或频率限制。HTTP 状态为 `429`。

## internal

服务处理发生未预期错误。HTTP 状态为 `500`；响应不包含异常栈。

## unimplemented

请求的操作尚未实现。HTTP 状态为 `501`。

## unavailable

服务暂时不可用。HTTP 状态为 `503`。

## deadline-exceeded

上游或服务处理超时。HTTP 状态为 `504`。

## unknown

服务无法归类的 HTTP 错误。

## totp-challenge-invalid

二次验证挑战不存在、已过期或已失效。客户端需要重新进行密码登录。

## totp-code-invalid

二次验证码错误；客户端可在挑战仍有效时重试。

## totp-enrollment-invalid

身份验证器设置流程已过期或无效；客户端需要重新开始设置。

## totp-already-enabled

账号已启用身份验证器；客户端应重新读取当前状态。

## totp-not-enabled

账号尚未启用身份验证器，无法执行需要已启用状态的操作。

## totp-credential-verification-failed

当前密码或二次验证码校验失败。
