package github.luckygc.am.module.archive.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import github.luckygc.am.module.archive.rule.ArchiveRuleDefinition;

@Mapper
public interface ArchiveRuleMapper {

    List<ArchiveRuleDefinition> listExecutableRules(
            @Param("criteria") ArchiveRuleExecutionCriteria criteria);

    List<Map<String, Object>> listRuleTraces(
            @Param("criteria") ArchiveRuleTraceSearchCriteria criteria);
}
