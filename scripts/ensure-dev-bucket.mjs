import { spawnSync } from "node:child_process";

const port = process.env.ARCHIVE_STORAGE_PORT || "9000";
const bucket = process.env.ARCHIVE_STORAGE_BUCKET || "archive";
const region = process.env.ARCHIVE_STORAGE_REGION || "us-east-1";
const accessKey = process.env.ARCHIVE_STORAGE_ACCESS_KEY || "rustfsadmin";
const secretKey = process.env.ARCHIVE_STORAGE_SECRET_KEY || "rustfsadmin";
const url = `http://localhost:${port}/${bucket}`;
const curl = process.platform === "win32" ? "curl.exe" : "curl";
const auth = ["--aws-sigv4", `aws:amz:${region}:s3`, "--user", `${accessKey}:${secretKey}`];

const head = spawnSync(curl, ["--fail", "--silent", "--head", ...auth, url], {
    stdio: "ignore",
});
if (head.status === 0) {
    console.log(`开发 bucket 已存在：${bucket}`);
    process.exit(0);
}

const create = spawnSync(curl, ["--fail", "--silent", "--show-error", "--request", "PUT", ...auth, url], {
    stdio: "inherit",
});
if (create.error) throw create.error;
if (create.status !== 0) process.exit(create.status || 1);
console.log(`开发 bucket 已创建：${bucket}`);
