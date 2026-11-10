package com.smartlib.interfaces;

import java.math.BigDecimal;


@FunctionalInterface
public interface FinePolicy {
    BigDecimal calculateFine(long overdueDays);
}
