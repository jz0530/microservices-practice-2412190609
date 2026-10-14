package com.zjgsu.cyy.dto;

// 当前尚未实现登录；buyerName 仅用于课堂演示，不代表已认证的用户身份。
public record CreateOrderRequest(String productId, String buyerName) {}