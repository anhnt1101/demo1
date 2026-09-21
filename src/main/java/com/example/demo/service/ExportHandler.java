package com.example.demo.service;

import com.example.demo.entity.ExportRequest;

public interface ExportHandler {

    String getExportType();

    String suggestFileName(ExportRequest request);

    void writeRows(ExportRequest request, ExportSheetWriter writer) throws Exception;
}