package com.zjgsu.cyy.exception;
import java.util.NoSuchElementException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiFailure.class)
    public ProblemDetail failure(ApiFailure e) { return ProblemDetail.forStatusAndDetail(e.status(),e.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(IllegalArgumentException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getMessage()); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail malformed() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid JSON request"); }
    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail missing(NoSuchElementException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,e.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Data conflict"); }
}
