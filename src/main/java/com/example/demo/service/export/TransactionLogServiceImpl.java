package com.example.demo.service.export;

import com.example.demo.dto.Request.TransactionLogRequest;
import com.example.demo.entity.TransactionLog;
import com.example.demo.repository.TransactionLogRepository;
import com.example.demo.service.TransactionLogSevice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionLogServiceImpl implements TransactionLogSevice {

    private final TransactionLogRepository transactionLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<TransactionLog> searchNativeQuery(TransactionLogRequest request) {

        if (request.getMinAmount() != null && request.getMaxAmount() != null && request.getMinAmount() > request.getMaxAmount()) {

            throw new IllegalArgumentException("Số tiền tối thiểu không được lớn hơn số tiền tối đa");
        }

        if (request.getMinAmount() != null && request.getMinAmount() < 0) {

            throw new IllegalArgumentException("Số tiền tối thiểu không được nhỏ hơn 0");
        }

        if (request.getMaxAmount() != null && request.getMaxAmount() < 0) {

            throw new IllegalArgumentException("Số tiền tối đa không được nhỏ hơn 0");
        }

        if (request.getFromDate() != null && request.getToDate() != null && request.getFromDate().after(request.getToDate())) {

            throw new IllegalArgumentException("Từ ngày không được lớn hơn đến ngày");
        }

        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

        Map<String, Object> params = new HashMap<>();

        if (StringUtils.hasText(request.getTransactionCode())) {

            where.append("""
                     AND LOWER(TRANSACTION_CODE)
                         LIKE :transactionCode
                    """);

            params.put("transactionCode", "%" + request.getTransactionCode().trim().toLowerCase(Locale.ROOT) + "%");
        }

        if (StringUtils.hasText(request.getAccountNo())) {

            where.append("""
                     AND LOWER(ACCOUNT_NO)
                         LIKE :accountNo
                    """);

            params.put("accountNo", "%" + request.getAccountNo().trim().toLowerCase(Locale.ROOT) + "%");
        }

        if (StringUtils.hasText(request.getStatus())) {

            where.append("""
                     AND STATUS = :status
                    """);

            params.put("status", request.getStatus().trim());
        }

        if (request.getMinAmount() != null) {

            where.append("""
                     AND AMOUNT >= :minAmount
                    """);

            params.put("minAmount", request.getMinAmount());
        }

        if (request.getMaxAmount() != null) {

            where.append("""
                     AND AMOUNT <= :maxAmount
                    """);

            params.put("maxAmount", request.getMaxAmount());
        }

        if (request.getFromDate() != null) {

            where.append("""
                     AND CREATED_AT >= :fromDate
                    """);

            params.put("fromDate", new Timestamp(request.getFromDate().getTime()));
        }

        if (request.getToDate() != null) {

            where.append("""
                     AND CREATED_AT <= :toDate
                    """);

            params.put("toDate", new Timestamp(request.getToDate().getTime()));
        }

        int page = request.getPage() != null ? request.getPage() : 0;

        int size = request.getSize() != null ? request.getSize() : 100;

        if (page < 0) {
            page = 0;
        }

        if (size <= 0) {
            size = 100;
        }

        if (size > 1000) {
            size = 1000;
        }

        Pageable pageable = PageRequest.of(page, size);

        String sql = """
                SELECT *
                FROM TRANSACTION_LOG
                """ + where + """
                 ORDER BY CREATED_AT DESC, ID DESC
                """;

        String countSql = """
                SELECT COUNT(*)
                FROM TRANSACTION_LOG
                """ + where;

        Query query = entityManager.createNativeQuery(sql, TransactionLog.class);

        Query countQuery = entityManager.createNativeQuery(countSql);

        for (Map.Entry<String, Object> entry : params.entrySet()) {

            query.setParameter(entry.getKey(), entry.getValue());

            countQuery.setParameter(entry.getKey(), entry.getValue());
        }

        query.setFirstResult((int) pageable.getOffset());

        query.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked") List<TransactionLog> list = query.getResultList();

        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(list, pageable, total);
    }
}
