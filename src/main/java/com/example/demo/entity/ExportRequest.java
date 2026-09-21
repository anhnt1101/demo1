package com.example.demo.entity;

import com.example.demo.constants.DownloadStatus;
import com.example.demo.constants.ExportStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "EXPORT_REQUEST")
public class ExportRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;


    @Column(name = "USER_ID", nullable = false, length = 100)
    private Long userId;


    @Column(name = "EXPORT_TYPE", nullable = false, length = 50)
    private String exportType;

    @Column(name = "PATH", length = 4000)
    private String path;

    @Lob
    @Column(name = "PARAMS")
    private String params;


    @Column(name = "EXPORT_STATUS", nullable = false, length = 20)
    private String exportStatus = ExportStatus.NEW;


    @Column(name = "DOWNLOAD_STATUS", nullable = false, length = 30)
    private String downloadStatus = DownloadStatus.NOT_DOWNLOADED;


    @Column(name = "FILE_NAME", length = 255)
    private String fileName;


    @Column(name = "OBJECT_KEY", length = 1000)
    private String objectKey;


    @Column(name = "ERROR_MESSAGE", length = 4000)
    private String errorMessage;


    @CreationTimestamp
    @Column(name = "CREATED_DATE", nullable = false, updatable = false)
    private LocalDateTime createdDate;


    @Column(name = "STARTED_DATE")
    private LocalDateTime startedDate;


    @Column(name = "COMPLETED_DATE")
    private LocalDateTime completedDate;


    @Column(name = "DOWNLOADED_DATE")
    private LocalDateTime downloadedDate;
}