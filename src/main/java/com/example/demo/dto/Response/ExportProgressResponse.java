package com.example.demo.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/*
 * Dùng riêng cho GET /{id}/progress — endpoint fallback
 * khi FE reconnect WebSocket và cần biết ngay export
 * đang ở đâu, không phải chờ message realtime tiếp theo.
 */
@Getter
@AllArgsConstructor
public class ExportProgressResponse {

    private Long requestId;

    private String exportStatus;

    /*
     * 0-100. Với NEW luôn là 0, COMPLETED luôn là 100,
     * PROCESSING lấy từ Redis (null nếu Redis không có
     * dữ liệu - ví dụ Redis vừa restart).
     */
    private Integer progress;
}