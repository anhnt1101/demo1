package com.example.demo.service.export;

import com.example.demo.dto.Request.CreateExportRequest;
import com.example.demo.dto.Response.DownloadUrlResponse;
import com.example.demo.dto.Response.ExportProgressResponse;
import com.example.demo.dto.Response.ExportRequestResponse;
import com.example.demo.entity.ExportRequest;

import java.util.List;

public interface ExportService {

    ExportRequestResponse createRequest(Long id, CreateExportRequest request);

    List<ExportRequestResponse> findAllByUserId(Long userId);

    boolean tryClaim(Long id);

    void markCompleted(Long id, String fileName, String objectKey, String path);

    void markError(Long id, String message);

    ExportRequest getForProcessing(Long id);

    ExportRequestResponse getStatus(Long userID, Long id);

    /*
     * Fallback cho FE khi WebSocket reconnect:
     * đọc trạng thái DB + progress trong Redis (nếu có),
     * thay vì chỉ ngồi chờ message realtime tiếp theo.
     */
    ExportProgressResponse getProgress(Long userID, Long id);

    DownloadUrlResponse issueDownloadUrl(Long userID, Long id);

    void deleteRequest(Long userID, Long id);

}