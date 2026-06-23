package github.luckygc.am.module.archive.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArchiveMapper {

    Long findParentId(@Param("id") Long id);

    int countChildCategories(@Param("categoryId") Long categoryId);

    int tableExists(@Param("tableName") String tableName);

    int columnExists(@Param("tableName") String tableName, @Param("columnName") String columnName);

    int indexExists(@Param("indexName") String indexName);

    int executeSql(@Param("sql") String sql);

    int updateCategoryTableStatus(
            @Param("id") Long id,
            @Param("archiveLevel") String archiveLevel,
            @Param("tableName") String tableName,
            @Param("tableStatus") String tableStatus,
            @Param("userId") Long userId);

    List<Map<String, Object>> listRecordOverview();

    List<Map<String, Object>> listDynamicRecords(
            @Param("tableName") String tableName,
            @Param("selectColumns") String selectColumns,
            @Param("archiveLevel") String archiveLevel,
            @Param("fondsCode") String fondsCode,
            @Param("conditions") List<ArchiveSqlCondition> conditions,
            @Param("recordIds") List<Long> recordIds);

    List<Map<String, Object>> listRecordsForSearchRebuild(
            @Param("tableName") String tableName,
            @Param("selectColumns") String selectColumns,
            @Param("archiveLevel") String archiveLevel);

    Long insertArchiveRecord(
            @Param("archiveLevel") String archiveLevel,
            @Param("parentId") Long parentId,
            @Param("fondsCode") String fondsCode,
            @Param("fondsName") String fondsName,
            @Param("categoryCode") String categoryCode,
            @Param("categoryName") String categoryName,
            @Param("archiveNo") String archiveNo,
            @Param("electronicStatus") String electronicStatus,
            @Param("archiveYear") int archiveYear,
            @Param("userId") Long userId);

    int insertDynamicRecord(
            @Param("tableName") String tableName,
            @Param("columns") String columns,
            @Param("values") List<Object> values);

    int updateArchiveRecord(
            @Param("id") Long id,
            @Param("parentId") Long parentId,
            @Param("fondsCode") String fondsCode,
            @Param("fondsName") String fondsName,
            @Param("archiveNo") String archiveNo,
            @Param("electronicStatus") String electronicStatus,
            @Param("archiveYear") int archiveYear,
            @Param("userId") Long userId);

    int updateDynamicRecord(
            @Param("tableName") String tableName,
            @Param("id") Long id,
            @Param("assignments") List<ArchiveSqlAssignment> assignments);

    int markDynamicRecordDeleted(@Param("tableName") String tableName, @Param("id") Long id);

    Map<String, Object> loadDynamicRecord(
            @Param("tableName") String tableName, @Param("id") Long id);

    Map<String, Object> getArchiveRecord(@Param("id") Long id);

    Map<String, Object> getPhysicalObjectByRecordId(@Param("archiveRecordId") Long archiveRecordId);

    int upsertPhysicalObject(
            @Param("archiveRecordId") Long archiveRecordId,
            @Param("physicalStatus") String physicalStatus,
            @Param("boxNo") String boxNo,
            @Param("locationNo") String locationNo,
            @Param("barcode") String barcode,
            @Param("remark") String remark,
            @Param("userId") Long userId);

    int insertRecordAudit(
            @Param("sourceTableName") String sourceTableName,
            @Param("sourceRecordId") Long sourceRecordId,
            @Param("archiveRecordId") Long archiveRecordId,
            @Param("fondsCode") String fondsCode,
            @Param("categoryCode") String categoryCode,
            @Param("operationType") String operationType,
            @Param("operationReason") String operationReason,
            @Param("operatedBy") Long operatedBy);

    int markArchiveRecordDeleted(@Param("id") Long id, @Param("userId") Long userId);

    int lockArchiveRecord(
            @Param("id") Long id,
            @Param("lockReason") String lockReason,
            @Param("lockedBy") Long lockedBy);

    int unlockArchiveRecord(@Param("id") Long id, @Param("userId") Long userId);

    int insertSearchProjection(
            @Param("archiveRecordId") Long archiveRecordId,
            @Param("searchText") String searchText,
            @Param("indexVersion") int indexVersion);

    int deleteSearchProjection(@Param("archiveRecordId") Long archiveRecordId);

    int insertSearchOutbox(
            @Param("archiveRecordId") Long archiveRecordId, @Param("eventType") String eventType);

    List<Map<String, Object>> listPendingSearchOutbox(@Param("limit") int limit);

    int markSearchOutboxProcessed(@Param("id") Long id);

    int markSearchOutboxFailed(@Param("id") Long id, @Param("lastError") String lastError);

    List<Map<String, Object>> searchRecordIds(@Param("keyword") String keyword);

    List<Map<String, Object>> listUniqueConstraints(@Param("categoryId") Long categoryId);

    Map<String, Object> getUniqueConstraint(@Param("id") Long id);

    Long insertUniqueConstraint(
            @Param("categoryId") Long categoryId,
            @Param("archiveLevel") String archiveLevel,
            @Param("constraintCode") String constraintCode,
            @Param("constraintName") String constraintName,
            @Param("includeFonds") boolean includeFonds,
            @Param("indexName") String indexName,
            @Param("enabled") boolean enabled,
            @Param("userId") Long userId);

    int updateUniqueConstraint(
            @Param("id") Long id,
            @Param("categoryId") Long categoryId,
            @Param("archiveLevel") String archiveLevel,
            @Param("constraintCode") String constraintCode,
            @Param("constraintName") String constraintName,
            @Param("includeFonds") boolean includeFonds,
            @Param("indexName") String indexName,
            @Param("enabled") boolean enabled,
            @Param("userId") Long userId);

    int deleteUniqueConstraint(
            @Param("id") Long id,
            @Param("categoryId") Long categoryId,
            @Param("userId") Long userId);

    int deleteUniqueConstraintFields(@Param("constraintId") Long constraintId);

    int insertUniqueConstraintField(
            @Param("constraintId") Long constraintId,
            @Param("fieldId") Long fieldId,
            @Param("fieldOrder") int fieldOrder);

    List<Map<String, Object>> listUniqueConstraintFields(@Param("constraintId") Long constraintId);

    int markFieldsExactSearchable(
            @Param("categoryId") Long categoryId,
            @Param("fieldIds") List<Long> fieldIds,
            @Param("userId") Long userId);

    int moveRecordToVolume(
            @Param("volumeId") Long volumeId,
            @Param("archiveRecordId") Long archiveRecordId,
            @Param("displayOrder") int displayOrder,
            @Param("userId") Long userId);
}
