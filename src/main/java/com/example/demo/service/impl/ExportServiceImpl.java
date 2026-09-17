package com.example.demo.service.impl;

import com.example.demo.constants.*;
import com.example.demo.dto.Request.CreateExportRequest;
import com.example.demo.dto.Response.DownloadUrlResponse;
import com.example.demo.dto.Response.ExportRequestResponse;
import com.example.demo.entity.ExportRequest;
import com.example.demo.repository.Export.ExportRequestRepository;
import com.example.demo.service.Export.ExportHandler;
import com.example.demo.service.ExportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExportServiceImpl implements ExportService {

    private final ExportRequestRepository repository;

    private final ObjectMapper objectMapper;

    private final MinioStorageService minioStorageService;

    private final Map<String, ExportHandler> handlerByType;


    @Value("${export.stale-after-minutes:120}")
    private int staleAfterMinutes;

    public ExportServiceImpl(ExportRequestRepository repository, ObjectMapper objectMapper, MinioStorageService minioStorageService, List<ExportHandler> handlers) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.minioStorageService = minioStorageService;
        this.handlerByType = handlers.stream().collect(Collectors.toMap(ExportHandler::getExportType, handler -> handler));
    }


    @Override
    @Transactional
    public ExportRequestResponse createRequest(Long id, CreateExportRequest request) {

        if (!handlerByType.containsKey(request.getExportType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không hỗ trợ exportType: " + request.getExportType());
        }

        ExportRequest entity = new ExportRequest();
        entity.setUserId(id);
        entity.setExportType(request.getExportType());
        entity.setParams(toJson(request.getParams()));
        entity.setExportStatus(ExportStatus.NEW);
        entity.setDownloadStatus(DownloadStatus.NOT_DOWNLOADED);
        repository.save(entity);
        return toResponse(entity);
    }


    @Override
    public List<Long> findNextPendingIds(int limit) {
        return repository.findNextPendingIds(limit);
    }


    @Override
    public boolean tryClaim(Long id) {
        return repository.claim(id) == 1;
    }


    @Override
    public void resetToNew(Long id) {
        repository.resetToNew(id);
    }


    @Override
    public void markCompleted(Long id, String fileName, String objectKey) {
        int updated = repository.markCompleted(id, fileName, objectKey);
        if (updated != 1) {
            throw new IllegalStateException("Không thể chuyển export #" + id + " sang COMPLETED");
        }
    }


    @Override
    public void markError(Long id, String message) {
        repository.markError(id, truncate(message, 4000));
    }


    @Override
    public void recoverStale() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(staleAfterMinutes);
        List<Long> ids = repository.findStaleProcessingIds(before);
        for (Long id : ids) {
            repository.markError(id, "Export PROCESSING quá " + staleAfterMinutes + " phút.");
            log.warn("Export #{} bị đánh dấu ERROR do stale", id);
        }
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
    public List<ExportRequestResponse> listMine(Long userID) {
        return repository.findTop50ByUserIdOrderByCreatedDateDesc(userID).stream().map(this::toResponse).toList();
    }


    @Override
    public DownloadUrlResponse issueDownloadUrl(Long userID, Long id) {
        ExportRequest request = getOwned(userID, id);

        if (!ExportStatus.COMPLETED.equals(request.getExportStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "File chưa export xong");
        }

        if (request.getObjectKey() == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "File export không còn tồn tại");
        }

        try {
            String url = minioStorageService.createDownloadUrl(request.getObjectKey());
            /*
             * Với presigned URL,
             * DOWNLOADED nghĩa là user đã xin link tải.
             */
            repository.markDownloaded(id);
            return new DownloadUrlResponse(url, minioStorageService.calculateExpiredAt());
        } catch (Exception e) {
            throw new RuntimeException("Không tạo được URL tải file", e);
        }
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
        return new ExportRequestResponse(e.getId(), e.getExportType(), e.getExportStatus(), e.getDownloadStatus(), e.getFileName(), e.getErrorMessage(), e.getCreatedDate(), e.getStartedDate(), e.getCompletedDate(), e.getDownloadedDate());
    }
}