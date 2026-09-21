package com.example.demo.service;

import com.example.demo.dto.Request.CreateExportRequest;
import com.example.demo.dto.Response.DownloadUrlResponse;
import com.example.demo.dto.Response.ExportRequestResponse;
import com.example.demo.entity.ExportRequest;

import java.util.List;
import java.util.Optional;

public interface ExportService {

    ExportRequestResponse createRequest(Long id, CreateExportRequest request);

    List<Long> findNextPendingIds(int limit);

    List<ExportRequestResponse> findAllByUserId(Long userId);

    boolean tryClaim(Long id);

    void resetToNew(Long id);

    void markCompleted(Long id, String fileName, String objectKey, String path);

    void markError(Long id, String message);

    void recoverStale();

    ExportRequest getForProcessing(Long id);

    ExportRequestResponse getStatus(Long userID, Long id);

    DownloadUrlResponse issueDownloadUrl(Long userID, Long id);
}