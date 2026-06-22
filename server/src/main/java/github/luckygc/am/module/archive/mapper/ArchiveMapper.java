package github.luckygc.am.module.archive.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArchiveMapper {

    List<Map<String, Object>> listFonds(@Param("enabled") Boolean enabled);

    Long insertFonds(
            @Param("fondsCode") String fondsCode,
            @Param("fondsName") String fondsName,
            @Param("enabled") boolean enabled,
            @Param("sortOrder") int sortOrder);

    int updateFonds(
            @Param("id") Long id,
            @Param("fondsCode") String fondsCode,
            @Param("fondsName") String fondsName,
            @Param("enabled") boolean enabled,
            @Param("sortOrder") int sortOrder);

    int deleteFonds(@Param("id") Long id);

    Map<String, Object> getFonds(@Param("id") Long id);

    Map<String, Object> getFondsByCode(@Param("fondsCode") String fondsCode);

    List<Map<String, Object>> listCategories(@Param("enabled") Boolean enabled);

    Long insertCategory(
            @Param("parentId") Long parentId,
            @Param("categoryCode") String categoryCode,
            @Param("categoryName") String categoryName,
            @Param("managementMode") String managementMode,
            @Param("enabled") boolean enabled,
            @Param("sortOrder") int sortOrder);

    int updateCategory(
            @Param("id") Long id,
            @Param("parentId") Long parentId,
            @Param("categoryCode") String categoryCode,
            @Param("categoryName") String categoryName,
            @Param("managementMode") String managementMode,
            @Param("enabled") boolean enabled,
            @Param("sortOrder") int sortOrder);

    int deleteCategory(@Param("id") Long id);

    Map<String, Object> getCategory(@Param("id") Long id);

    Long findParentId(@Param("id") Long id);

    int countChildCategories(@Param("categoryId") Long categoryId);

    List<Map<String, Object>> listFields(
            @Param("categoryId") Long categoryId,
            @Param("archiveLevel") String archiveLevel,
            @Param("enabled") Boolean enabled);

    Long insertField(
            @Param("categoryId") Long categoryId,
            @Param("archiveLevel") String archiveLevel,
            @Param("fieldCode") String fieldCode,
            @Param("fieldName") String fieldName,
            @Param("fieldType") String fieldType,
            @Param("columnName") String columnName,
            @Param("textLength") Integer textLength,
            @Param("decimalPrecision") Integer decimalPrecision,
            @Param("decimalScale") Integer decimalScale,
            @Param("editControl") String editControl,
            @Param("listVisible") boolean listVisible,
            @Param("listWidth") Integer listWidth,
            @Param("listSortOrder") int listSortOrder,
            @Param("detailVisible") boolean detailVisible,
            @Param("detailColSpan") int detailColSpan,
            @Param("detailSortOrder") int detailSortOrder,
            @Param("editVisible") boolean editVisible,
            @Param("editColSpan") int editColSpan,
            @Param("editSortOrder") int editSortOrder,
            @Param("exactSearchable") boolean exactSearchable,
            @Param("fullTextSearchable") boolean fullTextSearchable,
            @Param("enabled") boolean enabled,
            @Param("sortOrder") int sortOrder);

    int updateField(
            @Param("id") Long id,
            @Param("categoryId") Long categoryId,
            @Param("archiveLevel") String archiveLevel,
            @Param("fieldCode") String fieldCode,
            @Param("fieldName") String fieldName,
            @Param("fieldType") String fieldType,
            @Param("columnName") String columnName,
            @Param("textLength") Integer textLength,
            @Param("decimalPrecision") Integer decimalPrecision,
            @Param("decimalScale") Integer decimalScale,
            @Param("editControl") String editControl,
            @Param("listVisible") boolean listVisible,
            @Param("listWidth") Integer listWidth,
            @Param("listSortOrder") int listSortOrder,
            @Param("detailVisible") boolean detailVisible,
            @Param("detailColSpan") int detailColSpan,
            @Param("detailSortOrder") int detailSortOrder,
            @Param("editVisible") boolean editVisible,
            @Param("editColSpan") int editColSpan,
            @Param("editSortOrder") int editSortOrder,
            @Param("exactSearchable") boolean exactSearchable,
            @Param("fullTextSearchable") boolean fullTextSearchable,
            @Param("enabled") boolean enabled,
            @Param("sortOrder") int sortOrder);

    int deleteField(@Param("id") Long id, @Param("categoryId") Long categoryId);

    Map<String, Object> getField(@Param("id") Long id);

    int tableExists(@Param("tableName") String tableName);

    int columnExists(@Param("tableName") String tableName, @Param("columnName") String columnName);

    int indexExists(@Param("indexName") String indexName);

    int executeSql(@Param("sql") String sql);

    int updateCategoryTableStatus(
            @Param("id") Long id,
            @Param("archiveLevel") String archiveLevel,
            @Param("tableName") String tableName,
            @Param("tableStatus") String tableStatus);

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
            @Param("archiveYear") int archiveYear);

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
            @Param("archiveYear") int archiveYear);

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

    int markArchiveRecordDeleted(@Param("id") Long id);

    int lockArchiveRecord(
            @Param("id") Long id,
            @Param("lockReason") String lockReason,
            @Param("lockedBy") Long lockedBy);

    int unlockArchiveRecord(@Param("id") Long id);

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
            @Param("enabled") boolean enabled);

    int updateUniqueConstraint(
            @Param("id") Long id,
            @Param("categoryId") Long categoryId,
            @Param("archiveLevel") String archiveLevel,
            @Param("constraintCode") String constraintCode,
            @Param("constraintName") String constraintName,
            @Param("includeFonds") boolean includeFonds,
            @Param("indexName") String indexName,
            @Param("enabled") boolean enabled);

    int deleteUniqueConstraint(@Param("id") Long id, @Param("categoryId") Long categoryId);

    int deleteUniqueConstraintFields(@Param("constraintId") Long constraintId);

    int insertUniqueConstraintField(
            @Param("constraintId") Long constraintId,
            @Param("fieldId") Long fieldId,
            @Param("fieldOrder") int fieldOrder);

    List<Map<String, Object>> listUniqueConstraintFields(@Param("constraintId") Long constraintId);

    int markFieldsExactSearchable(
            @Param("categoryId") Long categoryId, @Param("fieldIds") List<Long> fieldIds);

    List<Map<String, Object>> listFieldLayouts(
            @Param("categoryId") Long categoryId,
            @Param("archiveLevel") String archiveLevel,
            @Param("surface") String surface,
            @Param("ownerUserId") Long ownerUserId,
            @Param("publicLayout") boolean publicLayout);

    int deleteFieldLayouts(
            @Param("categoryId") Long categoryId,
            @Param("archiveLevel") String archiveLevel,
            @Param("surface") String surface,
            @Param("ownerUserId") Long ownerUserId,
            @Param("publicLayout") boolean publicLayout);

    int insertFieldLayout(
            @Param("categoryId") Long categoryId,
            @Param("surface") String surface,
            @Param("ownerUserId") Long ownerUserId,
            @Param("fieldId") Long fieldId,
            @Param("visible") boolean visible,
            @Param("listWidth") Integer listWidth,
            @Param("colSpan") int colSpan,
            @Param("rowOrder") int rowOrder,
            @Param("colOrder") int colOrder);

    int moveRecordToVolume(
            @Param("volumeId") Long volumeId,
            @Param("archiveRecordId") Long archiveRecordId,
            @Param("displayOrder") int displayOrder);
}
