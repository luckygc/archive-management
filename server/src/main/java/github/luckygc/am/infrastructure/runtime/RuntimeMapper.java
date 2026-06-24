package github.luckygc.am.infrastructure.runtime;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface RuntimeMapper {

    Long insertRuntimeJob(
            @Param("queueName") String queueName,
            @Param("specVersion") String specVersion,
            @Param("messageId") String messageId,
            @Param("messageSource") String messageSource,
            @Param("messageType") String messageType,
            @Param("messageSubject") String messageSubject,
            @Param("dataContentType") String dataContentType,
            @Param("dataJson") String dataJson,
            @Param("messageTime") LocalDateTime messageTime,
            @Param("availableAt") LocalDateTime availableAt);

    Map<String, Object> claimRuntimeJob(
            @Param("queueName") String queueName,
            @Param("workerId") String workerId,
            @Param("leaseSeconds") long leaseSeconds);

    int completeRuntimeJob(@Param("id") Long id, @Param("workerId") String workerId);

    int failRuntimeJob(
            @Param("id") Long id,
            @Param("workerId") String workerId,
            @Param("lastError") String lastError,
            @Param("nextAvailableAt") LocalDateTime nextAvailableAt,
            @Param("maxAttempts") int maxAttempts);

    int updateRuntimeLock(
            @Param("lockName") String lockName,
            @Param("ownerId") String ownerId,
            @Param("leaseSeconds") long leaseSeconds);

    int insertRuntimeLock(
            @Param("lockName") String lockName,
            @Param("ownerId") String ownerId,
            @Param("leaseSeconds") long leaseSeconds);

    int releaseRuntimeLock(@Param("lockName") String lockName, @Param("ownerId") String ownerId);
}
