package com.example.demo.service;

import com.example.demo.entity.ExportRequest;

import java.util.function.IntConsumer;

public interface ExportHandler {

    String getExportType();

    String suggestFileName(ExportRequest request);

    void writeRows(ExportRequest request, ExportSheetWriter writer, IntConsumer progressCallback) throws Exception;
}