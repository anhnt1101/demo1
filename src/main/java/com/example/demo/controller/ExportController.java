package com.example.demo.controller;

import com.example.demo.dto.Request.CreateExportRequest;
import com.example.demo.dto.Response.DownloadUrlResponse;
import com.example.demo.dto.Response.ExportProgressResponse;
import com.example.demo.dto.Response.ExportRequestResponse;
import com.example.demo.entity.User;
import com.example.demo.service.export.ExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/export-requests")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @PostMapping
    public ResponseEntity<ExportRequestResponse> create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateExportRequest request) {
        return ResponseEntity.ok(exportService.createRequest(user.getId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExportRequestResponse> getStatus(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(exportService.getStatus(user.getId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {

        exportService.deleteRequest(user.getId(), id);

        return ResponseEntity.noContent().build();
    }

    /*
     * Fallback khi FE reconnect WebSocket (mở lại tab,
     * mất mạng chốc lát...) - lấy ngay trạng thái + %
     * hiện tại thay vì phải chờ message realtime kế tiếp.
     */
    @GetMapping("/{id}/progress")
    public ResponseEntity<ExportProgressResponse> getProgress(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(exportService.getProgress(user.getId(), id));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ExportRequestResponse>> mine(@AuthenticationPrincipal User user) {

        return ResponseEntity.ok(exportService.findAllByUserId(user.getId()));
    }

    /*
     * BE kiểm tra owner trước,
     * sau đó cấp MinIO Presigned URL.
     */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<DownloadUrlResponse> downloadUrl(@AuthenticationPrincipal User user, @PathVariable Long id) {

        return ResponseEntity.ok(exportService.issueDownloadUrl(user.getId(), id));
    }

}