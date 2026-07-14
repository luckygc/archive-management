package github.luckygc.am.module.archive.rule.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
import github.luckygc.am.module.archive.mapper.ArchiveRuleMapper;
import github.luckygc.am.module.archive.mapper.ArchiveRuleTraceSearchCriteria;
import github.luckygc.am.module.archive.rule.ArchiveRuleDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuleTrace;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuleTraceDataRepository;
import github.luckygc.am.module.archive.rule.service.ArchiveLocalRuleService.ExecuteArchiveRulesRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveLocalRuleService.SearchArchiveRuleTracesRequest;

@Service
public class ArchiveRuleTraceService {

    private static final Pattern SENSITIVE_PARAM_KEY_PATTERN =
            Pattern.compile(
                    ".*(password|passwd|secret|token|credential|authorization|cookie|idcard|identity|certificate|phone|mobile|email|address|bank|account|summary|content|fulltext).*",
                    Pattern.CASE_INSENSITIVE);
    private static final String REDACTED_VALUE = "[已脱敏]";

    private final ArchiveRuleTraceDataRepository traceRepository;
    private final ArchiveRuleMapper ruleMapper;
    private final ArchiveDataScopeService dataScopeService;
    private final ArchiveItemReadService archiveItemReadService;
    private final ArchiveVolumeService archiveVolumeService;

    public ArchiveRuleTraceService(
            ArchiveRuleTraceDataRepository traceRepository,
            ArchiveRuleMapper ruleMapper,
            ArchiveDataScopeService dataScopeService,
            ArchiveItemReadService archiveItemReadService,
            ArchiveVolumeService archiveVolumeService) {
        this.traceRepository = traceRepository;
        this.ruleMapper = ruleMapper;
        this.dataScopeService = dataScopeService;
        this.archiveItemReadService = archiveItemReadService;
        this.archiveVolumeService = archiveVolumeService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRuleTraces(SearchArchiveRuleTracesRequest request) {
        Long userId = AuthenticatedUsers.requireResolvedUserId(request.userId());
        int limit = request.limit() == null ? 100 : Math.clamp(request.limit(), 1, 500);
        boolean allData = dataScopeService.resolveUserDataScope(userId).allData();
        return ruleMapper
                .listRuleTraces(
                        new ArchiveRuleTraceSearchCriteria(
                                request.schemeVersionId(),
                                StringUtils.trimToNull(request.triggerCode()),
                                StringUtils.trimToNull(request.objectTypeCode()),
                                request.objectId(),
                                request.ruleType() == null ? null : request.ruleType().name(),
                                500))
                .stream()
                .filter(trace -> traceVisible(trace, userId, allData))
                .limit(limit)
                .toList();
    }

    void saveTrace(ExecuteArchiveRulesRequest request, ArchiveRuleDecision decision) {
        ArchiveRuleTrace trace = new ArchiveRuleTrace();
        trace.setSchemeVersionId(request.schemeVersionId());
        trace.setTriggerCode(request.triggerCode());
        trace.setObjectTypeCode(StringUtils.defaultString(request.objectTypeCode(), "UNKNOWN"));
        trace.setObjectId(request.objectId());
        trace.setRuleId(decision.ruleId());
        trace.setRuleCode(decision.ruleCode());
        trace.setRuleType(decision.ruleType());
        trace.setMatchedFlag(decision.matched());
        trace.setBlockingFlag(decision.blocking());
        trace.setEffectJson(
                decision.effects().stream()
                        .map(
                                effect -> {
                                    Map<String, Object> effectTrace = new LinkedHashMap<>();
                                    effectTrace.put("effectType", effect.effectType().name());
                                    effectTrace.put(
                                            "params", redactSensitiveParams(effect.params()));
                                    return effectTrace;
                                })
                        .toList());
        trace.setMessage(decision.message());
        trace.setSeverity(decision.severity());
        trace.setSkippedReason(decision.skippedReason());
        trace.setCreatedBy(request.userId());
        traceRepository.insert(trace);
    }

    private boolean traceVisible(Map<String, Object> trace, Long userId, boolean allData) {
        if (allData) return true;
        String objectTypeCode = string(trace, "objectTypeCode");
        Long objectId = longOrNull(trace, "objectId");
        if ("ARCHIVE_ITEM".equals(objectTypeCode) && objectId != null)
            return archiveItemVisible(objectId, userId);
        if ("ARCHIVE_VOLUME".equals(objectTypeCode) && objectId != null)
            return archiveVolumeVisible(objectId, userId);
        return Objects.equals(longOrNull(trace, "createdBy"), userId);
    }

    private boolean archiveItemVisible(Long objectId, Long userId) {
        try {
            archiveItemReadService.assertItemInDataScope(objectId, userId);
            return true;
        } catch (ResponseStatusException exception) {
            return nonVisibleArchiveObject(exception);
        }
    }

    private boolean archiveVolumeVisible(Long objectId, Long userId) {
        try {
            archiveVolumeService.assertVolumeInDataScope(objectId, userId);
            return true;
        } catch (ResponseStatusException exception) {
            return nonVisibleArchiveObject(exception);
        }
    }

    private boolean nonVisibleArchiveObject(ResponseStatusException exception) {
        if (exception.getStatusCode() == HttpStatus.FORBIDDEN
                || exception.getStatusCode() == HttpStatus.NOT_FOUND) return false;
        throw exception;
    }

    private Map<String, Object> redactSensitiveParams(Map<String, Object> params) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        params.forEach((key, value) -> redacted.put(key, redactSensitiveValue(key, value)));
        return redacted;
    }

    private @Nullable Object redactSensitiveValue(String key, @Nullable Object value) {
        if (SENSITIVE_PARAM_KEY_PATTERN.matcher(key).matches()) return REDACTED_VALUE;
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            nestedMap.forEach(
                    (nestedKey, nestedValue) ->
                            redacted.put(
                                    String.valueOf(nestedKey),
                                    redactSensitiveValue(String.valueOf(nestedKey), nestedValue)));
            return redacted;
        }
        if (value instanceof List<?> list)
            return list.stream().map(item -> redactListItem(key, item)).toList();
        return value;
    }

    private @Nullable Object redactListItem(String key, @Nullable Object item) {
        if (item instanceof Map<?, ?> nestedMap) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            nestedMap.forEach(
                    (nestedKey, nestedValue) ->
                            redacted.put(
                                    String.valueOf(nestedKey),
                                    redactSensitiveValue(String.valueOf(nestedKey), nestedValue)));
            return redacted;
        }
        return redactSensitiveValue(key, item);
    }

    private @Nullable String string(Map<String, Object> row, String key) {
        Object value = rowValue(row, key);
        return value == null ? null : String.valueOf(value);
    }

    private @Nullable Long longOrNull(Map<String, Object> row, String key) {
        Object value = rowValue(row, key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private @Nullable Object rowValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }
}
