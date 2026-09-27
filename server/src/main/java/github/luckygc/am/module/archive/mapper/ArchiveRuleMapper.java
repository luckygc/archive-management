package github.luckygc.am.module.archive.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinition;

@Mapper
public interface ArchiveRuleMapper {

    List<ArchiveRuntimeDefinition> listExecutableRuntimeDefinitions(
            @Param("criteria") ArchiveRuntimeExecutionCriteria criteria);

    List<Map<String, Object>> listRuntimeTraces(
            @Param("criteria") ArchiveRuntimeTraceSearchCriteria criteria);
}
