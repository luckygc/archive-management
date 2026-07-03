package github.luckygc.am.common.api;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.exception.BadRequestException;

public final class CursorPageTokenCodec {

    private static final String VERSION = "v1";
    private static final String CONTEXT_VERSION = "v2";
    private static volatile byte[] secretKey = randomSecretKey();
    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private CursorPageTokenCodec() {}

    public static void configureSecret(String secret) {
        String normalized = StringUtils.trimToNull(secret);
        if (normalized == null || normalized.length() < 32) {
            throw new IllegalArgumentException("cursor token secret 长度不能小于 32");
        }
        secretKey = normalized.getBytes(StandardCharsets.UTF_8);
    }

    public static PageRequest pageRequest(int limit, @Nullable String token, boolean requestTotal) {
        PageRequest pageRequest =
                requestTotal && StringUtils.isBlank(token)
                        ? PageRequest.ofSize(limit).withTotal()
                        : PageRequest.ofSize(limit).withoutTotal();
        DecodedCursor cursor = decode(token);
        if (cursor == null) {
            return pageRequest;
        }
        PageRequest.Cursor pageCursor =
                PageRequest.Cursor.forKey(cursor.values().toArray(Object[]::new));
        return switch (cursor.direction()) {
            case "next" -> pageRequest.afterCursor(pageCursor);
            case "prev" -> pageRequest.beforeCursor(pageCursor);
            default -> throw invalidCursor("cursor 格式无效");
        };
    }

    public static PageRequest pageRequest(
            int limit,
            @Nullable String token,
            boolean requestTotal,
            CursorPageTokenContext context) {
        PageRequest pageRequest =
                requestTotal && StringUtils.isBlank(token)
                        ? PageRequest.ofSize(limit).withTotal()
                        : PageRequest.ofSize(limit).withoutTotal();
        DecodedCursor cursor = decode(token);
        if (cursor == null) {
            return pageRequest;
        }
        if (cursor.limit() == null
                || cursor.context() == null
                || cursor.limit() != limit
                || !cursor.context().equals(context)) {
            throw invalidCursor("cursor 查询条件不匹配");
        }
        PageRequest.Cursor pageCursor =
                PageRequest.Cursor.forKey(cursor.values().toArray(Object[]::new));
        return switch (cursor.direction()) {
            case "next" -> pageRequest.afterCursor(pageCursor);
            case "prev" -> pageRequest.beforeCursor(pageCursor);
            default -> throw invalidCursor("cursor 格式无效");
        };
    }

    public static @Nullable String encode(PageRequest pageRequest) {
        return switch (pageRequest.mode()) {
            case CURSOR_NEXT -> encode("next", pageRequest.cursor().orElseThrow().elements());
            case CURSOR_PREVIOUS -> encode("prev", pageRequest.cursor().orElseThrow().elements());
            case OFFSET -> null;
        };
    }

    public static @Nullable String encode(String direction, PageRequest.Cursor cursor) {
        return encode(direction, cursor.elements());
    }

    public static @Nullable String encode(
            String direction,
            PageRequest.Cursor cursor,
            int limit,
            CursorPageTokenContext context) {
        return encode(direction, cursor.elements(), limit, context);
    }

    public static @Nullable String self(CursoredPage<?> page) {
        return page.numberOfElements() == 0 ? null : encode("self", page.cursor(0));
    }

    public static @Nullable String previous(CursoredPage<?> page) {
        return page.hasPrevious() ? encode(page.previousPageRequest()) : null;
    }

    public static @Nullable String next(CursoredPage<?> page) {
        return page.hasNext() ? encode(page.nextPageRequest()) : null;
    }

    public static @Nullable String encode(String direction, @Nullable List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> fields = new ArrayList<>(values.size() + 2);
        fields.add(VERSION);
        fields.add(direction);
        values.forEach(value -> fields.add(encodeValue(value)));
        String payload = String.join("|", fields);
        return BASE64_URL_ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "."
                + hmacHex(payload);
    }

    public static @Nullable String encode(
            String direction, @Nullable List<?> values, int limit, CursorPageTokenContext context) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> fields = new ArrayList<>(values.size() + 6);
        fields.add(CONTEXT_VERSION);
        fields.add(direction);
        fields.add(Integer.toString(limit));
        fields.add(encodeString(context.endpointId()));
        fields.add(context.queryFingerprint());
        fields.add(encodeString(context.userKey()));
        values.forEach(value -> fields.add(encodeValue(value)));
        String payload = String.join("|", fields);
        return BASE64_URL_ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "."
                + hmacHex(payload);
    }

    public static @Nullable DecodedCursor decode(@Nullable String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        try {
            String[] tokenParts = token.split("\\.", 2);
            if (tokenParts.length != 2) {
                throw invalidCursor("cursor 格式无效");
            }
            String payload =
                    new String(BASE64_URL_DECODER.decode(tokenParts[0]), StandardCharsets.UTF_8);
            if (!Objects.equals(hmacHex(payload), tokenParts[1])) {
                throw invalidCursor("cursor 签名无效");
            }
            String[] fields = payload.split("\\|", -1);
            if (fields.length >= 7 && CONTEXT_VERSION.equals(fields[0])) {
                if (!"next".equals(fields[1])
                        && !"prev".equals(fields[1])
                        && !"self".equals(fields[1])) {
                    throw invalidCursor("cursor 格式无效");
                }
                List<Object> values = new ArrayList<>(fields.length - 6);
                for (int index = 6; index < fields.length; index++) {
                    values.add(decodeValue(fields[index]));
                }
                return new DecodedCursor(
                        fields[1],
                        values,
                        Integer.valueOf(fields[2]),
                        new CursorPageTokenContext(
                                decodeString(fields[3]), fields[4], decodeString(fields[5])));
            }
            if (fields.length < 3 || !VERSION.equals(fields[0])) {
                throw invalidCursor("cursor 格式无效");
            }
            if (!"next".equals(fields[1])
                    && !"prev".equals(fields[1])
                    && !"self".equals(fields[1])) {
                throw invalidCursor("cursor 格式无效");
            }
            List<Object> values = new ArrayList<>(fields.length - 2);
            for (int index = 2; index < fields.length; index++) {
                values.add(decodeValue(fields[index]));
            }
            return new DecodedCursor(fields[1], values, null, null);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            if (exception instanceof BadRequestException badRequestException) {
                throw badRequestException;
            }
            throw invalidCursor("cursor 格式无效");
        }
    }

    private static String encodeValue(@Nullable Object value) {
        return switch (value) {
            case null -> "N:";
            case LocalDateTime dateTime -> "T:" + dateTime;
            case Timestamp timestamp -> "T:" + timestamp.toLocalDateTime();
            case LocalDate date -> "D:" + date;
            case Date date -> "D:" + date.toLocalDate();
            case Long longValue -> "L:" + longValue;
            case Integer integerValue -> "I:" + integerValue;
            case BigDecimal decimal -> "B:" + decimal.toPlainString();
            case Number number -> "L:" + number.longValue();
            default ->
                    "S:"
                            + BASE64_URL_ENCODER.encodeToString(
                                    value.toString().getBytes(StandardCharsets.UTF_8));
        };
    }

    private static String encodeString(String value) {
        return BASE64_URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeString(String value) {
        return new String(BASE64_URL_DECODER.decode(value), StandardCharsets.UTF_8);
    }

    private static @Nullable Object decodeValue(String value) {
        if (value.length() < 2 || value.charAt(1) != ':') {
            throw invalidCursor("cursor 格式无效");
        }
        String raw = value.substring(2);
        return switch (value.charAt(0)) {
            case 'N' -> null;
            case 'T' -> LocalDateTime.parse(raw);
            case 'D' -> LocalDate.parse(raw);
            case 'L' -> Long.valueOf(raw);
            case 'I' -> Integer.valueOf(raw);
            case 'B' -> new BigDecimal(raw);
            case 'S' -> new String(BASE64_URL_DECODER.decode(raw), StandardCharsets.UTF_8);
            default -> throw invalidCursor("cursor 格式无效");
        };
    }

    private static String hmacHex(String payload) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secretKey).hmacHex(payload);
    }

    private static byte[] randomSecretKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static BadRequestException invalidCursor(String reason) {
        return new BadRequestException("分页 cursor 无效", "cursor", reason);
    }

    public record DecodedCursor(
            String direction,
            List<Object> values,
            @Nullable Integer limit,
            @Nullable CursorPageTokenContext context) {

        public DecodedCursor {
            values = List.copyOf(values);
        }
    }

    public static @Nullable String self(
            CursoredPage<?> page, int limit, CursorPageTokenContext context) {
        return page.numberOfElements() == 0 ? null : encode("self", page.cursor(0), limit, context);
    }

    public static @Nullable String previous(
            CursoredPage<?> page, int limit, CursorPageTokenContext context) {
        return page.hasPrevious()
                ? encode("prev", page.previousPageRequest().cursor().orElseThrow(), limit, context)
                : null;
    }

    public static @Nullable String next(
            CursoredPage<?> page, int limit, CursorPageTokenContext context) {
        return page.hasNext()
                ? encode("next", page.nextPageRequest().cursor().orElseThrow(), limit, context)
                : null;
    }
}
