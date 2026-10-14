package com.zjgsu.cyy.exception;

public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException() {
        super("商品已被占用，不能重复购买");
    }
}