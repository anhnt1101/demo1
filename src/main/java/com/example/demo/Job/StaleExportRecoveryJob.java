package com.example.demo.Job;

import com.example.demo.service.export.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * ==================================================
 * VẤN ĐỀ TRƯỚC ĐÂY
 * ==================================================
 *
 * File Job/ExportRequestJob.java (bản cũ, toàn bộ comment)
 * đã bị XÓA HẲN ở bản mới.
 *
 * -> Không còn cơ chế nào phát hiện EXPORT_REQUEST
 *    kẹt ở PROCESSING khi worker crash / pod restart
 *    giữa chừng (sau khi Kafka consumer tryClaim(),
 *    trước khi ExportWorker gọi markCompleted/markError).
 *
 * Request đó sẽ kẹt PROCESSING VĨNH VIỄN, user không
 * bao giờ nhận được kết quả (không COMPLETED, không ERROR).
 *
 * ==================================================
 * GIẢI PHÁP
 * ==================================================
 *
 * @EnableScheduling đã có sẵn ở Demo1Application,
 * nên chỉ cần @Scheduled ở đây là đủ, không cần
 * cấu hình thêm.
 *
 * Định kỳ (mặc định 60s) quét các request PROCESSING
 * có STARTED_DATE quá cũ (mặc định 120 phút) và
 * chuyển sang ERROR + báo realtime cho user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaleExportRecoveryJob {

    private final ExportService exportService;

    @Scheduled(fixedDelayString = "${export.stale-check-interval-ms:60000}")
    public void recoverStale() {

        try {

            exportService.recoverStale();

        } catch (Exception e) {

            /*
             * Job chạy định kỳ, không được để 1 lần lỗi
             * làm @Scheduled ngừng chạy vĩnh viễn.
             */
            log.error("StaleExportRecoveryJob chạy lỗi", e);
        }
    }
}