package com.example.demo.controller;

import com.example.demo.dto.Request.LoginRequest;
import com.example.demo.dto.Request.RegisterRequest;
import com.example.demo.dto.Request.SearchRequest;
import com.example.demo.dto.Request.TransactionLogRequest;
import com.example.demo.dto.Response.AuthResponse;
import com.example.demo.dto.Response.LoginResponse;
import com.example.demo.entity.GroupCategory;
import com.example.demo.entity.TransactionLog;
import com.example.demo.service.TransactionLogSevice;
import com.example.demo.service.impl.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transaction-log")
@RequiredArgsConstructor
public class TransactionLogController {

    private final TransactionLogSevice transactionLogSevice;

    @PostMapping("/search-native-query")
    @PreAuthorize("hasAnyAuthority('ROLE_MAKER','ROLE_CHECKER','ROLE_ADMIN','ROLE_VIEWER')")
    public Page<TransactionLog> searchNativeQuery(@RequestBody TransactionLogRequest transactionLogRequest) {
        return transactionLogSevice.searchNativeQuery(transactionLogRequest);
    }


}
