package com.example.bigevent.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Department {
    private Integer id;
    private String name;
    private LocalDateTime createTime;
}