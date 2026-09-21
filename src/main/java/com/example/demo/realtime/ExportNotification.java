package com.example.demo.realtime;

public record ExportNotification(

        Long requestId,

        Long userId,

        String exportStatus,

        Integer progress,

        String fileName,

        String message

) {
}