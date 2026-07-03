package github.luckygc.am.module.archive.metadata;

/** 字段来源：BUILTIN 表示主表内置字段，METADATA 表示用户自定义动态字段。 */
public enum ArchiveFieldSource {
    BUILTIN,
    METADATA;

    public String value() {
        return name();
    }
}
