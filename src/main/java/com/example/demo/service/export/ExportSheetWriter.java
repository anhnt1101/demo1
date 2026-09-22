package com.example.demo.service;


import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ExportSheetWriter {
    /*
     * Excel 2007+:
     * 1,048,576 row / sheet.
     */
    private static final int MAX_ROWS_PER_SHEET = SpreadsheetVersion.EXCEL2007.getMaxRows();

    /*
     * Độ dài "yyyy-mm-dd hh:mm:ss" / "yyyy-mm-dd" khi hiển thị
     * trong Excel theo dateTimeStyle/dateStyle bên dưới -
     * dùng cố định thay vì gọi value.toString() (LocalDateTime.toString()
     * không khớp format hiển thị thật, VD thiếu giây khi =0).
     */
    private static final int DATETIME_DISPLAY_LENGTH = 19;

    private static final int DATE_DISPLAY_LENGTH = 10;

    /*
     * Cột rộng tối đa (ký tự) khi auto-fit, tránh cột như
     * DESCRIPTION có 1 dòng siêu dài kéo cả sheet ra quá khổ.
     */
    private static final int MAX_COLUMN_WIDTH_CHARS = 60;

    private final SXSSFWorkbook workbook;

    private final CellStyle headerStyle;

    private final CellStyle dateTimeStyle;

    private final CellStyle dateStyle;

    private String[] headers;

    private Sheet currentSheet;

    private int currentRowIndex;

    private int sheetNumber;

    private long totalRowsWritten;

    /*
     * ==================================================
     * TỰ ĐO ĐỘ RỘNG CỘT (thay cho autoSizeColumn của POI)
     * ==================================================
     *
     * Chỉ giữ độ dài lớn nhất từng cột (1 mảng int nhỏ),
     * không giữ lại dữ liệu -> tương thích với SXSSF streaming.
     */
    private int[] maxColumnCharLength;

    /*
     * Cần giữ tham chiếu tất cả sheet đã tạo (trường hợp
     * export > 1,048,576 dòng phải tách nhiều sheet) để
     * autoFitColumns() áp dụng width cho TẤT CẢ sheet ở cuối,
     * vì độ dài tối đa 1 cột chỉ biết chính xác sau khi
     * ghi xong toàn bộ dữ liệu (mọi sheet).
     */
    private final List<Sheet> allSheets = new ArrayList<>();


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

        this.maxColumnCharLength = new int[headers.length];

        for (int i = 0; i < headers.length; i++) {
            maxColumnCharLength[i] = headers[i].length();
        }

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
            updateMaxColumnLength(i, values[i]);
        }
        totalRowsWritten++;
    }


    public long getTotalRowsWritten() {
        return totalRowsWritten;
    }

    private void createNewSheet() {

        sheetNumber++;
        currentSheet = workbook.createSheet("Data_" + sheetNumber);
        allSheets.add(currentSheet);
        currentRowIndex = 0;

        Row header = currentSheet.createRow(currentRowIndex++);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }


    /*
     * Cập nhật độ dài lớn nhất đã thấy của cột columnIndex.
     *
     * Chỉ so sánh int, không giữ lại giá trị -> chi phí
     * bộ nhớ không tăng theo số dòng.
     */
    private void updateMaxColumnLength(int columnIndex, Object value) {

        if (value == null) {
            return;
        }

        int length;

        if (value instanceof LocalDateTime) {

            length = DATETIME_DISPLAY_LENGTH;

        } else if (value instanceof LocalDate) {

            length = DATE_DISPLAY_LENGTH;

        } else if (value instanceof BigDecimal decimal) {

            length = decimal.toPlainString().length();

        } else {

            length = value.toString().length();
        }

        if (length > maxColumnCharLength[columnIndex]) {

            maxColumnCharLength[columnIndex] = length;
        }
    }


    /*
     * ==================================================
     * Gọi 1 LẦN sau khi ghi xong TOÀN BỘ dữ liệu
     * (trước workbook.write()).
     * ==================================================
     *
     * Set độ rộng cho MỌI sheet đã tạo, vì cùng 1 export chỉ
     * có 1 bộ cột giống nhau ở mọi sheet (khi phải tách nhiều
     * sheet do > 1,048,576 dòng).
     */
    public void autoFitColumns() {

        if (headers == null) {
            return;
        }

        for (int col = 0; col < headers.length; col++) {

            int charWidth = Math.min(maxColumnCharLength[col] + 2, MAX_COLUMN_WIDTH_CHARS);

            /*
             * Đơn vị Excel column width = 1/256 độ rộng ký tự "0".
             */
            int width = charWidth * 256;

            for (Sheet sheet : allSheets) {

                sheet.setColumnWidth(col, width);
            }
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