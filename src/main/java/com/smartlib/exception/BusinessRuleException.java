package com.smartlib.exception;

/** Exception untuk pelanggaran aturan bisnis perpustakaan. */
public class BusinessRuleException extends Exception {
    private static final long serialVersionUID = 1L;
    public BusinessRuleException(String message) {
        super(message);
    }
}
