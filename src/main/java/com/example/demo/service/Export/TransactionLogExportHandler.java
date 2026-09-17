package com.example.demo.service.Export;

import com.example.demo.constants.ExportType;
import com.example.demo.dto.Request.TransactionLogRequest;
import com.example.demo.entity.ExportRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionLogExportHandler implements ExportHandler {

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;

    @Value("${export.jdbc-fetch-size:1000}")
    private int fetchSize;

    @Override
    public String getExportType() {
        return ExportType.TRANSACTION_LOG;
    }

    @Override
    public String suggestFileName(ExportRequest request) {
        return "transaction_log_" + request.getId() + ".xlsx";
    }

    @Override
    public void writeRows(ExportRequest request, ExportSheetWriter writer) throws Exception {

        /*
         * ==========================================
         * HEADER
         * ==========================================
         */
        writer.writeHeader("ID", "TRANSACTION_CODE", "ACCOUNT_NO", "AMOUNT", "STATUS", "CREATED_AT", "DESCRIPTION", "REFERENCE_NO", "UPDATED_AT");


        /*
         * ==========================================
         * PARSE PARAMS
         * ==========================================
         *
         * Dùng đúng DTO mà API search đang dùng.
         *
         * page/size có thể tồn tại trong JSON
         * nhưng export KHÔNG sử dụng.
         */
        TransactionLogRequest filter = parseParams(request.getParams());


        /*
         * ==========================================
         * VALIDATE
         * ==========================================
         */

        if (filter.getMinAmount() != null && filter.getMaxAmount() != null && filter.getMinAmount() > filter.getMaxAmount()) {

            throw new IllegalArgumentException("Số tiền tối thiểu không được lớn hơn số tiền tối đa");
        }


        if (filter.getMinAmount() != null && filter.getMinAmount() < 0) {

            throw new IllegalArgumentException("Số tiền tối thiểu không được nhỏ hơn 0");
        }


        if (filter.getMaxAmount() != null && filter.getMaxAmount() < 0) {

            throw new IllegalArgumentException("Số tiền tối đa không được nhỏ hơn 0");
        }


        if (filter.getFromDate() != null && filter.getToDate() != null && filter.getFromDate().after(filter.getToDate())) {

            throw new IllegalArgumentException("Từ ngày không được lớn hơn đến ngày");
        }


        /*
         * ==========================================
         * BASE SQL
         * ==========================================
         */
        StringBuilder sql = new StringBuilder("""
                SELECT
                    ID,
                    TRANSACTION_CODE,
                    ACCOUNT_NO,
                    AMOUNT,
                    STATUS,
                    CREATED_AT,
                    DESCRIPTION,
                    REFERENCE_NO,
                    UPDATED_AT
                FROM TRANSACTION_LOG
                WHERE 1 = 1
                """);


        List<Object> parameters = new ArrayList<>();


        /*
         * ==========================================
         * TRANSACTION_CODE
         * ==========================================
         *
         * Giống Search:
         *
         * LOWER(TRANSACTION_CODE)
         * LIKE '%value%'
         */
        if (StringUtils.hasText(filter.getTransactionCode())) {

            sql.append("""
                     AND LOWER(TRANSACTION_CODE)
                         LIKE ?
                    """);


            parameters.add("%" + filter.getTransactionCode().trim().toLowerCase(Locale.ROOT) + "%");
        }


        /*
         * ==========================================
         * ACCOUNT_NO
         * ==========================================
         *
         * Giống Search:
         *
         * LOWER(ACCOUNT_NO)
         * LIKE '%value%'
         */
        if (StringUtils.hasText(filter.getAccountNo())) {

            sql.append("""
                     AND LOWER(ACCOUNT_NO)
                         LIKE ?
                    """);


            parameters.add("%" + filter.getAccountNo().trim().toLowerCase(Locale.ROOT) + "%");
        }


        /*
         * ==========================================
         * STATUS
         * ==========================================
         *
         * Giống Search:
         *
         * STATUS = ?
         */
        if (StringUtils.hasText(filter.getStatus())) {

            sql.append("""
                     AND STATUS = ?
                    """);


            parameters.add(filter.getStatus().trim());
        }


        /*
         * ==========================================
         * MIN AMOUNT
         * ==========================================
         *
         * Giống Search:
         *
         * AMOUNT >= minAmount
         */
        if (filter.getMinAmount() != null) {

            sql.append("""
                     AND AMOUNT >= ?
                    """);


            parameters.add(filter.getMinAmount());
        }


        /*
         * ==========================================
         * MAX AMOUNT
         * ==========================================
         *
         * Giống Search:
         *
         * AMOUNT <= maxAmount
         */
        if (filter.getMaxAmount() != null) {

            sql.append("""
                     AND AMOUNT <= ?
                    """);


            parameters.add(filter.getMaxAmount());
        }


        /*
         * ==========================================
         * FROM DATE
         * ==========================================
         *
         * Có đầy đủ:
         *
         * dd/MM/yyyy HH:mm:ss
         *
         * Ví dụ:
         *
         * 16/09/2026 08:30:15
         *
         * =>
         *
         * CREATED_AT >= ?
         */
        if (filter.getFromDate() != null) {

            sql.append("""
                     AND CREATED_AT >= ?
                    """);


            parameters.add(new Timestamp(filter.getFromDate().getTime()));
        }


        /*
         * ==========================================
         * TO DATE
         * ==========================================
         *
         * Ví dụ:
         *
         * 16/09/2026 17:45:30
         *
         * =>
         *
         * CREATED_AT <= ?
         */
        if (filter.getToDate() != null) {

            sql.append("""
                     AND CREATED_AT <= ?
                    """);


            parameters.add(new Timestamp(filter.getToDate().getTime()));
        }


        /*
         * ==========================================
         * ORDER BY
         * ==========================================
         *
         * Giống Search.
         *
         * ID DESC giúp thứ tự ổn định
         * nếu nhiều record cùng CREATED_AT.
         */
        sql.append("""
                 ORDER BY CREATED_AT DESC, ID DESC
                """);


        /*
         * ==========================================
         * LOG
         * ==========================================
         */
        log.info("Export #{} query TRANSACTION_LOG", request.getId());


        log.info("Export SQL: {}", sql);


        log.info("Export parameters: {}", parameters);


        /*
         * ==========================================
         * JDBC STREAMING
         * ==========================================
         *
         * Không load toàn bộ dữ liệu vào RAM.
         *
         * Oracle trả từng batch theo fetchSize.
         */
        jdbcTemplate.query(

                connection -> {

                    PreparedStatement ps = connection.prepareStatement(sql.toString(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);


                    ps.setFetchSize(fetchSize);


                    ps.setFetchDirection(ResultSet.FETCH_FORWARD);


                    bindParameters(ps, parameters);


                    return ps;
                },


                /*
                 * Mỗi row DB đọc được
                 * sẽ ghi thẳng vào SXSSF.
                 */
                (RowCallbackHandler) rs ->

                        writer.writeRow(

                                rs.getLong("ID"),

                                rs.getString("TRANSACTION_CODE"),

                                rs.getString("ACCOUNT_NO"),

                                rs.getBigDecimal("AMOUNT"),

                                rs.getString("STATUS"),

                                toLocalDateTime(rs.getTimestamp("CREATED_AT")),

                                rs.getString("DESCRIPTION"),

                                rs.getString("REFERENCE_NO"),

                                toLocalDateTime(rs.getTimestamp("UPDATED_AT"))));


        /*
         * ==========================================
         * DONE
         * ==========================================
         */
        log.info("Export #{} stream xong {} dòng TRANSACTION_LOG", request.getId(), writer.getTotalRowsWritten());
    }


    private TransactionLogRequest parseParams(String json) {

        if (json == null || json.isBlank()) {

            return new TransactionLogRequest();
        }

        try {

            return objectMapper.readValue(json, TransactionLogRequest.class);

        } catch (Exception e) {

            throw new IllegalArgumentException("PARAMS của TRANSACTION_LOG không hợp lệ", e);
        }
    }


    private void bindParameters(PreparedStatement ps, List<Object> values) throws SQLException {

        for (int i = 0; i < values.size(); i++) {

            Object value = values.get(i);

            int index = i + 1;


            if (value instanceof LocalDateTime dateTime) {

                ps.setTimestamp(index, Timestamp.valueOf(dateTime));

            } else if (value instanceof BigDecimal decimal) {

                ps.setBigDecimal(index, decimal);

            } else {

                ps.setObject(index, value);
            }
        }
    }


    private LocalDateTime toLocalDateTime(Timestamp timestamp) {

        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}