package com.zjgsu.cyy.exception;
import org.springframework.http.HttpStatus;
public class ApiFailure extends RuntimeException {
    private final HttpStatus status;
    public ApiFailure(HttpStatus status,String message) { super(message); this.status=status; }
    public HttpStatus status() { return status; }
}
