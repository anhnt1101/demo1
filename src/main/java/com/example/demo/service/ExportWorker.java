package com.example.demo.service;

import com.example.demo.entity.ExportRequest;
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


    @Value("${export.sxssf-window-size:200}")
    private int sxssfWindowSize;


    @Value("${export.temp-dir:${java.io.tmpdir}}")
    private String tempDir;


    public ExportWorker(ExportService exportService, MinioStorageService minioStorageService, List<ExportHandler> handlers) {

        this.exportService = exportService;

        this.minioStorageService = minioStorageService;

        this.handlerByType = handlers.stream().collect(Collectors.toMap(ExportHandler::getExportType, handler -> handler));
    }


    public void process(Long requestId) {

        Path tempFile = null;

        SXSSFWorkbook workbook = null;

        String objectKey = null;

        boolean uploaded = false;

        try {
            ExportRequest request = exportService.getForProcessing(requestId);
            ExportHandler handler = handlerByType.get(request.getExportType());

            if (handler == null) {
                throw new IllegalStateException("Không có handler cho " + request.getExportType());
            }

            Path tempDirectory = Path.of(tempDir);
            Files.createDirectories(tempDirectory);
            tempFile = Files.createTempFile(tempDirectory, "export-" + requestId + "-", ".xlsx");

            /*
             * Window nhỏ -> RAM thấp.
             */
            workbook = new SXSSFWorkbook(sxssfWindowSize);

            /*
             * false ưu tiên tốc độ.
             *
             * true nếu server thiếu temp disk.
             */
            workbook.setCompressTempFiles(false);

            ExportSheetWriter writer = new ExportSheetWriter(workbook);

            /*
             * Oracle -> JDBC streaming -> SXSSF.
             */
            handler.writeRows(request, writer);

            /*
             * SXSSF -> local .xlsx
             */
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                workbook.write(outputStream);
            }

            String fileName = handler.suggestFileName(request);

            objectKey = buildObjectKey(request);

            /*
             * Local -> MinIO.
             */
            minioStorageService.upload(tempFile, objectKey);

            uploaded = true;

            String path = minioStorageService.createDownloadUrl(objectKey);

            /*
             * Chỉ COMPLETED khi upload MinIO
             * thành công.
             */
            exportService.markCompleted(requestId, fileName, objectKey, path);

            log.info("Export #{} COMPLETED, rows={}, object={}, path={}", requestId, writer.getTotalRowsWritten(), objectKey, path);

        } catch (Exception e) {
            log.error("Export #{} ERROR", requestId, e);
            /*
             * MinIO upload thành công nhưng DB
             * COMPLETED lỗi -> xóa object rác.
             */
            if (uploaded && objectKey != null) {
                minioStorageService.deleteQuietly(objectKey);
            }
            exportService.markError(requestId, e.getMessage());
        } finally {
            /*
             * Xóa temp XML của SXSSF.
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
             * Xóa file .xlsx local.
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
