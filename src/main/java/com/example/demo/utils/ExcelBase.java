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
    public static <T> ByteArrayInputStream exportToExcel(
            List<T> data,
            LinkedHashMap<String, String> columnMap,
            Map<String, Map<Object, String>> valueMappings,
            String sheetName) {

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
            for (T item : data) {
                Row row = sheet.createRow(rowIdx++);
                int cellIdx = 0;
                for (String fieldName : columnMap.values()) {
                    Cell cell = row.createCell(cellIdx++);
                    Object value = getFieldValue(item, fieldName);

                    if (valueMappings != null && valueMappings.containsKey(fieldName)) {
                        String displayValue = "";
                        if (value != null) {
                            displayValue = valueMappings.get(fieldName).get(value);
                        }

                        cell.setCellValue(displayValue != null ? displayValue : "");
                    } else {
                        // ... (Logic xử lý Date, Number như cũ) ...
                        if (value instanceof Date) {
                            cell.setCellValue(sdf.format((Date) value));
                        } else if (value instanceof Number) {
                            cell.setCellValue(((Number) value).doubleValue());
                        } else {
                            cell.setCellValue(value != null ? value.toString() : "");
                        }
                    }
                }
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

    // Hàm hỗ trợ kiểm tra dòng trống
    private static boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    // Hàm hỗ trợ đọc giá trị ô bất kể kiểu dữ liệu
    private static Object getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? cell.getDateCellValue() : cell.getNumericCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            default -> "";
        };
    }

    // Hàm hỗ trợ set giá trị (xử lý ép kiểu dữ liệu)
    private static void setFieldValue(Object obj, String fieldName, Object value, SimpleDateFormat sdf) throws Exception {
        if (value == null || value.toString().isEmpty()) return;

        if (fieldName.contains(".")) {
            String[] parts = fieldName.split("\\.");
            Object currentObj = obj;

            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                Field field = getDeclaredField(currentObj.getClass(), part);
                field.setAccessible(true);

                Object nextObj = field.get(currentObj);
                if (nextObj == null) {
                    // TỰ ĐỘNG KHỞI TẠO: Ví dụ tạo mới ComponentCode()
                    nextObj = field.getType().getDeclaredConstructor().newInstance();
                    field.set(currentObj, nextObj);
                }
                currentObj = nextObj;
            }
            fieldName = parts[parts.length - 1];
            obj = currentObj;
        }

        // --- ĐOẠN GÁN GIÁ TRỊ CUỐI CÙNG (GIỮ NGUYÊN) ---
        Field field = getDeclaredField(obj.getClass(), fieldName);
        field.setAccessible(true);
        Class<?> type = field.getType();

        if (type == String.class) {
            field.set(obj, value.toString());
        } else if (type == Integer.class || type == int.class) {
            field.set(obj, value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString().trim()));
        } else if (type == Long.class || type == long.class) {
            field.set(obj, value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString().trim()));
        } else if (type == Date.class) {
            if (value instanceof Date) field.set(obj, value);
            else field.set(obj, sdf.parse(value.toString()));
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
