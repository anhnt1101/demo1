package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import javax.naming.Name;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Table(name = "PMH_GROUP_CATEGORY")
public class GroupCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PARAM_NAME")
    private String paramName;

    @Column(name = "PARAM_VALUE")
    private String paramValue;

    @Column(name = "PARAM_TYPE")
    private String paramType;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "COMPONENT_CODE")
    private String componentCode;

    @Column(name = "STATUS")
    private Integer status;

    @Column(name = "IS_ACTIVE")
    private Integer isActive;

    @Column(name = "IS_DISPLAY")
    private Integer isDisplay;

    @Column(name = "NEW_DATA")
    private String newData;

    @Column(name = "EFFECTIVE_DATE")
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm", timezone = "Asia/Ho_Chi_Minh")
    private Date effectiveDate;

    @Column(name = "END_EFFECTIVE_DATE")
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm", timezone = "Asia/Ho_Chi_Minh")
    private Date endEffectiveDate;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "CREATED_DATE")
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private Date createdDate;

    @Column(name = "UPDATED_BY")
    private String updateBy;

    @Column(name = "UPDATED_DATE")
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private Date updateDate;
}
