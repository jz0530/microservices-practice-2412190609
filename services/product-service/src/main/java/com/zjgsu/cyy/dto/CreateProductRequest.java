package com.zjgsu.cyy.dto;

import java.math.BigDecimal;

public record CreateProductRequest(String title, String description, BigDecimal price) {}
