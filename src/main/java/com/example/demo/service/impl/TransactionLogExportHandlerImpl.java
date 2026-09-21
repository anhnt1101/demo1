package com.example.demo.service.impl;

import com.example.demo.constants.ExportType;
import com.example.demo.dto.Request.TransactionLogRequest;
import com.example.demo.entity.ExportRequest;
import com.example.demo.service.ExportHandler;
import com.example.demo.service.ExportSheetWriter;
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
import java.util.function.IntConsumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionLogExportHandlerImpl implements ExportHandler {

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;

    private static final long PROGRESS_BATCH_SIZE = 5_000L;

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
    public void writeRows(ExportRequest request, ExportSheetWriter writer, IntConsumer progressCallback) throws Exception {

        /*
         * ==================================================
         * 1. Header Excel
         * ==================================================
         */
        writer.writeHeader("ID", "TRANSACTION_CODE", "ACCOUNT_NO", "AMOUNT", "STATUS", "CREATED_AT", "DESCRIPTION", "REFERENCE_NO", "UPDATED_AT");


        /*
         * ==================================================
         * 2. Đọc filter từ PARAMS
         * ==================================================
         */
        TransactionLogRequest filter = parseParams(request.getParams());


        /*
         * ==================================================
         * 3. Validate filter
         * ==================================================
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
         * ==================================================
         * 4. Build WHERE dùng chung
         * ==================================================
         *
         * COUNT và SELECT dùng chung WHERE
         * để đảm bảo tổng bản ghi và dữ liệu export khớp nhau.
         */
        StringBuilder whereSql = new StringBuilder("""
                FROM TRANSACTION_LOG
                WHERE 1 = 1
                """);


        List<Object> parameters = new ArrayList<>();


        /*
         * TRANSACTION_CODE
         */
        if (StringUtils.hasText(filter.getTransactionCode())) {

            whereSql.append("""
                     AND LOWER(TRANSACTION_CODE) LIKE ?
                    """);

            parameters.add("%" + filter.getTransactionCode().trim().toLowerCase(Locale.ROOT) + "%");
        }


        /*
         * ACCOUNT_NO
         */
        if (StringUtils.hasText(filter.getAccountNo())) {

            whereSql.append("""
                     AND LOWER(ACCOUNT_NO) LIKE ?
                    """);

            parameters.add("%" + filter.getAccountNo().trim().toLowerCase(Locale.ROOT) + "%");
        }


        /*
         * STATUS
         */
        if (StringUtils.hasText(filter.getStatus())) {

            whereSql.append("""
                     AND STATUS = ?
                    """);

            parameters.add(filter.getStatus().trim());
        }


        /*
         * MIN AMOUNT
         */
        if (filter.getMinAmount() != null) {

            whereSql.append("""
                     AND AMOUNT >= ?
                    """);

            parameters.add(filter.getMinAmount());
        }


        /*
         * MAX AMOUNT
         */
        if (filter.getMaxAmount() != null) {

            whereSql.append("""
                     AND AMOUNT <= ?
                    """);

            parameters.add(filter.getMaxAmount());
        }


        /*
         * FROM DATE
         */
        if (filter.getFromDate() != null) {

            whereSql.append("""
                     AND CREATED_AT >= ?
                    """);

            parameters.add(new Timestamp(filter.getFromDate().getTime()));
        }


        /*
         * TO DATE
         */
        if (filter.getToDate() != null) {

            whereSql.append("""
                     AND CREATED_AT <= ?
                    """);

            parameters.add(new Timestamp(filter.getToDate().getTime()));
        }


        /*
         * ==================================================
         * 5. COUNT tổng số bản ghi
         * ==================================================
         */
        String countSql = "SELECT COUNT(*) " + whereSql;


        Long totalRows = jdbcTemplate.queryForObject(countSql, Long.class, parameters.toArray());


        if (totalRows == null) {
            totalRows = 0L;
        }


        log.info("Export #{} tổng số bản ghi = {}", request.getId(), totalRows);


        /*
         * ==================================================
         * 6. Không có dữ liệu
         * ==================================================
         */
        if (totalRows == 0) {

            progressCallback.accept(100);

            log.info("Export #{} không có dữ liệu", request.getId());

            return;
        }


        /*
         * ==================================================
         * 7. Build SELECT
         * ==================================================
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
                """);


        sql.append(whereSql);


        /*
         * Nếu cần sort thì bật lại.
         */
//    sql.append("""
//             ORDER BY CREATED_AT DESC, ID DESC
//            """);


        log.info("Export #{} query TRANSACTION_LOG", request.getId());

        log.info("Export SQL: {}", sql);

        log.info("Export parameters: {}", parameters);


        /*
         * ==================================================
         * 8. Biến theo dõi progress
         * ==================================================
         */
        final long total = totalRows;


        /*
         * Lambda cần biến mutable,
         * nên dùng array 1 phần tử.
         */
        final long[] processedRows = {0L};


        /*
         * Tránh gửi cùng một % nhiều lần.
         */
        final int[] lastProgress = {-1};


        /*
         * ==================================================
         * 9. JDBC STREAMING
         * ==================================================
         */
        jdbcTemplate.query(

                connection -> {

                    PreparedStatement ps = connection.prepareStatement(sql.toString(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);


                    /*
                     * Ví dụ fetchSize = 20.000.
                     *
                     * JDBC fetch dữ liệu theo từng đợt,
                     * không load toàn bộ ResultSet vào RAM.
                     */
                    ps.setFetchSize(fetchSize);


                    ps.setFetchDirection(ResultSet.FETCH_FORWARD);


                    bindParameters(ps, parameters);


                    return ps;
                },


                (RowCallbackHandler) rs -> {

                    /*
                     * ==================================================
                     * 10. Ghi từng row vào Excel
                     * ==================================================
                     */
                    writer.writeRow(

                            rs.getLong("ID"),

                            rs.getString("TRANSACTION_CODE"),

                            rs.getString("ACCOUNT_NO"),

                            rs.getBigDecimal("AMOUNT"),

                            rs.getString("STATUS"),

                            toLocalDateTime(rs.getTimestamp("CREATED_AT")),

                            rs.getString("DESCRIPTION"),

                            rs.getString("REFERENCE_NO"),

                            toLocalDateTime(rs.getTimestamp("UPDATED_AT")));


                    /*
                     * Đã xử lý thêm 1 bản ghi.
                     */
                    processedRows[0]++;


                    /*
                     * ==================================================
                     * 11. Cập nhật progress
                     * ==================================================
                     *
                     * Chỉ cập nhật khi:
                     *
                     * - đủ mỗi 5.000 bản ghi
                     *
                     * HOẶC
                     *
                     * - đã xử lý row cuối cùng
                     *
                     * Ví dụ:
                     *
                     * total = 100.000
                     *
                     * 5.000  -> 5%
                     * 10.000 -> 10%
                     * ...
                     * 100.000 -> 100%
                     */
                    if (processedRows[0] % PROGRESS_BATCH_SIZE == 0

                            || processedRows[0] == total) {


                        /*
                         * % thực tế:
                         *
                         * processed / total * 100
                         */
                        int progress = (int) Math.round(processedRows[0] * 100.0 / total);


                        /*
                         * Bảo vệ để không bao giờ > 100.
                         */
                        progress = Math.min(progress, 100);


                        /*
                         * Chỉ gửi khi %
                         * thực sự thay đổi.
                         */
                        if (progress != lastProgress[0]) {

                            progressCallback.accept(progress);


                            lastProgress[0] = progress;
                        }


                        log.info("Export #{} progress: {}/{} rows = {}%", request.getId(), processedRows[0], total, progress);
                    }
                });


        /*
         * ==================================================
         * 12. Stream hoàn thành
         * ==================================================
         */
        log.info("Export #{} stream xong {}/{} bản ghi TRANSACTION_LOG", request.getId(), processedRows[0], total);
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