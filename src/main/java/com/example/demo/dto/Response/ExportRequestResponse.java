package com.example.demo.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ExportRequestResponse {

    private Long id;

    private String exportType;

    private String exportStatus;

    private String downloadStatus;

    private String fileName;

    private String path;

    private String errorMessage;

    private LocalDateTime createdDate;

    private LocalDateTime startedDate;

    private LocalDateTime completedDate;

    private LocalDateTime downloadedDate;
}