package github.luckygc.am.module.archive.governance.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceBinding;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceBindingType;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScheme;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersionStatus;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScope;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScopeType;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceBindingDataRepository;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeDataRepository;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeVersionDataRepository;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceScopeDataRepository;

@Service
public class ArchiveGovernanceService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");

    private final ArchiveGovernanceSchemeDataRepository schemeRepository;
    private final ArchiveGovernanceSchemeVersionDataRepository versionRepository;
    private final ArchiveGovernanceScopeDataRepository scopeRepository;
    private final ArchiveGovernanceBindingDataRepository bindingRepository;

    public ArchiveGovernanceService(
            ArchiveGovernanceSchemeDataRepository schemeRepository,
            ArchiveGovernanceSchemeVersionDataRepository versionRepository,
            ArchiveGovernanceScopeDataRepository scopeRepository,
            ArchiveGovernanceBindingDataRepository bindingRepository) {
        this.schemeRepository = schemeRepository;
        this.versionRepository = versionRepository;
        this.scopeRepository = scopeRepository;
        this.bindingRepository = bindingRepository;
    }

    @Transactional(readOnly = true)
    public List<ArchiveGovernanceSchemeResponse> listSchemes(@Nullable Boolean enabled) {
        return (enabled == null ? schemeRepository.list() : schemeRepository.list(enabled))
                .stream().map(this::toSchemeResponse).toList();
    }

    @Transactional(readOnly = true)
    public ArchiveGovernanceSchemeResponse getScheme(Long schemeId) {
        return toSchemeResponse(loadScheme(schemeId));
    }

    @Transactional
    public ArchiveGovernanceSchemeResponse createScheme(
            CreateArchiveGovernanceSchemeRequest request, Long userId) {
        String code = normalizeCode(request.schemeCode(), "schemeCode", "治理方案编码不能为空");
        String name = requiredText(request.schemeName(), "schemeName", "治理方案名称不能为空");
        if (schemeRepository.findBySchemeCode(code) != null) {
            throw new BadRequestException("治理方案编码已存在", "schemeCode", "治理方案编码已存在");
        }
        ArchiveGovernanceScheme scheme = new ArchiveGovernanceScheme();
        scheme.setSchemeCode(code);
        scheme.setSchemeName(name);
        scheme.setDescription(StringUtils.trimToNull(request.description()));
        scheme.setEnabled(request.enabled() == null || request.enabled());
        scheme.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        scheme.setCreatedBy(userId);
        scheme.setUpdatedBy(userId);
        return toSchemeResponse(schemeRepository.insert(scheme));
    }

    @Transactional
    public ArchiveGovernanceSchemeResponse updateScheme(
            Long schemeId, UpdateArchiveGovernanceSchemeRequest request, Long userId) {
        ArchiveGovernanceScheme scheme = loadScheme(schemeId);
        String code = normalizeCode(request.schemeCode(), "schemeCode", "治理方案编码不能为空");
        String name = requiredText(request.schemeName(), "schemeName", "治理方案名称不能为空");
        ArchiveGovernanceScheme existing = schemeRepository.findBySchemeCode(code);
        if (existing != null && !Objects.equals(existing.getId(), schemeId)) {
            throw new BadRequestException("治理方案编码已存在", "schemeCode", "治理方案编码已存在");
        }
        scheme.setSchemeCode(code);
        scheme.setSchemeName(name);
        scheme.setDescription(StringUtils.trimToNull(request.description()));
        scheme.setEnabled(request.enabled() == null || request.enabled());
        scheme.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        scheme.setUpdatedBy(userId);
        return toSchemeResponse(schemeRepository.update(scheme));
    }

    @Transactional
    public void deleteScheme(Long schemeId, Long userId) {
        ArchiveGovernanceScheme scheme = loadScheme(schemeId);
        List<ArchiveGovernanceSchemeVersion> versions = versionRepository.findBySchemeId(schemeId);
        if (!versions.isEmpty()) {
            throw new BadRequestException("治理方案存在版本，不能删除");
        }
        scheme.setUpdatedBy(userId);
        schemeRepository.update(scheme);
        schemeRepository.delete(scheme);
    }

    @Transactional(readOnly = true)
    public List<ArchiveGovernanceSchemeVersionResponse> listVersions(Long schemeId) {
        loadScheme(schemeId);
        return versionRepository.findBySchemeId(schemeId).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional
    public ArchiveGovernanceSchemeVersionResponse createVersion(
            Long schemeId, CreateArchiveGovernanceSchemeVersionRequest request, Long userId) {
        loadScheme(schemeId);
        String versionCode =
                normalizeCode(request.versionCode(), "versionCode", "治理方案版本号不能为空");
        if (versionRepository.findBySchemeIdAndVersionCode(schemeId, versionCode) != null) {
            throw new BadRequestException("治理方案版本号已存在", "versionCode", "治理方案版本号已存在");
        }
        ArchiveGovernanceSchemeVersion version = new ArchiveGovernanceSchemeVersion();
        version.setSchemeId(schemeId);
        version.setVersionCode(versionCode);
        version.setVersionDescription(StringUtils.trimToNull(request.versionDescription()));
        version.setStatus(ArchiveGovernanceSchemeVersionStatus.DRAFT);
        version.setCreatedBy(userId);
        version.setUpdatedBy(userId);
        return toVersionResponse(versionRepository.insert(version));
    }

    @Transactional
    public ArchiveGovernanceSchemeVersionResponse updateVersion(
            Long versionId, UpdateArchiveGovernanceSchemeVersionRequest request, Long userId) {
        ArchiveGovernanceSchemeVersion version = loadVersion(versionId);
        ArchiveGovernanceVersionPolicy.requireEditable(version.getStatus());
        String versionCode =
                normalizeCode(request.versionCode(), "versionCode", "治理方案版本号不能为空");
        ArchiveGovernanceSchemeVersion existing =
                versionRepository.findBySchemeIdAndVersionCode(version.getSchemeId(), versionCode);
        if (existing != null && !Objects.equals(existing.getId(), versionId)) {
            throw new BadRequestException("治理方案版本号已存在", "versionCode", "治理方案版本号已存在");
        }
        version.setVersionCode(versionCode);
        version.setVersionDescription(StringUtils.trimToNull(request.versionDescription()));
        version.setUpdatedBy(userId);
        return toVersionResponse(versionRepository.update(version));
    }

    @Transactional
    public ArchiveGovernanceSchemeVersionResponse publishVersion(Long versionId, Long userId) {
        ArchiveGovernanceSchemeVersion version = loadVersion(versionId);
        if (version.getStatus() != ArchiveGovernanceSchemeVersionStatus.DRAFT) {
            throw new BadRequestException("只有草稿治理方案版本可以发布");
        }
        validateBindings(versionId);
        version.setStatus(ArchiveGovernanceSchemeVersionStatus.PUBLISHED);
        version.setPublishedBy(userId);
        version.setPublishedAt(LocalDateTime.now());
        version.setUpdatedBy(userId);
        return toVersionResponse(versionRepository.update(version));
    }

    @Transactional
    public ArchiveGovernanceSchemeVersionResponse freezeVersion(Long versionId, Long userId) {
        ArchiveGovernanceSchemeVersion version = loadVersion(versionId);
        if (version.getStatus() != ArchiveGovernanceSchemeVersionStatus.PUBLISHED) {
            throw new BadRequestException("只有已发布治理方案版本可以冻结");
        }
        version.setStatus(ArchiveGovernanceSchemeVersionStatus.FROZEN);
        version.setFrozenBy(userId);
        version.setFrozenAt(LocalDateTime.now());
        version.setUpdatedBy(userId);
        return toVersionResponse(versionRepository.update(version));
    }

    @Transactional
    public ArchiveGovernanceSchemeVersionResponse retireVersion(Long versionId, Long userId) {
        ArchiveGovernanceSchemeVersion version = loadVersion(versionId);
        if (version.getStatus() != ArchiveGovernanceSchemeVersionStatus.FROZEN) {
            throw new BadRequestException("只有冻结治理方案版本可以退役");
        }
        version.setStatus(ArchiveGovernanceSchemeVersionStatus.RETIRED);
        version.setRetiredBy(userId);
        version.setRetiredAt(LocalDateTime.now());
        version.setUpdatedBy(userId);
        return toVersionResponse(versionRepository.update(version));
    }

    @Transactional(readOnly = true)
    public ArchiveGovernanceSchemeVersionResponse resolveDefaultVersion(
            @Nullable String fondsCode, @Nullable String categoryCode) {
        ArchiveGovernanceSchemeVersion version =
                resolveDefaultVersionEntity(
                        StringUtils.trimToNull(fondsCode), StringUtils.trimToNull(categoryCode));
        return toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public ArchiveGovernanceSchemeVersion requireDefaultVersionForNewArchive(
            @Nullable String fondsCode, @Nullable String categoryCode) {
        ArchiveGovernanceSchemeVersion version =
                resolveDefaultVersionEntity(
                        StringUtils.trimToNull(fondsCode), StringUtils.trimToNull(categoryCode));
        ArchiveGovernanceVersionPolicy.requireUsableForNewArchive(version.getStatus());
        return version;
    }

    @Transactional(readOnly = true)
    public List<ArchiveGovernanceScopeResponse> listScopes(Long versionId) {
        loadVersion(versionId);
        return scopeRepository.findBySchemeVersionId(versionId).stream()
                .map(this::toScopeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArchiveGovernanceBindingResponse> listBindings(Long versionId) {
        loadVersion(versionId);
        return bindingRepository.findBySchemeVersionId(versionId).stream()
                .map(this::toBindingResponse)
                .toList();
    }

    @Transactional
    public List<ArchiveGovernanceScopeResponse> replaceScopes(
            Long versionId, List<CreateArchiveGovernanceScopeRequest> requests, Long userId) {
        ArchiveGovernanceSchemeVersion version = loadVersion(versionId);
        ArchiveGovernanceVersionPolicy.requireEditable(version.getStatus());
        List<ArchiveGovernanceScope> existing = scopeRepository.findBySchemeVersionId(versionId);
        if (!existing.isEmpty()) {
            scopeRepository.deleteAll(existing);
        }
        List<ArchiveGovernanceScope> scopes =
                requests.stream()
                        .map(request -> toScope(versionId, request, userId))
                        .toList();
        return scopeRepository.insertAll(scopes).stream().map(this::toScopeResponse).toList();
    }

    @Transactional
    public List<ArchiveGovernanceBindingResponse> replaceBindings(
            Long versionId, List<CreateArchiveGovernanceBindingRequest> requests, Long userId) {
        ArchiveGovernanceSchemeVersion version = loadVersion(versionId);
        ArchiveGovernanceVersionPolicy.requireEditable(version.getStatus());
        List<ArchiveGovernanceBinding> existing = bindingRepository.findBySchemeVersionId(versionId);
        if (!existing.isEmpty()) {
            bindingRepository.deleteAll(existing);
        }
        List<ArchiveGovernanceBinding> bindings =
                requests.stream()
                        .map(request -> toBinding(versionId, request, userId))
                        .sorted(Comparator.comparingInt(ArchiveGovernanceBinding::getBindingOrder))
                        .toList();
        return bindingRepository.insertAll(bindings).stream().map(this::toBindingResponse).toList();
    }

    @Transactional(readOnly = true)
    public void requireTargetNotReferenced(ArchiveGovernanceBindingType bindingType, Long targetId) {
        if (bindingRepository.countProtectedByBindingTypeAndTargetId(bindingType, targetId) > 0) {
            throw new BadRequestException("该配置已被治理方案版本引用");
        }
    }

    private ArchiveGovernanceSchemeVersion resolveDefaultVersionEntity(
            @Nullable String fondsCode, @Nullable String categoryCode) {
        if (categoryCode != null) {
            ArchiveGovernanceSchemeVersion version =
                    findPublishedDefault(ArchiveGovernanceScopeType.CATEGORY, null, categoryCode);
            if (version != null) {
                return version;
            }
        }
        if (fondsCode != null) {
            ArchiveGovernanceSchemeVersion version =
                    findPublishedDefault(ArchiveGovernanceScopeType.FONDS, fondsCode, null);
            if (version != null) {
                return version;
            }
        }
        ArchiveGovernanceSchemeVersion version =
                findPublishedDefault(ArchiveGovernanceScopeType.GLOBAL, null, null);
        if (version == null) {
            throw new BadRequestException("无法解析到可用的治理方案版本");
        }
        return version;
    }

    private @Nullable ArchiveGovernanceSchemeVersion findPublishedDefault(
            ArchiveGovernanceScopeType scopeType,
            @Nullable String fondsCode,
            @Nullable String categoryCode) {
        for (ArchiveGovernanceScope scope :
                scopeRepository.findByScopeTypeAndDefaultFlag(scopeType, true)) {
            if (!Objects.equals(StringUtils.trimToNull(scope.getFondsCode()), fondsCode)) {
                continue;
            }
            if (!Objects.equals(StringUtils.trimToNull(scope.getCategoryCode()), categoryCode)) {
                continue;
            }
            ArchiveGovernanceSchemeVersion version =
                    versionRepository.findById(scope.getSchemeVersionId()).orElse(null);
            if (version != null
                    && version.getStatus() == ArchiveGovernanceSchemeVersionStatus.PUBLISHED) {
                return version;
            }
        }
        return null;
    }

    private void validateBindings(Long versionId) {
        for (ArchiveGovernanceBinding binding : bindingRepository.findBySchemeVersionId(versionId)) {
            if (binding.getTargetId() == null
                    && StringUtils.trimToNull(binding.getTargetCode()) == null) {
                throw new BadRequestException("治理方案绑定目标不能为空");
            }
        }
    }

    private ArchiveGovernanceScope toScope(
            Long versionId, CreateArchiveGovernanceScopeRequest request, Long userId) {
        ArchiveGovernanceScopeType scopeType =
                request.scopeType() == null ? ArchiveGovernanceScopeType.GLOBAL : request.scopeType();
        String fondsCode = StringUtils.trimToNull(request.fondsCode());
        String categoryCode = StringUtils.trimToNull(request.categoryCode());
        switch (scopeType) {
            case GLOBAL -> {
                if (fondsCode != null || categoryCode != null) {
                    throw new BadRequestException("全局适用范围不能配置全宗或分类");
                }
            }
            case FONDS -> {
                if (fondsCode == null || categoryCode != null) {
                    throw new BadRequestException("全宗适用范围必须且只能配置全宗编码");
                }
            }
            case CATEGORY -> {
                if (categoryCode == null) {
                    throw new BadRequestException("分类适用范围必须配置分类编码");
                }
            }
        }
        ArchiveGovernanceScope scope = new ArchiveGovernanceScope();
        scope.setSchemeVersionId(versionId);
        scope.setScopeType(scopeType);
        scope.setFondsCode(fondsCode);
        scope.setCategoryCode(categoryCode);
        scope.setDefaultFlag(request.defaultFlag() == null || request.defaultFlag());
        scope.setCreatedBy(userId);
        scope.setUpdatedBy(userId);
        return scope;
    }

    private ArchiveGovernanceBinding toBinding(
            Long versionId, CreateArchiveGovernanceBindingRequest request, Long userId) {
        if (request.bindingType() == null) {
            throw new BadRequestException("治理方案绑定类型不能为空", "bindingType", "治理方案绑定类型不能为空");
        }
        ArchiveGovernanceBinding binding = new ArchiveGovernanceBinding();
        binding.setSchemeVersionId(versionId);
        binding.setBindingType(request.bindingType());
        binding.setTargetType(StringUtils.trimToNull(request.targetType()));
        binding.setTargetId(request.targetId());
        binding.setTargetCode(StringUtils.trimToNull(request.targetCode()));
        binding.setBindingOrder(request.bindingOrder() == null ? 0 : request.bindingOrder());
        binding.setCreatedBy(userId);
        binding.setUpdatedBy(userId);
        return binding;
    }

    private ArchiveGovernanceScheme loadScheme(Long schemeId) {
        return schemeRepository.findById(schemeId).orElseThrow(() -> notFound("治理方案不存在"));
    }

    private ArchiveGovernanceSchemeVersion loadVersion(Long versionId) {
        return versionRepository.findById(versionId).orElseThrow(() -> notFound("治理方案版本不存在"));
    }

    private String normalizeCode(
            @Nullable String value, String field, String blankMessage) {
        String code = StringUtils.trimToNull(value);
        if (code == null) {
            throw new BadRequestException(blankMessage, field, blankMessage);
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BadRequestException("编码必须为小写 snake_case 或 kebab-case", field, "编码格式不合法");
        }
        return code;
    }

    private String requiredText(@Nullable String value, String field, String message) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            throw new BadRequestException(message, field, message);
        }
        return text;
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ArchiveGovernanceSchemeResponse toSchemeResponse(ArchiveGovernanceScheme scheme) {
        return new ArchiveGovernanceSchemeResponse(
                scheme.getId(),
                scheme.getSchemeCode(),
                scheme.getSchemeName(),
                scheme.getDescription(),
                scheme.isEnabled(),
                scheme.getSortOrder());
    }

    private ArchiveGovernanceSchemeVersionResponse toVersionResponse(
            ArchiveGovernanceSchemeVersion version) {
        return new ArchiveGovernanceSchemeVersionResponse(
                version.getId(),
                version.getSchemeId(),
                version.getVersionCode(),
                version.getVersionDescription(),
                version.getStatus(),
                version.getPublishedBy(),
                version.getPublishedAt(),
                version.getFrozenBy(),
                version.getFrozenAt(),
                version.getRetiredBy(),
                version.getRetiredAt());
    }

    private ArchiveGovernanceScopeResponse toScopeResponse(ArchiveGovernanceScope scope) {
        return new ArchiveGovernanceScopeResponse(
                scope.getId(),
                scope.getSchemeVersionId(),
                scope.getScopeType(),
                scope.getFondsCode(),
                scope.getCategoryCode(),
                scope.isDefaultFlag());
    }

    private ArchiveGovernanceBindingResponse toBindingResponse(ArchiveGovernanceBinding binding) {
        return new ArchiveGovernanceBindingResponse(
                binding.getId(),
                binding.getSchemeVersionId(),
                binding.getBindingType(),
                binding.getTargetType(),
                binding.getTargetId(),
                binding.getTargetCode(),
                binding.getBindingOrder());
    }

    public record CreateArchiveGovernanceSchemeRequest(
            String schemeCode,
            String schemeName,
            @Nullable String description,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record UpdateArchiveGovernanceSchemeRequest(
            String schemeCode,
            String schemeName,
            @Nullable String description,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record ArchiveGovernanceSchemeResponse(
            Long id,
            String schemeCode,
            String schemeName,
            @Nullable String description,
            boolean enabled,
            int sortOrder) {}

    public record CreateArchiveGovernanceSchemeVersionRequest(
            String versionCode, @Nullable String versionDescription) {}

    public record UpdateArchiveGovernanceSchemeVersionRequest(
            String versionCode, @Nullable String versionDescription) {}

    public record ArchiveGovernanceSchemeVersionResponse(
            Long id,
            Long schemeId,
            String versionCode,
            @Nullable String versionDescription,
            ArchiveGovernanceSchemeVersionStatus status,
            @Nullable Long publishedBy,
            @Nullable LocalDateTime publishedAt,
            @Nullable Long frozenBy,
            @Nullable LocalDateTime frozenAt,
            @Nullable Long retiredBy,
            @Nullable LocalDateTime retiredAt) {}

    public record CreateArchiveGovernanceScopeRequest(
            @Nullable ArchiveGovernanceScopeType scopeType,
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            @Nullable Boolean defaultFlag) {}

    public record ArchiveGovernanceScopeResponse(
            Long id,
            Long schemeVersionId,
            ArchiveGovernanceScopeType scopeType,
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            boolean defaultFlag) {}

    public record CreateArchiveGovernanceBindingRequest(
            ArchiveGovernanceBindingType bindingType,
            @Nullable String targetType,
            @Nullable Long targetId,
            @Nullable String targetCode,
            @Nullable Integer bindingOrder) {}

    public record ArchiveGovernanceBindingResponse(
            Long id,
            Long schemeVersionId,
            ArchiveGovernanceBindingType bindingType,
            @Nullable String targetType,
            @Nullable Long targetId,
            @Nullable String targetCode,
            int bindingOrder) {}
}
