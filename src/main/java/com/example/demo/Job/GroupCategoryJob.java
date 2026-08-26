package com.example.demo.Job;

import com.example.demo.service.GroupCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupCategoryJob {

    private final GroupCategoryService groupCategoryService;

    @Scheduled(cron = "0 * * * * *")
    public void testJob() {

        groupCategoryService.updateIsActiveByEffectiveDate();
        System.out.println("JOB đang chạy...");
    }

}
