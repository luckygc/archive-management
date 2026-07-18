package github.luckygc.am.module.archive.rule.service;

import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeStatus;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeActionDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeDefinitionDataRepository;

@Service
public class ArchiveRuntimeFieldReferenceService {

    private final ArchiveRuntimeDefinitionDataRepository definitionRepository;
    private final ArchiveRuntimeActionDataRepository actionRepository;

    public ArchiveRuntimeFieldReferenceService(
            ArchiveRuntimeDefinitionDataRepository definitionRepository,
            ArchiveRuntimeActionDataRepository actionRepository) {
        this.definitionRepository = definitionRepository;
        this.actionRepository = actionRepository;
    }

    @Transactional(readOnly = true)
    public void requireDeleteAllowed(
            String categoryCode, ArchiveFieldScope fieldScope, String fieldCode) {
        requireNotReferenced(categoryCode, runtimeFieldCode(fieldScope, fieldCode));
    }

    @Transactional(readOnly = true)
    public void requireUpdateAllowed(
            String categoryCode,
            ArchiveLevel currentLevel,
            ArchiveFieldScope currentScope,
            String currentCode,
            ArchiveFieldType currentType,
            boolean currentEnabled,
            boolean currentWritable,
            ArchiveLevel nextLevel,
            ArchiveFieldScope nextScope,
            String nextCode,
            ArchiveFieldType nextType,
            boolean nextEnabled,
            boolean nextWritable) {
        boolean destructive =
                currentLevel != nextLevel
                        || currentScope != nextScope
                        || !Objects.equals(currentCode, nextCode)
                        || currentType != nextType
                        || (currentEnabled && !nextEnabled)
                        || (currentWritable && !nextWritable);
        if (destructive) {
            requireNotReferenced(categoryCode, runtimeFieldCode(currentScope, currentCode));
        }
    }

    private void requireNotReferenced(String categoryCode, String runtimeFieldCode) {
        for (ArchiveRuntimeDefinition definition :
                definitionRepository.findByStatus(ArchiveRuntimeStatus.PUBLISHED)) {
            if (definition.getScopeCategoryCode() != null
                    && !definition.getScopeCategoryCode().equals(categoryCode)) {
                continue;
            }
            if (containsFieldReference(definition.getConditionJson(), runtimeFieldCode)
                    || containsActionReference(definition, runtimeFieldCode)) {
                throw new BadRequestException(
                        "字段已被已发布运行时定义引用："
                                + definition.getDefinitionCode()
                                + " / "
                                + runtimeFieldCode);
            }
        }
    }

    private boolean containsActionReference(
            ArchiveRuntimeDefinition definition, String runtimeFieldCode) {
        for (ArchiveRuntimeAction action :
                actionRepository.findByDefinitionId(definition.getId())) {
            if (Objects.equals(action.getActionParams().get("field"), runtimeFieldCode)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsFieldReference(Object value, String runtimeFieldCode) {
        if (value instanceof Map<?, ?> map) {
            if (Objects.equals(map.get("field"), runtimeFieldCode)) return true;
            for (Object nested : map.values()) {
                if (containsFieldReference(nested, runtimeFieldCode)) return true;
            }
        } else if (value instanceof Iterable<?> values) {
            for (Object nested : values) {
                if (containsFieldReference(nested, runtimeFieldCode)) return true;
            }
        }
        return false;
    }

    private String runtimeFieldCode(ArchiveFieldScope fieldScope, String fieldCode) {
        return (fieldScope == ArchiveFieldScope.METADATA ? "metadata." : "physical.") + fieldCode;
    }
}
