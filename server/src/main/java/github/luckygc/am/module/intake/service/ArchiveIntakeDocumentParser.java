package github.luckygc.am.module.intake.service;

import static github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.MAX_ITEMS;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import github.luckygc.am.common.exception.BadRequestException;

/** 说明文本、目录著录及档案元数据的编码与 XML 安全解析。 */
final class ArchiveIntakeDocumentParser {

    private static final String CATALOG_NAME = "目录文件.XML";
    private static final Pattern XML_ENCODING =
            Pattern.compile(
                    "<\\?xml[^>]*\\bencoding\\s*=\\s*['\"]([^'\"]+)['\"]",
                    Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_XML_ENCODINGS =
            Set.of("UTF-8", "UTF8", "GB18030", "GB2312");

    String readText(@Nullable Path path, String name) {
        byte[] bytes = readBytes(path, name);
        if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xfe})
                || startsWith(bytes, new byte[] {(byte) 0xfe, (byte) 0xff})) {
            throw invalid(name + " 编码非法，仅支持 UTF-8、GB18030 或 GB2312");
        }
        if (startsWith(bytes, new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf})) {
            bytes = java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        for (Charset charset :
                List.of(
                        StandardCharsets.UTF_8,
                        Charset.forName("GB18030"),
                        Charset.forName("GB2312"))) {
            try {
                String result =
                        charset.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(bytes))
                                .toString();
                if (result.indexOf('\0') < 0) {
                    return result;
                }
            } catch (CharacterCodingException ignored) {
                // 尝试规范允许的下一种编码。
            }
        }
        throw invalid(name + " 编码非法，仅支持 UTF-8、GB18030 或 GB2312");
    }

    ExplanationData parseExplanation(String text) {
        for (String field : List.of("移交单位", "内容描述", "起止档号", "档案数量", "软硬件环境")) {
            if (extractExplanationValue(text, field) == null) {
                throw invalid("说明文件.TXT 缺少必填项：" + field);
            }
        }
        int archiveCount;
        String countValue = requireNonNull(extractExplanationValue(text, "档案数量"));
        try {
            archiveCount = Integer.parseInt(countValue);
        } catch (NumberFormatException exception) {
            throw invalid("说明文件.TXT 的档案数量必须是整数");
        }
        if (archiveCount < 1 || archiveCount > MAX_ITEMS) {
            throw invalid("说明文件.TXT 的档案数量必须在 1 到 100 之间");
        }
        boolean missingOptionalFields =
                List.of("离线载体参数", "载体编号", "载体数量", "制作单位", "检查单位").stream()
                        .anyMatch(field -> extractExplanationValue(text, field) == null);
        return new ExplanationData(
                extractExplanationValue(text, "载体编号"), archiveCount, missingOptionalFields);
    }

    private @Nullable String extractExplanationValue(String text, String field) {
        Matcher matcher =
                Pattern.compile(
                                "(?m)^\\s*"
                                        + Pattern.quote(field)
                                        + "\\s*[:：=]\\s*([^\\r\\n]+)\\s*$")
                        .matcher(text);
        return matcher.find() ? StringUtils.trimToNull(matcher.group(1)) : null;
    }

    CatalogData parseCatalog(@Nullable Path path) {
        Document document = parseXml(path, CATALOG_NAME);
        Element root = document.getDocumentElement();
        if (root == null || !"文件目录".equals(root.getTagName())) {
            throw invalid("目录文件.XML 根元素必须为 文件目录");
        }
        List<Element> fileElements =
                directChildren(root, element -> "文件".equals(element.getTagName()));
        for (Element child : directChildren(root, ignored -> true)) {
            if (!"文件".equals(child.getTagName())) {
                throw invalid("目录文件.XML 包含非法结构：" + child.getTagName());
            }
        }
        if (fileElements.isEmpty()) {
            throw invalid("目录文件.XML 至少需要包含一个 文件 条目");
        }
        if (fileElements.size() > MAX_ITEMS) {
            throw invalid("目录文件.XML 最多包含 100 个档案条目");
        }

        List<CatalogItem> items = new ArrayList<>();
        Set<String> archiveNumbers = new HashSet<>();
        boolean missingPageCount = false;
        for (int index = 0; index < fileElements.size(); index++) {
            Element element = fileElements.get(index);
            Set<String> allowedFields =
                    Set.of("顺序号", "档号", "责任者", "题名", "日期", "保管期限", "密级", "页数", "备注");
            for (Element child : directChildren(element, ignored -> true)) {
                if (!allowedFields.contains(child.getTagName()) || hasElementChild(child)) {
                    throw invalid(
                            "目录文件.XML 第 " + (index + 1) + " 个 文件 包含非法结构：" + child.getTagName());
                }
            }
            String sequenceNumber = requiredChildText(element, "顺序号", index);
            String archiveNo = requiredChildText(element, "档号", index);
            String responsible = requiredChildText(element, "责任者", index);
            String title = requiredChildText(element, "题名", index);
            String date = requiredChildText(element, "日期", index);
            String retentionPeriod = requiredChildText(element, "保管期限", index);
            String securityLevel = requiredChildText(element, "密级", index);
            String remarks = requiredChildElementText(element, "备注", index);
            String pageCountText = optionalChildText(element, "页数", index);
            Integer pageCount = null;
            if (pageCountText == null) {
                missingPageCount = true;
            } else {
                try {
                    pageCount = Integer.valueOf(pageCountText);
                } catch (NumberFormatException exception) {
                    throw invalid("目录文件.XML 第 " + (index + 1) + " 个 文件 的页数必须是整数");
                }
                if (pageCount < 0) {
                    throw invalid("目录文件.XML 第 " + (index + 1) + " 个 文件 的页数不能为负数");
                }
            }
            if (!archiveNumbers.add(archiveNo)) {
                throw invalid("目录文件.XML 包含重复档号：" + archiveNo);
            }
            items.add(
                    new CatalogItem(
                            sequenceNumber,
                            archiveNo,
                            responsible,
                            title,
                            date,
                            retentionPeriod,
                            securityLevel,
                            pageCount,
                            remarks));
        }
        return new CatalogData(List.copyOf(items), missingPageCount);
    }

    Document parseXml(@Nullable Path path, String displayName) {
        if (path == null) {
            throw invalid(displayName + " 不存在");
        }
        validateXmlEncoding(path, displayName);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            try (InputStream input = Files.newInputStream(path)) {
                return factory.newDocumentBuilder().parse(input);
            }
        } catch (ParserConfigurationException exception) {
            throw invalid("XML 安全解析器初始化失败");
        } catch (SAXException exception) {
            throw invalid(displayName + " 不是合法安全 XML，禁止 DTD 和外部实体");
        } catch (IOException exception) {
            throw invalid(displayName + " 读取失败");
        }
    }

    private void validateXmlEncoding(Path path, String displayName) {
        byte[] prefix;
        try (InputStream input = Files.newInputStream(path)) {
            prefix = input.readNBytes(512);
        } catch (IOException exception) {
            throw invalid(displayName + " 读取失败");
        }
        String declaration = new String(prefix, StandardCharsets.ISO_8859_1);
        Matcher matcher = XML_ENCODING.matcher(declaration);
        if (matcher.find()
                && !ALLOWED_XML_ENCODINGS.contains(matcher.group(1).toUpperCase(Locale.ROOT))) {
            throw invalid(displayName + " 编码非法，仅支持 UTF-8、GB18030 或 GB2312");
        }
        if (startsWith(prefix, new byte[] {(byte) 0xff, (byte) 0xfe})
                || startsWith(prefix, new byte[] {(byte) 0xfe, (byte) 0xff})) {
            throw invalid(displayName + " 编码非法，仅支持 UTF-8、GB18030 或 GB2312");
        }
    }

    private String requiredChildText(Element parent, String name, int itemIndex) {
        String value = requiredChildElementText(parent, name, itemIndex);
        if (StringUtils.isBlank(value)) {
            throw invalid("目录文件.XML 第 " + (itemIndex + 1) + " 个 文件 的" + name + "不能为空");
        }
        return value;
    }

    private String requiredChildElementText(Element parent, String name, int itemIndex) {
        List<Element> children =
                directChildren(parent, element -> name.equals(element.getTagName()));
        if (children.size() != 1) {
            throw invalid("目录文件.XML 第 " + (itemIndex + 1) + " 个 文件 必须且只能包含一个" + name);
        }
        return StringUtils.trim(children.getFirst().getTextContent());
    }

    private @Nullable String optionalChildText(Element parent, String name, int itemIndex) {
        List<Element> children =
                directChildren(parent, element -> name.equals(element.getTagName()));
        if (children.size() > 1) {
            throw invalid("目录文件.XML 第 " + (itemIndex + 1) + " 个 文件 不能重复包含" + name);
        }
        if (children.isEmpty()) {
            return null;
        }
        return StringUtils.trimToNull(children.getFirst().getTextContent());
    }

    private List<Element> directChildren(Element parent, Predicate<Element> predicate) {
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && predicate.test(element)) {
                result.add(element);
            }
        }
        return result;
    }

    private boolean hasElementChild(Element parent) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                return true;
            }
        }
        return false;
    }

    private byte[] readBytes(@Nullable Path path, String name) {
        if (path == null) {
            throw invalid(name + " 不存在");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw invalid(name + " 读取失败");
        }
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private <T> T requireNonNull(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("解析后的文件信息不完整");
        }
        return value;
    }

    private BadRequestException invalid(String message) {
        return new BadRequestException(message);
    }

    record CatalogData(List<CatalogItem> items, boolean missingPageCount) {}

    record ExplanationData(
            @Nullable String packageCode, int archiveCount, boolean missingOptionalFields) {}

    record CatalogItem(
            String sequenceNumber,
            String archiveNo,
            String responsible,
            String title,
            String date,
            String retentionPeriod,
            String securityLevel,
            @Nullable Integer pageCount,
            String remarks) {}
}
