package com.example.demo.service.export;

import com.example.demo.entity.ExportRequest;
import com.example.demo.service.ExportSheetWriter;

import java.util.function.IntConsumer;

public interface ExportHandler {

    String getExportType();

    String suggestFileName(ExportRequest request);

    void writeRows(ExportRequest request, ExportSheetWriter writer, IntConsumer progressCallback) throws Exception;
}