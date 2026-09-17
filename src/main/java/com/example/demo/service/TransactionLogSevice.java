package com.example.demo.service;

import com.example.demo.dto.Request.TransactionLogRequest;
import com.example.demo.entity.TransactionLog;
import jakarta.transaction.Transaction;
import org.springframework.data.domain.Page;

import java.io.ByteArrayInputStream;

public interface TransactionLogSevice {

    Page<TransactionLog> searchNativeQuery(TransactionLogRequest transactionLogRequest);

}
