package com.example.demo.service.Export;


import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ExportSheetWriter {
    /*
     * Excel 2007+:
     * 1,048,576 row / sheet.
     */
    private static final int MAX_ROWS_PER_SHEET = SpreadsheetVersion.EXCEL2007.getMaxRows();

    private final SXSSFWorkbook workbook;

    private final CellStyle headerStyle;

    private final CellStyle dateTimeStyle;

    private final CellStyle dateStyle;

    private String[] headers;

    private Sheet currentSheet;

    private int currentRowIndex;

    private int sheetNumber;

    private long totalRowsWritten;


    public ExportSheetWriter(SXSSFWorkbook workbook) {
        this.workbook = workbook;
        /*
         * Header style tạo 1 lần.
         */
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        DataFormat format = workbook.createDataFormat();
        dateTimeStyle = workbook.createCellStyle();
        dateTimeStyle.setDataFormat(format.getFormat("yyyy-mm-dd hh:mm:ss"));
        dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(format.getFormat("yyyy-mm-dd"));
    }


    public void writeHeader(String... headers) {
        this.headers = headers;
        createNewSheet();
    }


    public void writeRow(Object... values) {

        /*
         * Header đã chiếm row 0.
         */
        if (currentRowIndex >= MAX_ROWS_PER_SHEET) {
            createNewSheet();
        }

        Row row = currentSheet.createRow(currentRowIndex++);

        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            setCellValue(cell, values[i]);
        }
        totalRowsWritten++;
    }


    public long getTotalRowsWritten() {
        return totalRowsWritten;
    }

    private void createNewSheet() {

        sheetNumber++;
        currentSheet = workbook.createSheet("Data_" + sheetNumber);
        currentRowIndex = 0;

        Row header = currentSheet.createRow(currentRowIndex++);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }


    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }

        /*
         * BigDecimal xử lý trước Number.
         *
         * Excel numeric chỉ chính xác khoảng
         * 15 chữ số.
         */
        if (value instanceof BigDecimal decimal) {
            if (decimal.precision() <= 15) {
                cell.setCellValue(decimal.doubleValue());

            } else {
                /*
                 * Giá trị tiền quá lớn:
                 * ghi String để không mất precision.
                 */
                cell.setCellValue(decimal.toPlainString());
            }
            return;
        }


        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }

        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }

        if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
            cell.setCellStyle(dateTimeStyle);
            return;
        }

        if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            cell.setCellStyle(dateStyle);
            return;
        }

        cell.setCellValue(value.toString());
    }
}