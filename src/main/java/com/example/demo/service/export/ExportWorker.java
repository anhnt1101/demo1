package com.example.demo.service.export;

import com.example.demo.constants.ExportStatus;
import com.example.demo.entity.ExportRequest;
import com.example.demo.entity.User;
import com.example.demo.Event.ExportNotification;
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

    private final ExportRedisService realtimeService;

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


    public ExportWorker(ExportService exportService, MinioStorageService minioStorageService, List<ExportHandler> handlers, ExportRedisService realtimeService, UserRepository userRepository) {

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
        ExportRequest request = null;
        String username = null;
        try {
            request = exportService.getForProcessing(requestId);
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
            Path tempDirectory = Path.of(tempDir);
            Files.createDirectories(tempDirectory);
            tempFile = Files.createTempFile(tempDirectory, "export-" + requestId + "-", ".xlsx");
            workbook = new SXSSFWorkbook(sxssfWindowSize);
            workbook.setCompressTempFiles(false);
            ExportSheetWriter writer = new ExportSheetWriter(workbook);
            final ExportRequest finalRequest = request;
            final String finalUsername = username;
            handler.writeRows(request, writer, progress -> {
                realtimeService.updateProgress(requestId, progress);
                realtimeService.publish(new ExportNotification(requestId, finalRequest.getUserId(), finalUsername, ExportStatus.PROCESSING, progress, null, "Đang xuất Excel " + progress + "%"));
                log.info("Export #{} user={} progress={}%", requestId, finalRequest.getUserId(), progress);
            });
            writer.autoFitColumns();
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                workbook.write(outputStream);
            }
            String fileName = handler.suggestFileName(request);
            objectKey = buildObjectKey(request);
            minioStorageService.upload(tempFile, objectKey);
            uploaded = true;
            String path = minioStorageService.createDownloadUrl(objectKey);
            exportService.markCompleted(requestId, fileName, objectKey, path);
            realtimeService.publish(new ExportNotification(requestId, request.getUserId(), username, ExportStatus.COMPLETED, 100, fileName, "File Excel đã sẵn sàng"));
            log.info("Export #{} COMPLETED, rows={}, object={}, path={}", requestId, writer.getTotalRowsWritten(), objectKey, path);
        } catch (Exception e) {
            log.error("Export #{} ERROR", requestId, e);
            if (uploaded && objectKey != null) {
                minioStorageService.deleteQuietly(objectKey);
            }
            exportService.markError(requestId, e.getMessage());
            if (request != null) {
                realtimeService.publish(new ExportNotification(requestId, request.getUserId(), username, ExportStatus.ERROR, 0, null, e.getMessage()));
            }
        } finally {
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