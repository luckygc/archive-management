package github.luckygc.am.module.archive.rule.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveRuleMapper;
import github.luckygc.am.module.archive.mapper.ArchiveRuntimeTraceSearchCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveRuntimeTraceSearchCriteria.ArchiveRuntimeTracePageWindow;
import github.luckygc.am.module.archive.mapper.ArchiveRuntimeTraceSearchCriteria.ArchiveRuntimeTraceTargetScope;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTrace;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeTraceDataRepository;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;

@Service
public class ArchiveRuntimeTraceService {

    private final ArchiveRuntimeTraceDataRepository traceRepository;
    private final ArchiveRuleMapper ruleMapper;
    private final ArchiveDataScopeService dataScopeService;
    private final ArchiveCategoryService categoryService;

    public ArchiveRuntimeTraceService(
            ArchiveRuntimeTraceDataRepository traceRepository,
            ArchiveRuleMapper ruleMapper,
            ArchiveDataScopeService dataScopeService,
            ArchiveCategoryService categoryService) {
        this.traceRepository = traceRepository;
        this.ruleMapper = ruleMapper;
        this.dataScopeService = dataScopeService;
        this.categoryService = categoryService;
    }

    @Transactional
    public void saveSuccessfulExecution(
            ArchiveRuntimeExecutionRequest request,
            ArchiveRuntimeExecutionResult result,
            @Nullable Long persistedObjectId) {
        if (result.blocking()) {
            throw new IllegalArgumentException("阻断结果不能写入成功事务追踪");
        }
        for (ArchiveRuntimeDecision decision : result.decisions()) {
            ArchiveRuntimeTrace trace = new ArchiveRuntimeTrace();
            trace.setSchemeVersionId(request.schemeVersionId());
            trace.setTriggerPoint(request.triggerPoint());
            trace.setObjectTypeCode(
                    StringUtils.defaultIfBlank(request.objectTypeCode(), "UNKNOWN"));
            trace.setObjectId(persistedObjectId == null ? request.objectId() : persistedObjectId);
            trace.setDefinitionId(decision.definitionId());
            trace.setDefinitionCode(decision.definitionCode());
            trace.setDefinitionKind(decision.definitionKind());
            trace.setMatchedFlag(decision.matched());
            trace.setBlockingFlag(false);
            trace.setActionJson(actionSummary(decision.actions()));
            trace.setMessage(decision.message());
            trace.setSeverity(decision.severity());
            trace.setSkippedReason(decision.skippedReason());
            trace.setCreatedBy(request.userId());
            traceRepository.insert(trace);
        }
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<Map<String, Object>> listTraces(
            SearchArchiveRuntimeTracesRequest request, PageRequest pageRequest) {
        Long userId = AuthenticatedUsers.requireResolvedUserId(request.userId());
        ResolvedArchiveDataScope resolved = dataScopeService.resolveUserDataScope(userId);
        boolean allData = resolved.allData();
        TraceScopes scopes = allData ? TraceScopes.empty() : traceScopes(resolved);
        List<Map<String, Object>> rows =
                ruleMapper.listRuntimeTraces(
                        criteria(request, pageRequest, userId, allData, scopes));
        return toCursorPage(rows, pageRequest);
    }

    private List<Map<String, Object>> actionSummary(List<ArchiveRuntimeActionDecision> decisions) {
        return decisions.stream()
                .map(
                        decision -> {
                            Map<String, Object> summary = new LinkedHashMap<>();
                            summary.put("actionType", decision.actionType().name());
                            Object field = decision.params().get("field");
                            if (field instanceof String fieldCode) summary.put("field", fieldCode);
                            return Map.copyOf(summary);
                        })
                .toList();
    }

    private TraceScopes traceScopes(ResolvedArchiveDataScope resolved) {
        List<ArchiveRuntimeTraceTargetScope> itemScopes = new ArrayList<>();
        List<ArchiveRuntimeTraceTargetScope> volumeScopes = new ArrayList<>();
        var categories = categoryService.listCategories(null);
        Map<Long, ArchiveDataScopeFilter> filters =
                dataScopeService.compileItemFilters(resolved, categories, null);
        for (var category : categories) {
            ArchiveDataScopeFilter filter =
                    filters.getOrDefault(category.id(), ArchiveDataScopeFilter.none());
            if (filter.empty()) continue;
            List<ArchiveDataScopeSqlGroup> groups =
                    filter.allData()
                            ? List.of(
                                    new ArchiveDataScopeSqlGroup(
                                            List.of(), List.of(), List.of(), List.of()))
                            : filter.groups();
            String tableName = ArchiveDynamicTableNames.tableName(category, ArchiveLevel.ITEM);
            itemScopes.add(
                    new ArchiveRuntimeTraceTargetScope(category.categoryCode(), tableName, groups));
            List<ArchiveDataScopeSqlGroup> fixedGroups =
                    groups.stream().filter(group -> group.conditions().isEmpty()).toList();
            if (!fixedGroups.isEmpty()) {
                volumeScopes.add(
                        new ArchiveRuntimeTraceTargetScope(
                                category.categoryCode(), tableName, fixedGroups));
            }
        }
        return new TraceScopes(itemScopes, volumeScopes);
    }

    private ArchiveRuntimeTraceSearchCriteria criteria(
            SearchArchiveRuntimeTracesRequest request,
            PageRequest pageRequest,
            Long userId,
            boolean allData,
            TraceScopes scopes) {
        return new ArchiveRuntimeTraceSearchCriteria(
                request.schemeVersionId(),
                request.triggerPoint() == null ? null : request.triggerPoint().name(),
                StringUtils.trimToNull(request.objectTypeCode()),
                request.objectId(),
                request.definitionKind() == null ? null : request.definitionKind().name(),
                allData,
                userId,
                scopes.itemScopes(),
                scopes.volumeScopes(),
                pageWindow(pageRequest));
    }

    private ArchiveRuntimeTracePageWindow pageWindow(PageRequest pageRequest) {
        @Nullable LocalDateTime cursorCreatedAt = null;
        @Nullable Long cursorId = null;
        if (pageRequest.cursor().isPresent()) {
            List<?> values = pageRequest.cursor().orElseThrow().elements();
            if (values.size() != 2
                    || !(values.get(0) instanceof LocalDateTime createdAt)
                    || !(values.get(1) instanceof Number id)) {
                throw new BadRequestException(
                        "分页 cursor 无效", "cursor", "运行时追踪 cursor 必须包含创建时间和数值 ID");
            }
            cursorCreatedAt = createdAt;
            cursorId = id.longValue();
        }
        return new ArchiveRuntimeTracePageWindow(
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
        if (previousQuery) pageRows = pageRows.reversed();
        boolean hasPrevious = previousQuery ? hasMore : pageRequest.cursor().isPresent();
        boolean hasNext = previousQuery ? pageRequest.cursor().isPresent() : hasMore;
        List<?> self = pageRows.isEmpty() ? null : cursorValues(pageRows.getFirst());
        List<?> previous =
                hasPrevious && !pageRows.isEmpty() ? cursorValues(pageRows.getFirst()) : null;
        List<?> next = hasNext && !pageRows.isEmpty() ? cursorValues(pageRows.getLast()) : null;
        return CursorPageResponse.withCursorValues(
                pageRows, limit, self, previous, next, null, null);
    }

    private List<?> cursorValues(Map<String, Object> row) {
        Object createdAt = rowValue(row, "createdAt");
        Object id = rowValue(row, "id");
        if (!(createdAt instanceof LocalDateTime localDateTime) || !(id instanceof Number number)) {
            throw new IllegalStateException("运行时追踪缺少创建时间或数值 ID");
        }
        return List.of(localDateTime, number.longValue());
    }

    private @Nullable Object rowValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    public record SearchArchiveRuntimeTracesRequest(
            @Nullable Long schemeVersionId,
            @Nullable ArchiveRuntimeTriggerPoint triggerPoint,
            @Nullable String objectTypeCode,
            @Nullable Long objectId,
            @Nullable ArchiveRuntimeDefinitionKind definitionKind,
            @Nullable Long userId) {}

    private record TraceScopes(
            List<ArchiveRuntimeTraceTargetScope> itemScopes,
            List<ArchiveRuntimeTraceTargetScope> volumeScopes) {
        private TraceScopes {
            itemScopes = List.copyOf(itemScopes);
            volumeScopes = List.copyOf(volumeScopes);
        }

        private static TraceScopes empty() {
            return new TraceScopes(List.of(), List.of());
        }
    }
}
