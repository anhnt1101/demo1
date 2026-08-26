package com.example.demo.repository;

import com.example.demo.entity.GroupCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Repository
public interface GroupCategoryRepository extends JpaRepository<GroupCategory,Long>,JpaSpecificationExecutor<GroupCategory> {

    Page<GroupCategory> findAllByOrderByUpdateDateDesc(Pageable pageable);

    boolean existsByParamValue(String paramValue);

    boolean existsByParamType(String paramType);

    @Query("""
    SELECT COUNT(g) > 0
    FROM GroupCategory g
    WHERE g.effectiveDate <= :endEffectiveDate
      AND g.endEffectiveDate >= :effectiveDate
    """)
    boolean existsOverlappingDate(
            @Param("effectiveDate") Date effectiveDate,
            @Param("endEffectiveDate") Date endEffectiveDate
    );

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE GroupCategory g SET g.isActive = 0
        WHERE g.isActive = 1
          AND (g.effectiveDate > :now
               OR (g.endEffectiveDate IS NOT NULL AND g.endEffectiveDate <= :now))
        """)
    int deactivateExpiredOrNotYetEffective(@Param("now") Date now);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE GroupCategory g SET g.isActive = 1
        WHERE g.isActive = 0
          AND g.effectiveDate <= :now
          AND (g.endEffectiveDate IS NULL OR g.endEffectiveDate > :now)
        """)
    int activateNowInEffect(@Param("now") Date now);



}