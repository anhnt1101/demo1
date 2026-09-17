package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Table(name = "TRANSACTION_LOG")
public class TransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TRANSACTION_CODE")
    private String transactionCode;

    @Column(name = "ACCOUNT_NO")
    private String accountNo;

    @Column(name = "AMOUNT")
    private Long amount;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "CREATED_AT")
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm", timezone = "Asia/Ho_Chi_Minh")
    private Date createdAt;

}
