package com.example.demo.service.export;

import com.example.demo.constants.ExportStatus;
import com.example.demo.entity.ExportRequest;
import com.example.demo.entity.User;
import com.example.demo.realtime.ExportNotification;
import com.example.demo.realtime.ExportRealtimeService;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ExportSheetWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ExportWorker {

    private final ExportService exportService;

    private final MinioStorageService minioStorageService;

    private final Map<String, ExportHandler> handlerByType;

    private final ExportRealtimeService realtimeService;

    /*
     * Dùng để lấy username 1 LẦN duy nhất khi bắt đầu xử lý
     * một export, thay vì ExportRedisSubscriber phải query
     * DB cho MỖI message realtime (progress, completed, error).
     */
    private final UserRepository userRepository;

    @Value("${export.sxssf-window-size:200}")
    private int sxssfWindowSize;


    @Value("${export.temp-dir:${java.io.tmpdir}}")
    private String tempDir;


    public ExportWorker(ExportService exportService, MinioStorageService minioStorageService, List<ExportHandler> handlers, ExportRealtimeService realtimeService, UserRepository userRepository) {

        this.exportService = exportService;

        this.minioStorageService = minioStorageService;

        this.realtimeService = realtimeService;

        this.userRepository = userRepository;

        this.handlerByType = handlers.stream().collect(Collectors.toMap(ExportHandler::getExportType, handler -> handler));
    }


    public void process(Long requestId) {

        Path tempFile = null;

        SXSSFWorkbook workbook = null;

        String objectKey = null;

        boolean uploaded = false;

        /*
         * Khai báo ngoài try để catch vẫn lấy được
         * userId khi cần publish ERROR.
         */
        ExportRequest request = null;

        /*
         * Khai báo ngoài try (giống request)
         * để catch (ERROR) vẫn dùng được username
         * khi publish ExportNotification.
         */
        String username = null;

        try {

            /*
             * Request lúc này đã được Kafka Consumer claim:
             *
             * NEW -> PROCESSING
             */
            request = exportService.getForProcessing(requestId);


            /*
             * Lấy username 1 LẦN ở đây.
             *
             * Toàn bộ notification (PROCESSING/COMPLETED/ERROR)
             * của export này sẽ dùng lại giá trị này,
             * không query DB lại nữa.
             */
            username = userRepository.findById(request.getUserId())
                    .map(User::getUsername)
                    .orElse(null);

            if (username == null) {
                log.warn("Không tìm thấy username cho userId={}, export #{} sẽ không có realtime WebSocket", request.getUserId(), requestId);
            }


            ExportHandler handler = handlerByType.get(request.getExportType());


            if (handler == null) {

                throw new IllegalStateException("Không có handler cho " + request.getExportType());
            }


            /*
             * ==================================================
             * Tạo thư mục/file temp
             * ==================================================
             */
            Path tempDirectory = Path.of(tempDir);

            Files.createDirectories(tempDirectory);


            tempFile = Files.createTempFile(tempDirectory, "export-" + requestId + "-", ".xlsx");


            /*
             * ==================================================
             * Tạo SXSSFWorkbook
             * ==================================================
             *
             * Window nhỏ -> RAM thấp.
             */
            workbook = new SXSSFWorkbook(sxssfWindowSize);


            /*
             * false:
             * ưu tiên tốc độ.
             *
             * true:
             * giảm dung lượng temp disk nhưng tốn CPU hơn.
             */
            workbook.setCompressTempFiles(false);


            ExportSheetWriter writer = new ExportSheetWriter(workbook);


            /*
             * ==================================================
             * Oracle -> JDBC Streaming -> SXSSF
             * ==================================================
             *
             * TransactionLogExportHandlerImpl:
             *
             * cứ mỗi 5.000 bản ghi
             *          ↓
             * tính:
             *
             * processed / total * 100
             *
             *          ↓
             * progressCallback.accept(progress)
             *
             *          ↓
             * callback về đây
             *
             *          ↓
             * Redis
             */
            final ExportRequest finalRequest = request;

            /*
             * Lambda yêu cầu biến capture phải final/effectively final.
             *
             * "username" ở trên được khai báo "= null" rồi gán lại
             * trong try -> bị coi là gán 2 lần -> KHÔNG effectively
             * final -> lambda bên dưới không compile được.
             *
             * Tạo 1 bản copy final riêng, giống cách làm với
             * "finalRequest", chỉ dùng bên trong lambda.
             */
            final String finalUsername = username;


            handler.writeRows(request, writer, progress -> {

                /*
                 * 1. Lưu progress vào Redis.
                 */
                realtimeService.updateProgress(requestId, progress);


                /*
                 * 2. Đẩy progress realtime.
                 *
                 * Redis Pub/Sub
                 *      ↓
                 * WebSocket
                 *      ↓
                 * Angular
                 */
                realtimeService.publish(new ExportNotification(requestId, finalRequest.getUserId(), finalUsername, ExportStatus.PROCESSING, progress, null, "Đang xuất Excel " + progress + "%"));


                log.info("Export #{} user={} progress={}%", requestId, finalRequest.getUserId(), progress);
            });


            /*
             * ==================================================
             * Auto-fit độ rộng cột
             * ==================================================
             *
             * Gọi sau khi handler.writeRows(...) ghi xong TOÀN BỘ
             * dữ liệu (mọi sheet), trước khi ghi ra file - lúc này
             * writer đã biết độ dài tối đa thật sự của từng cột.
             */
            writer.autoFitColumns();


            /*
             * ==================================================
             * SXSSF -> file .xlsx local
             * ==================================================
             */
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {

                workbook.write(outputStream);
            }


            /*
             * Tên file trả về cho user.
             */
            String fileName = handler.suggestFileName(request);


            /*
             * Object key trên MinIO.
             */
            objectKey = buildObjectKey(request);


            /*
             * ==================================================
             * Upload local file -> MinIO
             * ==================================================
             */
            minioStorageService.upload(tempFile, objectKey);


            uploaded = true;


            /*
             * ==================================================
             * Flow download hiện tại của bạn
             * ==================================================
             *
             * Tạm thời vẫn giữ:
             *
             * OBJECT_KEY
             *      ↓
             * tạo Presigned URL
             *      ↓
             * lưu PATH
             *
             * Phần này mình chưa thay vì hiện tại
             * đang tập trung Kafka + Redis.
             */
            String path = minioStorageService.createDownloadUrl(objectKey);


            /*
             * ==================================================
             * PROCESSING -> COMPLETED
             * ==================================================
             *
             * Chỉ COMPLETED khi:
             *
             * - Excel tạo thành công
             * - MinIO upload thành công
             */
            exportService.markCompleted(requestId, fileName, objectKey, path);


            /*
             * ==================================================
             * Publish Redis event COMPLETED
             * ==================================================
             *
             * Handler có thể đã:
             *
             * updateProgress(requestId, 100)
             *
             * Nhưng lúc đó chỉ có nghĩa:
             * "đã xử lý 100% bản ghi".
             *
             * Sau khi DB thực sự COMPLETED
             * mới publish sự kiện COMPLETED.
             */
            realtimeService.publish(new ExportNotification(requestId, request.getUserId(), username, ExportStatus.COMPLETED, 100, fileName, "File Excel đã sẵn sàng"));


            log.info("Export #{} COMPLETED, rows={}, object={}, path={}", requestId, writer.getTotalRowsWritten(), objectKey, path);

        } catch (Exception e) {

            log.error("Export #{} ERROR", requestId, e);


            /*
             * ==================================================
             * MinIO upload thành công nhưng DB COMPLETED lỗi
             * ==================================================
             *
             * Xóa object để tránh file rác.
             */
            if (uploaded && objectKey != null) {

                minioStorageService.deleteQuietly(objectKey);
            }


            /*
             * PROCESSING -> ERROR
             */
            exportService.markError(requestId, e.getMessage());


            /*
             * ==================================================
             * Publish Redis event ERROR
             * ==================================================
             */
            if (request != null) {

                realtimeService.publish(new ExportNotification(requestId, request.getUserId(), username, ExportStatus.ERROR, 0, null, e.getMessage()));
            }

        } finally {

            /*
             * ==================================================
             * Xóa temp XML của SXSSF
             * ==================================================
             */
            if (workbook != null) {

                try {

                    workbook.dispose();

                } catch (Exception ignored) {
                }


                try {

                    workbook.close();

                } catch (IOException ignored) {
                }
            }


            /*
             * ==================================================
             * Xóa file .xlsx local
             * ==================================================
             */
            if (tempFile != null) {

                try {

                    Files.deleteIfExists(tempFile);

                } catch (IOException e) {

                    log.warn("Không xóa được file temp {}", tempFile);
                }
            }
        }
    }


    private String buildObjectKey(ExportRequest request) {
        LocalDate now = LocalDate.now();
        return "exports/" + request.getExportType().toLowerCase() + "/" + now.getYear() + "/" + String.format("%02d", now.getMonthValue()) + "/" + request.getId() + "_" + UUID.randomUUID() + ".xlsx";
    }
}