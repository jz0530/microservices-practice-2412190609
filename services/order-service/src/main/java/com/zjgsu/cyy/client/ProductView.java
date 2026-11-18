package com.zjgsu.cyy.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductView(String id, String title, String description,
                          BigDecimal price, String status) {
}
