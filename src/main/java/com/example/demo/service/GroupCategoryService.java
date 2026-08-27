package com.example.demo.service;

import com.example.demo.entity.GroupCategory;
import com.example.demo.dto.Request.GroupCategoryRequest;
import com.example.demo.dto.Request.SearchRequest;
import org.springframework.data.domain.Page;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

public interface GroupCategoryService {

    Page<GroupCategory> fillAll(SearchRequest searchRequest);

    GroupCategory getByID(Long id);

    GroupCategory add(GroupCategoryRequest groupCategoryRequest);

    GroupCategory update(GroupCategoryRequest groupCategoryRequest);

    GroupCategory delete(Long id);

    void updateIsActiveByEffectiveDate();

    Map<String, Object> submitApproval(List<Long> ids, Integer newStatus);

    Map<String, Object> approve(List<Long> ids, Integer newStatus);

    Map<String, Object> cancelApprove(List<Long> ids, Integer newStatus);

    Map<String, Object> reject(List<Long> ids, Integer newStatus);

    Page<GroupCategory> searchSpecification(SearchRequest searchRequest);

    Page<GroupCategory> searchNativeQuery(SearchRequest searchRequest);

    Page<GroupCategory> searchProcedure(SearchRequest searchRequest);

    ByteArrayInputStream exportAll(SearchRequest searchRequest);

}
