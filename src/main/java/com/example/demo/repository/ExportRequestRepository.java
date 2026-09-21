package com.example.demo.repository;

import com.example.demo.entity.ExportRequest;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExportRequestRepository extends JpaRepository<ExportRequest, Long> {

    /*
     * Lấy các request NEW cũ nhất.
     */
//    @Query(value = """
//            SELECT ID
//            FROM (
//                SELECT ID
//                FROM EXPORT_REQUEST
//                WHERE EXPORT_STATUS = 'NEW'
//                ORDER BY CREATED_DATE ASC, ID ASC
//            )
//            WHERE ROWNUM <= :limit
//            """, nativeQuery = true)
//    List<Long> findNextPendingIds(@Param("limit") int limit);

    /*
     * Atomic claim.
     *
     * 2 worker cùng claim một ID:
     * chỉ một worker UPDATE được 1 row.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE EXPORT_REQUEST
            SET EXPORT_STATUS = 'PROCESSING',
                STARTED_DATE = SYSTIMESTAMP,
                ERROR_MESSAGE = NULL
            WHERE ID = :id
              AND EXPORT_STATUS = 'NEW'
            """, nativeQuery = true)
    int claim(@Param("id") Long id);

    /*
     * Executor reject sau khi đã claim.
     */
//    @Transactional
//    @Modifying
//    @Query(value = """
//            UPDATE EXPORT_REQUEST
//            SET EXPORT_STATUS = 'NEW',
//                STARTED_DATE = NULL
//            WHERE ID = :id
//              AND EXPORT_STATUS = 'PROCESSING'
//            """, nativeQuery = true)
//    int resetToNew(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE EXPORT_REQUEST
            SET EXPORT_STATUS = 'COMPLETED',
                FILE_NAME = :fileName,
                OBJECT_KEY = :objectKey,
                PATH = :path,
                COMPLETED_DATE = SYSTIMESTAMP,
                ERROR_MESSAGE = NULL
            WHERE ID = :id
              AND EXPORT_STATUS = 'PROCESSING'
            """, nativeQuery = true)
    int markCompleted(@Param("id") Long id, @Param("fileName") String fileName, @Param("objectKey") String objectKey, @Param("path") String path);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE EXPORT_REQUEST
            SET EXPORT_STATUS = 'ERROR',
                ERROR_MESSAGE = :message,
                COMPLETED_DATE = SYSTIMESTAMP
            WHERE ID = :id
              AND EXPORT_STATUS = 'PROCESSING'
            """, nativeQuery = true)
    int markError(@Param("id") Long id, @Param("message") String message);


    @Transactional
    @Modifying
    @Query(value = """
            UPDATE EXPORT_REQUEST
            SET DOWNLOAD_STATUS = 'DOWNLOADED',
                DOWNLOADED_DATE = SYSTIMESTAMP
            WHERE ID = :id
              AND EXPORT_STATUS = 'COMPLETED'
            """, nativeQuery = true)
    int markDownloaded(@Param("id") Long id);


//    @Query(value = """
//            SELECT ID
//            FROM EXPORT_REQUEST
//            WHERE EXPORT_STATUS = 'PROCESSING'
//              AND STARTED_DATE < :staleBefore
//            """, nativeQuery = true)
//    List<Long> findStaleProcessingIds(@Param("staleBefore") LocalDateTime staleBefore);

    Optional<ExportRequest> findByIdAndUserId(Long id, Long userID);

    List<ExportRequest> findAllByUserIdOrderByCreatedDateDesc(Long userId);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE EXPORT_REQUEST
            SET EXPORT_STATUS = 'ERROR',
                ERROR_MESSAGE = :message,
                COMPLETED_DATE = SYSTIMESTAMP
            WHERE ID = :id
              AND EXPORT_STATUS = 'NEW'
            """, nativeQuery = true)
    int markNewError(@Param("id") Long id, @Param("message") String message);

}