//package com.example.demo.controller;
//
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequestMapping("/api/admin")
//public class AdminController {
//    @GetMapping("/dashboard")
////    @PreAuthorize("hasRole('ADMIN')")
//    public String adminDashboard() {
//        return "Chỉ ADMIN mới xem được";
//    }
//
//    @GetMapping("/reports")
//    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
//    public String reports() {
//        return "ADMIN hoặc MANAGER đều xem được";
//    }
//}
