package com.example.demo.utils;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.util.*;

public class ExcelBase {

    private static final String DATE_FORMAT = "dd/MM/yyyy HH:mm";

    // --- EXPORT GENERIC ---
    public static <T> ByteArrayInputStream exportToExcel(List<T> data, LinkedHashMap<String, String> columnMap, Map<String, Map<Object, String>> valueMappings, String sheetName) {

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);

            // 1. Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            // 2. Tạo Header
            Row headerRow = sheet.createRow(0);
            int colIdx = 0;
            for (String header : columnMap.keySet()) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(header);
                cell.setCellStyle(headerStyle);
            }

            // 3. Điền dữ liệu
            int rowIdx = 1;
            int stt = 1;

            for (T item : data) {

                Row row = sheet.createRow(rowIdx++);

                int cellIdx = 0;

                for (String fieldName : columnMap.values()) {

                    Cell cell = row.createCell(cellIdx++);

                    // STT là cột đặc biệt
                    if ("sttNew".equals(fieldName)) {
                        cell.setCellValue(stt);
                        continue;
                    }

                    Object value = getFieldValue(item, fieldName);

                    if (valueMappings != null && valueMappings.containsKey(fieldName)) {
                        String displayValue = "";
                        if (value != null) {
                            displayValue = valueMappings.get(fieldName).get(value);
                        }
                        cell.setCellValue(displayValue != null ? displayValue : "");
                    } else {
                        if (value instanceof Date) {
                            cell.setCellValue(sdf.format((Date) value));
                        } else if (value instanceof Number) {
                            cell.setCellValue(((Number) value).doubleValue());
                        } else {
                            cell.setCellValue(value != null ? value.toString() : "");
                        }
                    }
                }

                stt++;
            }

            for (int i = 0; i < columnMap.size(); i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 15 * 256) sheet.setColumnWidth(i, 15 * 256);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Lỗi Export: " + e.getMessage());
        }
    }

    private static Object getFieldValue(Object obj, String fieldName) throws Exception {
        if (obj == null) return null;

        for (String part : fieldName.split("\\.")) {
            Field field = getDeclaredField(obj.getClass(), part);
            field.setAccessible(true); // Cho phép truy cập vào field private
            obj = field.get(obj);
            if (obj == null) return null;
        }
        return obj;
    }

    /**
     * Tìm kiếm Field trong Class, kể cả các Class cha (Inheritance)
     */
    private static Field getDeclaredField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // Nếu không tìm thấy ở class hiện tại, tìm ở class cha
            if (clazz.getSuperclass() != null) {
                return getDeclaredField(clazz.getSuperclass(), fieldName);
            }
            throw e;
        }
    }
}
