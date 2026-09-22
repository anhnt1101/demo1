package com.example.demo.service.export;

import com.example.demo.constants.DownloadStatus;
import com.example.demo.constants.ExportStatus;
import com.example.demo.dto.Request.CreateExportRequest;
import com.example.demo.dto.Response.DownloadUrlResponse;
import com.example.demo.dto.Response.ExportProgressResponse;
import com.example.demo.dto.Response.ExportRequestResponse;
import com.example.demo.entity.ExportRequest;
import com.example.demo.kafka.ExportJobPublisher;
import com.example.demo.realtime.ExportNotification;
import com.example.demo.realtime.ExportRealtimeService;
import com.example.demo.repository.ExportRequestRepository;
import com.example.demo.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private final ExportRealtimeService realtimeService;

    private final UserRepository userRepository;

    @Value("${export.stale-after-minutes:120}")
    private int staleAfterMinutes;

    public ExportServiceImpl(ExportRequestRepository repository, ObjectMapper objectMapper, MinioStorageService minioStorageService, List<ExportHandler> handlers, ExportJobPublisher exportJobPublisher, ExportRealtimeService realtimeService, UserRepository userRepository) {

        this.repository = repository;

        this.objectMapper = objectMapper;

        this.minioStorageService = minioStorageService;

        this.exportJobPublisher = exportJobPublisher;

        this.realtimeService = realtimeService;

        this.userRepository = userRepository;

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
    public void recoverStale() {

        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(staleAfterMinutes);

        List<ExportRequest> staleRequests = repository.findStaleProcessing(staleBefore);

        if (staleRequests.isEmpty()) {
            return;
        }

        log.warn("Tìm thấy {} export PROCESSING quá {} phút, chuyển ERROR", staleRequests.size(), staleAfterMinutes);

        for (ExportRequest stale : staleRequests) {

            String message = "Export PROCESSING quá " + staleAfterMinutes + " phút (worker có thể đã crash), tự động đánh dấu ERROR.";

            /*
             * WHERE EXPORT_STATUS = 'PROCESSING' trong markError
             * đảm bảo nếu worker vừa markCompleted xong đúng lúc
             * job này chạy thì sẽ không ghi đè COMPLETED -> ERROR.
             */
            int updated = repository.markError(stale.getId(), message);

            if (updated != 1) {
                continue;
            }

            log.warn("Export #{} bị đánh dấu ERROR do stale (userId={})", stale.getId(), stale.getUserId());

            /*
             * Lấy username để push WebSocket, tương tự ExportWorker.
             * Không có username thì vẫn OK, chỉ là FE không nhận
             * được cập nhật realtime (bảng "mine" vẫn có ERROR khi tải lại).
             */
            String username = userRepository.findById(stale.getUserId()).map(com.example.demo.entity.User::getUsername).orElse(null);

            realtimeService.publish(new ExportNotification(stale.getId(), stale.getUserId(), username, ExportStatus.ERROR, 0, null, message));
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
    public ExportProgressResponse getProgress(Long userID, Long id) {

        ExportRequest request = getOwned(userID, id);

        Integer progress = switch (request.getExportStatus()) {

            case ExportStatus.COMPLETED -> 100;

            case ExportStatus.NEW -> 0;

            /*
             * PROCESSING / ERROR -> đọc Redis.
             *
             * ERROR vẫn có thể có progress dở dang,
             * hữu ích để FE hiển thị "lỗi lúc 47%".
             *
             * Redis miss (TTL hết hạn, Redis restart)
             * thì trả null - FE tự hiểu là "chưa rõ %".
             */
            default -> realtimeService.getProgress(id);
        };

        return new ExportProgressResponse(request.getId(), request.getExportStatus(), progress);
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
        return new ExportRequestResponse(e.getId(), e.getExportType(), e.getExportStatus(), e.getDownloadStatus(), e.getFileName(), e.getPath(), e.getErrorMessage(), e.getCreatedDate(), e.getStartedDate(), e.getCompletedDate(), e.getDownloadedDate());
    }
}