package com.example.demo.service.impl;

import com.example.demo.constants.DownloadStatus;
import com.example.demo.constants.ExportStatus;
import com.example.demo.dto.Request.CreateExportRequest;
import com.example.demo.dto.Response.DownloadUrlResponse;
import com.example.demo.dto.Response.ExportRequestResponse;
import com.example.demo.entity.ExportRequest;
import com.example.demo.kafka.ExportJobPublisher;
import com.example.demo.repository.ExportRequestRepository;
import com.example.demo.service.ExportHandler;
import com.example.demo.service.ExportService;
import com.example.demo.service.MinioStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExportServiceImpl implements ExportService {

    private final ExportRequestRepository repository;

    private final ObjectMapper objectMapper;

    private final MinioStorageService minioStorageService;

    private final ExportJobPublisher exportJobPublisher;

    private final Map<String, ExportHandler> handlerByType;

    public ExportServiceImpl(ExportRequestRepository repository, ObjectMapper objectMapper, MinioStorageService minioStorageService, List<ExportHandler> handlers, ExportJobPublisher exportJobPublisher) {

        this.repository = repository;

        this.objectMapper = objectMapper;

        this.minioStorageService = minioStorageService;

        this.exportJobPublisher = exportJobPublisher;

        this.handlerByType = handlers.stream().collect(Collectors.toMap(ExportHandler::getExportType, handler -> handler));
    }

    @Override
    public ExportRequestResponse createRequest(Long userId, CreateExportRequest request) {

        /*
         * 1. Kiểm tra loại export có Handler hay không.
         */
        if (!handlerByType.containsKey(request.getExportType())) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không hỗ trợ exportType: " + request.getExportType());
        }

        /*
         * 2. Tạo EXPORT_REQUEST.
         */
        ExportRequest entity = new ExportRequest();

        entity.setUserId(userId);

        entity.setExportType(request.getExportType());

        entity.setParams(toJson(request.getParams()));

        entity.setExportStatus(ExportStatus.NEW);

        entity.setDownloadStatus(DownloadStatus.NOT_DOWNLOADED);


        /*
         * 3. Lưu Oracle trước.
         *
         * Không đặt @Transactional trên method này
         * để transaction repository kết thúc trước
         * khi publish Kafka.
         */
        entity = repository.saveAndFlush(entity);


        try {

            /*
             * 4. Gửi requestId sang Kafka.
             *
             * Ví dụ:
             *
             * EXPORT_REQUEST ID = 28
             *
             * Kafka message = "28"
             */
            exportJobPublisher.publish(entity.getId());

        } catch (Exception e) {

            log.error("Publish Kafka export #{} thất bại", entity.getId(), e);


            /*
             * DB đã NEW nhưng Kafka không nhận được.
             *
             * Không được để NEW nằm kẹt mãi.
             */
            repository.markNewError(entity.getId(), truncate("Kafka publish error: " + e.getMessage(), 4000));


            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không gửi được yêu cầu export vào Kafka");
        }


        /*
         * 5. API trả request cho FE.
         */
        return toResponse(entity);
    }

    @Override
    public List<ExportRequestResponse> findAllByUserId(Long userId) {

        return repository.findAllByUserIdOrderByCreatedDateDesc(userId).stream().map(this::toResponse).toList();
    }


    @Override
    public boolean tryClaim(Long id) {
        return repository.claim(id) == 1;
    }

    @Override
    public void markCompleted(Long id, String fileName, String objectKey, String path) {
        int updated = repository.markCompleted(id, fileName, objectKey, path);
        if (updated != 1) {
            throw new IllegalStateException("Không thể chuyển export #" + id + " sang COMPLETED");
        }
    }


    @Override
    public void markError(Long id, String message) {
        repository.markError(id, truncate(message, 4000));
    }

    @Override
    public ExportRequest getForProcessing(Long id) {
        ExportRequest request = repository.findById(id).orElseThrow(() -> new IllegalStateException("Không tìm thấy export #" + id));
        if (!ExportStatus.PROCESSING.equals(request.getExportStatus())) {
            throw new IllegalStateException("Export #" + id + " không ở PROCESSING");
        }
        return request;
    }


    @Override
    public ExportRequestResponse getStatus(Long userID, Long id) {
        return toResponse(getOwned(userID, id));
    }

    @Override
    public DownloadUrlResponse issueDownloadUrl(Long userID, Long id) {
        ExportRequest request = getOwned(userID, id);

        if (!ExportStatus.COMPLETED.equals(request.getExportStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "File chưa export xong");
        }

        if (request.getPath() == null || request.getPath().isBlank()) {

            throw new ResponseStatusException(HttpStatus.GONE, "URL tải file không tồn tại");
        }
        repository.markDownloaded(id);
        LocalDateTime expiresAt = request.getCompletedDate().plusHours(24);
        return new DownloadUrlResponse(request.getPath(), expiresAt);
    }


    private ExportRequest getOwned(Long userID, Long id) {
        Optional<ExportRequest> optional = repository.findByIdAndUserId(id, userID);

        if (optional.isEmpty()) {
            throw new AccessDeniedException("Không có quyền truy cập export #" + id);
        }

        return optional.get();
    }


    private String toJson(Object value) {

        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "params không hợp lệ");
        }
    }


    private String truncate(String value, int max) {

        if (value == null) {
            return "Lỗi không xác định";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private ExportRequestResponse toResponse(ExportRequest e) {
        return new ExportRequestResponse(e.getId(), e.getExportType(), e.getExportStatus(), e.getDownloadStatus(), e.getFileName(), e.getErrorMessage(), e.getPath(), e.getCreatedDate(), e.getStartedDate(), e.getCompletedDate(), e.getDownloadedDate());
    }
}