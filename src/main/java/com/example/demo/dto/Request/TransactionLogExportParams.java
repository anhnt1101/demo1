package com.example.demo.dto.Request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class TransactionLogExportParams {

    private Long id;

    private String transactionCode;

    private String accountNo;

    private BigDecimal amountFrom;

    private BigDecimal amountTo;

    private String status;

    private LocalDateTime fromDate;

    private LocalDateTime toDate;

    private String description;

    private String referenceNo;
}