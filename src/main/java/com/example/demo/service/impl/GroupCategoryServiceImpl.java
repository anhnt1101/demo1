package com.example.demo.service.impl;

import Event.GroupCategoryChangedEvent;
import com.example.demo.entity.GroupCategory;
import com.example.demo.repository.GroupCategoryRepository;
import com.example.demo.dto.GroupCategoryRequest;
import com.example.demo.dto.SearchRequest;
import com.example.demo.service.GroupCategoryService;
import com.example.demo.utils.ExcelBase;
import jakarta.persistence.*;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GroupCategoryServiceImpl implements GroupCategoryService {

    private final GroupCategoryRepository groupCategoryRepository;

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void updateIsActiveByEffectiveDate() {
        Date now = new Date();
        int deactivated = groupCategoryRepository.deactivateExpiredOrNotYetEffective(now);
        int activated = groupCategoryRepository.activateNowInEffect(now);
        System.out.println("Sync isActive: now " + now);

        if (activated > 0 || deactivated > 0) {

            eventPublisher.publishEvent(
                    new GroupCategoryChangedEvent()
            );
        }

    }

    private Integer computeIsActive(Date effectiveDate, Date endEffectiveDate, Date now) {
        if (effectiveDate == null || now.before(effectiveDate)) {
            return 0; // chưa tới ngày hiệu lực
        }
        if (endEffectiveDate != null && now.after(endEffectiveDate)) {
            return 0; // đã hết hiệu lực
        }
        return 1; // đang trong khoảng hiệu lực
    }

    private List<String> checkDuplicate(GroupCategoryRequest groupCategoryRequest) {
        List<String> duplicated = new ArrayList<>();
        if (groupCategoryRepository.existsByParamValue(groupCategoryRequest.getParamValue())) {
            duplicated.add("paramValue");
        }
        if (groupCategoryRepository.existsByParamType(groupCategoryRequest.getParamType())) {
            duplicated.add("paramType");
        }
        if(groupCategoryRepository.existsOverlappingDate(groupCategoryRequest.getEffectiveDate(),groupCategoryRequest.getEndEffectiveDate())){
            duplicated.add("exitsDate");
        }
        return duplicated;
    }

    @Override
    public Page<GroupCategory> fillAll(SearchRequest searchRequest) {
        Pageable pageable = PageRequest.of( searchRequest.getPage(),searchRequest.getSize());
        return groupCategoryRepository.findAllByOrderByUpdateDateDesc(pageable);
    }

    @Override
    @Transactional
    public GroupCategory add(GroupCategoryRequest groupCategoryRequest) {
        List<String> duplicated = checkDuplicate(groupCategoryRequest);
//        if (!duplicated.isEmpty()) {
//            throw new ResponseStatusException(
//                    HttpStatus.CONFLICT,
//                    "Trùng dữ liệu: " + String.join(", ", duplicated)
//            );
//        }
        System.out.println(groupCategoryRequest.getEffectiveDate());
        GroupCategory groupCategory = new GroupCategory();
        groupCategory.setParamName(groupCategoryRequest.getParamName());
        groupCategory.setParamValue(groupCategoryRequest.getParamValue());
        groupCategory.setParamType(groupCategoryRequest.getParamType());
        groupCategory.setDescription(groupCategoryRequest.getDescription());
        groupCategory.setComponentCode(groupCategoryRequest.getComponentCode());
        groupCategory.setStatus(groupCategoryRequest.getStatus());
        groupCategory.setIsActive(
                computeIsActive(groupCategoryRequest.getEffectiveDate(),groupCategoryRequest.getEndEffectiveDate(),new Date()));
        groupCategory.setIsDisplay(1);
        groupCategory.setEffectiveDate(groupCategoryRequest.getEffectiveDate());
        groupCategory.setEndEffectiveDate(groupCategoryRequest.getEndEffectiveDate());
        groupCategory.setCreatedDate(new Date());
        groupCategory.setUpdateDate(new Date());
        groupCategoryRepository.save(groupCategory);
        return groupCategory;
    }

    @Override
    @Transactional
    public GroupCategory update(GroupCategoryRequest groupCategoryRequest) {
        GroupCategory groupCategory = groupCategoryRepository.findById(groupCategoryRequest.getId()).orElseThrow(()
                ->new IllegalArgumentException("Không tìm thấy GroupCategory"));
        if(groupCategory.getStatus()==1){
            groupCategory.setParamName(groupCategoryRequest.getParamName());
            groupCategory.setParamValue(groupCategoryRequest.getParamValue());
            groupCategory.setParamType(groupCategoryRequest.getParamType());
            groupCategory.setDescription(groupCategoryRequest.getDescription());
            groupCategory.setComponentCode(groupCategoryRequest.getComponentCode());
            groupCategory.setStatus(groupCategoryRequest.getStatus());
            groupCategory.setIsActive(
                    computeIsActive(groupCategoryRequest.getEffectiveDate(),groupCategoryRequest.getEndEffectiveDate(),new Date()));
            groupCategory.setIsDisplay(groupCategoryRequest.getIsDisplay());
            groupCategory.setNewData(null);
            groupCategory.setEffectiveDate(groupCategoryRequest.getEffectiveDate());
            groupCategory.setEndEffectiveDate(groupCategoryRequest.getEndEffectiveDate());
            groupCategory.setUpdateDate(new Date());
        }else {
            groupCategory.setStatus(groupCategoryRequest.getStatus());
            groupCategory.setIsActive(computeIsActive(
                    groupCategoryRequest.getEffectiveDate(),groupCategoryRequest.getEndEffectiveDate(),new Date()));
            groupCategory.setNewData(groupCategoryRequest.getNewData());
            groupCategory.setUpdateDate(new Date());
        }
        return groupCategoryRepository.save(groupCategory);
    }

    @Override
    @Transactional
    public GroupCategory delete(Long id) {
        GroupCategory groupCategory = groupCategoryRepository.findById(id).orElseThrow(()
                ->new IllegalArgumentException("Không tìm thấy GroupCategory"));
        groupCategoryRepository.delete(groupCategory);
        return groupCategory;
    }



    @Override
    @Transactional
    public Map<String, Object> submitApproval(List<Long> ids, Integer newStatus) {

        if(ids == null || ids.isEmpty()){
            throw new IllegalArgumentException("Danh sách ID không được rỗng");
        }

        if(newStatus == null){
            throw new IllegalArgumentException("Danh sách status không được rỗng");
        }

        List<GroupCategory> list = groupCategoryRepository.findAllById(ids);

        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi nào");
        }

        Integer firstStatus = list.get(0).getStatus();
        boolean allSameStatus = list.stream().allMatch(c -> c.getStatus().equals(firstStatus));

        if (!allSameStatus) {
            throw new IllegalStateException("Các bản ghi phải có cùng trạng thái");
        }

        if (!Integer.valueOf(1).equals(firstStatus) && !Integer.valueOf(5).equals(firstStatus)
                && !Integer.valueOf(7).equals(firstStatus) || !Integer.valueOf(3).equals(newStatus)) {
            throw new IllegalStateException(
                    "Chỉ bản ghi trạng thái 1, 7 mới được gửi duyệt sang trạng thái 3!"
            );
        }


        list.forEach(item -> {
            item.setStatus(newStatus);
            item.setIsActive(computeIsActive(item.getEffectiveDate(), item.getEndEffectiveDate(), new Date()));
            item.setUpdateDate(new Date());
        });

        List<GroupCategory> updated = groupCategoryRepository.saveAll(list);

        return Map.of(
                "message", "Gửi duyệt thành công",
                "updatedCount", updated.size(),
                "oldStatus", firstStatus,
                "newStatus", newStatus
        );
    }

    @Override
    public Map<String, Object> approve(List<Long> ids, Integer newStatus) {
        if(ids==null||ids.isEmpty()){
            throw new IllegalArgumentException("Danh sách ID không được rỗng");
        }
        if(newStatus == null){
            throw new IllegalArgumentException("Danh sách status không được rỗng");
        }

        List<GroupCategory> list = groupCategoryRepository.findAllById(ids);

        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi nào");
        }

        Integer firstStatus = list.get(0).getStatus();
        boolean allSameStatus = list.stream().allMatch(c -> c.getStatus().equals(firstStatus));

        if (!allSameStatus) {
            throw new IllegalStateException("Các bản ghi phải có cùng trạng thái");
        }

        if (!Integer.valueOf(3).equals(firstStatus) || !Integer.valueOf(4).equals(newStatus)) {
            throw new IllegalStateException(
                    "Chỉ bản ghi trạng thái 3 mới được gửi duyệt sang trạng thái 4!"
            );
        }

        list.forEach(item -> {
            if (StringUtils.hasText(item.getNewData())) {
                try {
                    GroupCategoryRequest cate = objectMapper.readValue(item.getNewData(), GroupCategoryRequest.class);
                    item.setParamType(cate.getParamType());
                    item.setParamValue(cate.getParamValue());
                    item.setParamName(cate.getParamName());
                    item.setComponentCode(cate.getComponentCode());
                    item.setNewData(null);
                    item.setDescription(cate.getDescription());
                    item.setEffectiveDate(cate.getEffectiveDate());
                    item.setEndEffectiveDate(cate.getEndEffectiveDate());
                } catch (Exception e) {
                    throw new RuntimeException("Lỗi đọc newData", e);
                }
            }else {
                item.setIsActive(computeIsActive(item.getEffectiveDate(), item.getEndEffectiveDate(), new Date()));
                item.setIsDisplay(2);
            }
            item.setStatus(newStatus);
            item.setUpdateDate(new Date());
        });

        List<GroupCategory> updated = groupCategoryRepository.saveAll(list);

        return Map.of(
                "message", "Duyệt thành công",
                "updatedCount", updated.size(),
                "oldStatus", firstStatus,
                "newStatus", newStatus
        );
    }

    @Override
    public Map<String, Object> cancelApprove(List<Long> ids, Integer newStatus) {
        if(ids == null || ids.isEmpty()){
            throw new IllegalArgumentException("Danh sách ID không được rỗng");
        }
        if(newStatus == null){
            throw new IllegalArgumentException("Danh sách status không được rỗng");
        }

        List<GroupCategory> list = groupCategoryRepository.findAllById(ids);

        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi nào");
        }

        Integer firstStatus = list.get(0).getStatus();
        boolean allSameStatus = list.stream().allMatch(c -> c.getStatus().equals(firstStatus));

        if (!allSameStatus) {
            throw new IllegalStateException("Các bản ghi phải có cùng trạng thái");
        }

        if (!Integer.valueOf(4).equals(firstStatus) || !Integer.valueOf(7).equals(newStatus)) {
            throw new IllegalStateException(
                    "Chỉ bản ghi trạng thái 4 mới được gửi duyệt sang trạng thái 7!"
            );
        }


        list.forEach(item -> {
            item.setStatus(newStatus);
            item.setIsActive(computeIsActive(item.getEffectiveDate(), item.getEndEffectiveDate(), new Date()));
            item.setUpdateDate(new Date());
        });

        List<GroupCategory> updated = groupCategoryRepository.saveAll(list);

        return Map.of(
                "message", "Hủy duyệt thành công",
                "updatedCount", updated.size(),
                "oldStatus", firstStatus,
                "newStatus", newStatus
        );
    }

    @Override
    public Map<String, Object> reject(List<Long> ids, Integer newStatus) {
        if(ids == null || ids.isEmpty()){
            throw new IllegalArgumentException("Danh sách ID không được rỗng");
        }
        if(newStatus == null){
            throw new IllegalArgumentException("Danh sách status không được rỗng");
        }

        List<GroupCategory> list = groupCategoryRepository.findAllById(ids);

        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi nào");
        }

        Integer firstStatus = list.get(0).getStatus();
        boolean allSameStatus = list.stream().allMatch(c -> c.getStatus().equals(firstStatus));

        if (!allSameStatus) {
            throw new IllegalStateException("Các bản ghi phải có cùng trạng thái");
        }

        if (!Integer.valueOf(3).equals(firstStatus) || !Integer.valueOf(5).equals(newStatus)) {
            throw new IllegalStateException(
                    "Chỉ bản ghi trạng thái 3 mới được gửi duyệt sang trạng thái 5!"
            );
        }


        list.forEach(item -> {
            item.setStatus(newStatus);
            item.setIsActive(computeIsActive(item.getEffectiveDate(), item.getEndEffectiveDate(), new Date()));
            item.setUpdateDate(new Date());
        });

        List<GroupCategory> updated = groupCategoryRepository.saveAll(list);

        return Map.of(
                "message", "Từ chối duyệt thành công",
                "updatedCount", updated.size(),
                "oldStatus", firstStatus,
                "newStatus", newStatus
        );
    }

    @Override
    public Page<GroupCategory> searchSpecification(SearchRequest searchRequest) {

        Pageable pageable = PageRequest.of(searchRequest.getPage(),searchRequest.getSize(),
                        Sort.by(Sort.Direction.DESC, "updateDate"));
        Specification<GroupCategory> specification = (root, query, criteriaBuilder) ->{
            List< Predicate > predicate = new ArrayList<>();

            if(searchRequest.getParamType() != null && !searchRequest.getParamType().isBlank()){
                predicate.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("paramType")),
                                "%" + searchRequest.getParamType().toLowerCase() + "%"
                        )
                );
            }
            if(searchRequest.getParamValue() != null && !searchRequest.getParamValue().isBlank()){
                predicate.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("paramValue")),
                                "%" + searchRequest.getParamValue().toLowerCase() + "%"
                        )
                );
            }
            if(searchRequest.getParamName() != null && !searchRequest.getParamName().isBlank()){
                predicate.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("paramName")),
                                "%" + searchRequest.getParamName().toLowerCase() + "%"
                        )
                );
            }
            if (searchRequest.getStatus() != null){
                predicate.add(
                        criteriaBuilder.equal(
                                root.get("status"),searchRequest.getStatus()
                        )
                );
            }
            if(searchRequest.getIsActive() != null){
                predicate.add(
                        criteriaBuilder.equal(
                                root.get("isActive"),searchRequest.getIsActive()
                        )
                );
            }
            if(predicate.isEmpty()){
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.and(
                    predicate.toArray(new Predicate[0])
            );
        };
        Page<GroupCategory> result = groupCategoryRepository.findAll(specification,pageable);
        return result;
    }

    @Override
    public Page<GroupCategory> searchNativeQuery(SearchRequest searchRequest) {
        StringBuilder where = new StringBuilder();
        Map<String,Object> params = new HashMap<>();
        //thêm điều kiện
        boolean first = true;
        if (StringUtils.hasText(searchRequest.getParamType())){
            if (first) {
                where.append(" WHERE ");
                first = false;
            } else {
                where.append(" AND ");
            }
            where.append(" LOWER(PARAM_TYPE) LIKE (:paramType) ");
            params.put("paramType", "%" + searchRequest.getParamType().toLowerCase() + "%" );
        }
        if (StringUtils.hasText(searchRequest.getParamValue())){
            if (first) {
                where.append(" WHERE ");
                first = false;
            } else {
                where.append(" AND ");
            }
            where.append(" LOWER(PARAM_VALUE) LIKE (:paramValue) ");
            params.put("paramValue", "%" + searchRequest.getParamValue().toLowerCase() + "%");
        }
        if (StringUtils.hasText(searchRequest.getParamName())){
            if (first) {
                where.append(" WHERE ");
                first = false;
            } else {
                where.append(" AND ");
            }
            where.append(" LOWER(PARAM_NAME) LIKE (:paramName) ");
            params.put("paramName", "%" + searchRequest.getParamName().toLowerCase() + "%");
        }
        if (searchRequest.getStatus() != null){
            if (first) {
                where.append(" WHERE ");
                first = false;
            } else {
                where.append(" AND ");
            }
            where.append(" STATUS = :status ");
            params.put("status",searchRequest.getStatus());
        }
        if (searchRequest.getIsActive() != null){
            if (first) {
                where.append(" WHERE ");
                first = false;
            } else {
                where.append(" AND ");
            }
            where.append(" IS_ACTIVE = :isActive ");
            params.put("isActive",searchRequest.getIsActive());
        }

        Pageable pageable = PageRequest.of(searchRequest.getPage(),searchRequest.getSize(),
                Sort.by(Sort.Direction.DESC, "updateDate"));

        String sql = "SELECT * FROM PMH_GROUP_CATEGORY" + where + " ORDER BY UPDATE_DATE DESC";
        String countSql = "SELECT COUNT(*) FROM PMH_GROUP_CATEGORY" + where;

        Query query = entityManager.createNativeQuery( sql,GroupCategory.class);
        Query countQuery = entityManager.createNativeQuery(countSql);
        for (Map.Entry<String,Object> entry : params.entrySet()){
            query.setParameter(entry.getKey(),entry.getValue());
            countQuery.setParameter(entry.getKey(),entry.getValue());
        }

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<GroupCategory> list = query.getResultList();
        Long total = ((Number)countQuery.getSingleResult()).longValue();

        return new PageImpl<>(list,pageable,total);
    }

    @Override
    public Page<GroupCategory> searchProcedure(SearchRequest searchRequest) {
        StoredProcedureQuery query =
                entityManager.createStoredProcedureQuery("SEARCH_GROUP_CATEGORY", GroupCategory.class);

        // in
        query.registerStoredProcedureParameter("P_PARAM_TYPE", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("P_PARAM_VALUE", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("P_PARAM_NAME", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("P_STATUS", Integer.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("P_IS_ACTIVE", Integer.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("P_PAGE", Integer.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("P_SIZE", Integer.class, ParameterMode.IN);

        // out
        query.registerStoredProcedureParameter("P_TOTAL", Long.class, ParameterMode.OUT);
        query.registerStoredProcedureParameter("P_RESULT", void.class, ParameterMode.REF_CURSOR);

        // set value
        query.setParameter("P_PARAM_TYPE", searchRequest.getParamType());
        query.setParameter("P_PARAM_VALUE", searchRequest.getParamValue());
        query.setParameter("P_PARAM_NAME", searchRequest.getParamName());
        query.setParameter("P_STATUS", searchRequest.getStatus());
        query.setParameter("P_IS_ACTIVE", searchRequest.getIsActive());

        // tránh null page size
        int page = searchRequest.getPage() == null ? 0 : searchRequest.getPage();
        int size = searchRequest.getSize() == null ? 10 : searchRequest.getSize();

        query.setParameter("P_PAGE", page);
        query.setParameter("P_SIZE", size);

        query.execute();

        // lấy data từ SYS_REFCURSOR
        List<GroupCategory> data = query.getResultList();

        // tổng bản ghi
        Long total = ((Number) query.getOutputParameterValue("P_TOTAL")).longValue();

        Pageable pageable = PageRequest.of(page, size,Sort.by(Sort.Direction.DESC, "updateDate"));

        return new PageImpl<>(data, pageable, total);
    }

    @Override
    public ByteArrayInputStream exportAll(SearchRequest searchRequest) {
        searchRequest.setPage(0);
        searchRequest.setSize(Integer.MAX_VALUE);
        Page<GroupCategory> pageResult = searchSpecification(searchRequest);
        List<GroupCategory> categoryList = pageResult.getContent();

        // 2. Cấu hình Column Map (Tiêu đề Excel -> Field Name trong Java)
        LinkedHashMap<String, String> columnMap = new LinkedHashMap<>();
        columnMap.put("Tên tham số", "paramName");
        columnMap.put("Giá trị thành phn", "paramValue");
        columnMap.put("Danh mục theo nhóm", "paramType");
        columnMap.put("Mô tả", "description");
        columnMap.put("Cấu phần xử lý", "componentCode");
        columnMap.put("Trạng thái tham số", "status");
        columnMap.put("Trạng thái hoạt động", "isActive");
//        columnMap.put("Hiển thị", "isDisplay");
        columnMap.put("Ngày bắt đầu", "effectiveDate");
        columnMap.put("Ngày kết thúc", "endEffectiveDate");

        // 3. Cấu hình Value Mappings (Dịch số thành chữ)
        Map<String, Map<Object, String>> valueMappings = new HashMap<>();

        valueMappings.put("status", Map.of(1, "Tạo mới", 3, "Chờ phê duyệt", 4, "Đã phê duyệt",
                5, "Từ chối", 7, "Huỷ phê duyệt"));
        valueMappings.put("isActive", Map.of(0, "Không hoạt động", 1, "Hoạt động"));
//        valueMappings.put("isDisplay", Map.of(1, "Không hiển thị", 1, "Hiển thị"));


        // 4. Gọi Utils
        return ExcelBase.exportToExcel(categoryList, columnMap, valueMappings, "Group Categories");
    }

}
