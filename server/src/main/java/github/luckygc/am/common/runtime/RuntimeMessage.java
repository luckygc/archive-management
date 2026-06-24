package github.luckygc.am.common.runtime;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;

/** 运行时队列传递的 CloudEvents 风格消息。 */
public record RuntimeMessage(
        String specVersion,
        String id,
        String source,
        String type,
        String subject,
        String dataContentType,
        String dataJson,
        Instant time) {

    /** 当前消息格式遵循的 CloudEvents specversion。 */
    public static final String CLOUD_EVENTS_SPEC_VERSION = "1.0";

    public RuntimeMessage {
        specVersion = defaultText(specVersion, CLOUD_EVENTS_SPEC_VERSION);
        id = requireText(id, "消息 ID");
        source = requireText(source, "消息来源");
        type = requireText(type, "消息类型");
        dataContentType = defaultText(dataContentType, "application/json");
        dataJson = defaultText(dataJson, "{}");
        time = time == null ? Instant.now() : time;
    }

    public RuntimeMessage(
            String id, String source, String type, String subject, String dataJson, Instant time) {
        this(
                CLOUD_EVENTS_SPEC_VERSION,
                id,
                source,
                type,
                subject,
                "application/json",
                dataJson,
                time);
    }

    private static String requireText(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String defaultText(String value, String defaultValue) {
        return StringUtils.defaultIfBlank(value, defaultValue).trim();
    }
}
