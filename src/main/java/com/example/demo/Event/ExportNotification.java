package com.example.demo.Event;

public record ExportNotification(

        Long requestId,

        Long userId,

        String username,

        String exportStatus,

        Integer progress,

        String fileName,

        String message

) {
}