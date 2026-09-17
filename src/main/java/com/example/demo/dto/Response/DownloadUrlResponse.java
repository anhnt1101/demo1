package com.example.demo.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DownloadUrlResponse {

    private String url;

    private LocalDateTime expiresAt;
}
