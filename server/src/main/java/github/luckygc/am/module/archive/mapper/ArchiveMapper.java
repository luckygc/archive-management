package github.luckygc.am.module.archive.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

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
            @Param("fieldScope") String fieldScope,
            @Param("tableName") String tableName,
            @Param("tableStatus") String tableStatus,
            @Param("userId") Long userId);

    List<Map<String, Object>> listItemOverview();

    List<Map<String, Object>> listRelatedFilterCategories(@Param("categoryId") Long categoryId);

    List<Map<String, Object>> listDynamicItems(
            @Param("source") ArchiveDynamicItemSource source,
            @Param("projection") ArchiveDynamicItemProjection projection,
            @Param("criteria") ArchiveDynamicItemCriteria criteria,
            @Param("page") ArchiveDynamicItemPageWindow page);

    int countDynamicItems(
            @Param("source") ArchiveDynamicItemSource source,
            @Param("criteria") ArchiveDynamicItemCriteria criteria);

    List<Map<String, Object>> listItemsForSearchRebuild(
            @Param("tableName") String tableName,
            @Param("selectColumns") String selectColumns,
            @Param("archiveLevel") String archiveLevel);

    Long insertArchiveItem(
            @Param("archiveLevel") String archiveLevel,
            @Param("volumeId") Long volumeId,
            @Param("fondsCode") String fondsCode,
            @Param("fondsName") String fondsName,
            @Param("categoryCode") String categoryCode,
            @Param("categoryName") String categoryName,
            @Param("archiveNo") String archiveNo,
            @Param("electronicStatus") String electronicStatus,
            @Param("securityLevelId") Long securityLevelId,
            @Param("retentionPeriodId") Long retentionPeriodId,
            @Param("archiveYear") int archiveYear,
            @Param("governanceSchemeVersionId") Long governanceSchemeVersionId,
            @Param("userId") Long userId);

    int countArchiveItemsByArchiveNo(
            @Param("categoryCode") String categoryCode,
            @Param("archiveNo") String archiveNo,
            @Param("excludedId") @Nullable Long excludedId);

    int insertDynamicRecord(
            @Param("tableName") String tableName,
            @Param("columns") String columns,
            @Param("values") List<Object> values);

    int updateArchiveItem(
            @Param("id") Long id,
            @Param("volumeId") Long volumeId,
            @Param("fondsCode") String fondsCode,
            @Param("fondsName") String fondsName,
            @Param("archiveNo") String archiveNo,
            @Param("electronicStatus") String electronicStatus,
            @Param("securityLevelId") Long securityLevelId,
            @Param("retentionPeriodId") Long retentionPeriodId,
            @Param("archiveYear") int archiveYear,
            @Param("userId") Long userId);

    int updateDynamicRecord(
            @Param("tableName") String tableName,
            @Param("id") Long id,
            @Param("assignments") List<ArchiveSqlAssignment> assignments);

    int markDynamicRecordDeleted(
            @Param("tableName") String tableName,
            @Param("id") Long id,
            @Param("userId") Long userId);

    Map<String, Object> loadDynamicRecord(
            @Param("tableName") String tableName, @Param("id") Long id);

    Map<String, Object> getArchiveItem(@Param("id") Long id);

    List<Map<String, Object>> listArchiveVolumes(
            @Param("fondsCode") String fondsCode, @Param("categoryCode") String categoryCode);

    Map<String, Object> getArchiveVolume(@Param("id") Long id);

    Long insertArchiveVolume(
            @Param("fondsCode") String fondsCode,
            @Param("fondsName") String fondsName,
            @Param("categoryCode") String categoryCode,
            @Param("categoryName") String categoryName,
            @Param("archiveNo") String archiveNo,
            @Param("electronicStatus") String electronicStatus,
            @Param("archiveYear") int archiveYear,
            @Param("governanceSchemeVersionId") Long governanceSchemeVersionId,
            @Param("userId") Long userId);

    int countArchiveVolumesByArchiveNo(
            @Param("categoryCode") String categoryCode,
            @Param("archiveNo") String archiveNo,
            @Param("excludedId") @Nullable Long excludedId);

    List<Map<String, Object>> listArchiveItemElectronicFiles(
            @Param("archiveItemId") Long archiveItemId);

    Long insertArchiveItemElectronicFile(
            @Param("archiveItemId") Long archiveItemId,
            @Param("storageObjectId") Long storageObjectId,
            @Param("usageType") String usageType,
            @Param("displayOrder") int displayOrder,
            @Param("userId") Long userId);

    int deleteArchiveItemElectronicFile(
            @Param("archiveItemId") Long archiveItemId,
            @Param("electronicFileId") Long electronicFileId);

    Long getArchiveItemElectronicFileStorageObjectId(
            @Param("archiveItemId") Long archiveItemId,
            @Param("electronicFileId") Long electronicFileId);

    int markArchiveItemDeleted(@Param("id") Long id, @Param("userId") Long userId);

    int lockArchiveItem(
            @Param("id") Long id,
            @Param("lockReason") String lockReason,
            @Param("lockedBy") Long lockedBy);

    int unlockArchiveItem(@Param("id") Long id, @Param("userId") Long userId);

    int insertSearchProjection(
            @Param("archiveItemId") Long archiveItemId,
            @Param("searchText") String searchText,
            @Param("indexVersion") int indexVersion);

    int deleteSearchProjection(@Param("archiveItemId") Long archiveItemId);

    int insertSearchOutbox(
            @Param("archiveItemId") Long archiveItemId, @Param("eventType") String eventType);

    List<Map<String, Object>> listPendingSearchOutbox(@Param("limit") int limit);

    int markSearchOutboxProcessed(@Param("id") Long id);

    int markSearchOutboxFailed(@Param("id") Long id, @Param("lastError") String lastError);

    List<Map<String, Object>> listUniqueConstraints(@Param("categoryId") Long categoryId);

    Map<String, Object> getUniqueConstraint(@Param("id") Long id);

    Long insertUniqueConstraint(
            @Param("categoryId") Long categoryId,
            @Param("archiveLevel") String archiveLevel,
            @Param("constraintCode") String constraintCode,
            @Param("constraintName") String constraintName,
            @Param("indexName") String indexName,
            @Param("enabled") boolean enabled,
            @Param("userId") Long userId);

    int updateUniqueConstraint(
            @Param("id") Long id,
            @Param("categoryId") Long categoryId,
            @Param("archiveLevel") String archiveLevel,
            @Param("constraintCode") String constraintCode,
            @Param("constraintName") String constraintName,
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

    int moveItemToVolume(
            @Param("volumeId") Long volumeId,
            @Param("archiveItemId") Long archiveItemId,
            @Param("displayOrder") int displayOrder,
            @Param("userId") Long userId);

    List<Map<String, Object>> listItemRelations(@Param("sourceItemId") Long sourceItemId);

    Long insertItemRelation(
            @Param("sourceItemId") Long sourceItemId, @Param("targetItemId") Long targetItemId);

    int deleteItemRelation(@Param("id") Long id, @Param("sourceItemId") Long sourceItemId);

    List<Map<String, Object>> listItemLineTables(@Param("categoryId") Long categoryId);

    Map<String, Object> getItemLineTable(@Param("id") Long id);

    Long insertItemLineTable(
            @Param("categoryId") Long categoryId,
            @Param("tableCode") String tableCode,
            @Param("tableName") String tableName,
            @Param("physicalTableName") String physicalTableName,
            @Param("sortOrder") int sortOrder,
            @Param("userId") Long userId);

    int updateItemLineTablePhysicalName(
            @Param("id") Long id,
            @Param("physicalTableName") String physicalTableName,
            @Param("userId") Long userId);

    List<Map<String, Object>> listItemLineFields(@Param("lineTableId") Long lineTableId);

    Long insertItemLineField(
            @Param("lineTableId") Long lineTableId,
            @Param("fieldCode") String fieldCode,
            @Param("fieldName") String fieldName,
            @Param("fieldType") String fieldType,
            @Param("columnName") String columnName,
            @Param("exactSearchable") boolean exactSearchable,
            @Param("sortOrder") int sortOrder,
            @Param("userId") Long userId);

    List<Map<String, Object>> listItemLineRows(
            @Param("tableName") String tableName, @Param("itemId") Long itemId);
}
