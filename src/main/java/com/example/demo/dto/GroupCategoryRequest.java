package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;

@Data
public class GroupCategoryRequest {

    private Long id;

    @NotBlank(message = "Tên thành phần không được để trống")
    @Size(max = 255, message = "Tên thành phần tối đa 255 ký tự")
    private String paramName;

    @NotBlank(message = "Giá trị thành phần không được để trống")
    @Size(max = 255, message = "Giá trị thành phần tối đa 255 ký tự")
    private String paramValue;

    @NotBlank(message = "Danh mục theo nhóm không được để trống")
    @Size(max = 255, message = "Danh mục theo nhóm tối đa 255 ký tự")
    private String paramType;

    @Size(max = 4000, message = "Mô tả tối đa 4000 ký tự")
    private String description;

    @NotBlank(message = "Vui lòng chọn cấu phần xử lý")
    @Size(max = 255, message = "Cấu phần xử lý tối đa 255 ký tự")
    private String componentCode;

    private Integer status;

    private Integer isActive;

    private Integer isDisplay;

    private String newData;

    @NotNull(message = "Vui lòng chọn ngày hiệu lực")
    @JsonFormat(shape = JsonFormat.Shape.STRING,pattern = "dd/MM/yyyy HH:mm", timezone = "Asia/Ho_Chi_Minh")
    private Date effectiveDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm", timezone = "Asia/Ho_Chi_Minh")
    private Date endEffectiveDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm")
    private Date createdDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm")
    private Date updateData;
}
