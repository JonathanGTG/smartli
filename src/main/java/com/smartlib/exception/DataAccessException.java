package com.smartlib.exception;

/** Pembungkus SQLException agar lapisan service/UI tidak bergantung pada java.sql. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) { super(message, cause); }
}
