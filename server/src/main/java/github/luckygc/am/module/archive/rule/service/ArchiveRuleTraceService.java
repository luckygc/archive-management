package github.luckygc.am.module.archive.rule.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.data.page.PageRequest;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveRuleMapper;
import github.luckygc.am.module.archive.mapper.ArchiveRuleTraceSearchCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveRuleTraceSearchCriteria.ArchiveRuleTracePageWindow;
import github.luckygc.am.module.archive.mapper.ArchiveRuleTraceSearchCriteria.ArchiveRuleTraceTargetScope;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
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
    private final ArchiveCategoryService categoryService;

    public ArchiveRuleTraceService(
            ArchiveRuleTraceDataRepository traceRepository,
            ArchiveRuleMapper ruleMapper,
            ArchiveDataScopeService dataScopeService,
            ArchiveCategoryService categoryService) {
        this.traceRepository = traceRepository;
        this.ruleMapper = ruleMapper;
        this.dataScopeService = dataScopeService;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<Map<String, Object>> listRuleTraces(
            SearchArchiveRuleTracesRequest request, PageRequest pageRequest) {
        Long userId = AuthenticatedUsers.requireResolvedUserId(request.userId());
        boolean allData = dataScopeService.resolveUserDataScope(userId).allData();
        TraceScopes scopes = allData ? TraceScopes.empty() : traceScopes(userId);
        List<Map<String, Object>> rows =
                ruleMapper.listRuleTraces(criteria(request, pageRequest, userId, allData, scopes));
        return toCursorPage(rows, pageRequest);
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

    private TraceScopes traceScopes(Long userId) {
        List<ArchiveRuleTraceTargetScope> itemScopes = new ArrayList<>();
        List<ArchiveRuleTraceTargetScope> volumeScopes = new ArrayList<>();
        for (var category : categoryService.listCategories(null)) {
            ArchiveDataScopeFilter filter =
                    dataScopeService.buildItemFilter(userId, category.id(), null);
            if (filter.empty()) {
                continue;
            }
            List<ArchiveDataScopeSqlGroup> groups =
                    filter.allData()
                            ? List.of(
                                    new ArchiveDataScopeSqlGroup(
                                            List.of(), List.of(), List.of(), List.of()))
                            : filter.groups();
            String tableName = ArchiveDynamicTableNames.tableName(category, ArchiveLevel.ITEM);
            itemScopes.add(
                    new ArchiveRuleTraceTargetScope(category.categoryCode(), tableName, groups));
            List<ArchiveDataScopeSqlGroup> fixedGroups =
                    groups.stream().filter(group -> group.conditions().isEmpty()).toList();
            if (!fixedGroups.isEmpty()) {
                volumeScopes.add(
                        new ArchiveRuleTraceTargetScope(
                                category.categoryCode(), tableName, fixedGroups));
            }
        }
        return new TraceScopes(itemScopes, volumeScopes);
    }

    private ArchiveRuleTraceSearchCriteria criteria(
            SearchArchiveRuleTracesRequest request,
            PageRequest pageRequest,
            Long userId,
            boolean allData,
            TraceScopes scopes) {
        return new ArchiveRuleTraceSearchCriteria(
                request.schemeVersionId(),
                StringUtils.trimToNull(request.triggerCode()),
                StringUtils.trimToNull(request.objectTypeCode()),
                request.objectId(),
                request.ruleType() == null ? null : request.ruleType().name(),
                allData,
                userId,
                scopes.itemScopes(),
                scopes.volumeScopes(),
                pageWindow(pageRequest));
    }

    private ArchiveRuleTracePageWindow pageWindow(PageRequest pageRequest) {
        @Nullable LocalDateTime cursorCreatedAt = null;
        @Nullable Long cursorId = null;
        if (pageRequest.cursor().isPresent()) {
            List<?> values = pageRequest.cursor().orElseThrow().elements();
            if (values.size() != 2
                    || !(values.get(0) instanceof LocalDateTime createdAt)
                    || !(values.get(1) instanceof Number id)) {
                throw new BadRequestException(
                        "分页 cursor 无效", "cursor", "规则追踪 cursor 必须包含创建时间和数值 ID");
            }
            cursorCreatedAt = createdAt;
            cursorId = id.longValue();
        }
        return new ArchiveRuleTracePageWindow(
                pageRequest.mode() == PageRequest.Mode.CURSOR_PREVIOUS,
                cursorCreatedAt,
                cursorId,
                pageRequest.size() + 1);
    }

    private CursorPageResponse<Map<String, Object>> toCursorPage(
            List<Map<String, Object>> rows, PageRequest pageRequest) {
        int limit = pageRequest.size();
        boolean hasMore = rows.size() > limit;
        List<Map<String, Object>> pageRows =
                new ArrayList<>(hasMore ? rows.subList(0, limit) : rows);
        boolean previousQuery = pageRequest.mode() == PageRequest.Mode.CURSOR_PREVIOUS;
        if (previousQuery) {
            pageRows = pageRows.reversed();
        }
        boolean hasPrevious = previousQuery ? hasMore : pageRequest.cursor().isPresent();
        boolean hasNext = previousQuery ? pageRequest.cursor().isPresent() : hasMore;
        List<?> self = pageRows.isEmpty() ? null : cursorValues(pageRows.getFirst());
        List<?> prev =
                hasPrevious && !pageRows.isEmpty() ? cursorValues(pageRows.getFirst()) : null;
        List<?> next = hasNext && !pageRows.isEmpty() ? cursorValues(pageRows.getLast()) : null;
        return CursorPageResponse.withCursorValues(pageRows, limit, self, prev, next, null, null);
    }

    private List<?> cursorValues(Map<String, Object> row) {
        Object createdAt = rowValue(row, "createdAt");
        Object id = rowValue(row, "id");
        if (!(createdAt instanceof LocalDateTime localDateTime) || !(id instanceof Number number)) {
            throw new IllegalStateException("规则追踪缺少创建时间或数值 ID");
        }
        return List.of(localDateTime, number.longValue());
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

    private @Nullable Object rowValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    private record TraceScopes(
            List<ArchiveRuleTraceTargetScope> itemScopes,
            List<ArchiveRuleTraceTargetScope> volumeScopes) {

        private TraceScopes {
            itemScopes = List.copyOf(itemScopes);
            volumeScopes = List.copyOf(volumeScopes);
        }

        private static TraceScopes empty() {
            return new TraceScopes(List.of(), List.of());
        }
    }
}
