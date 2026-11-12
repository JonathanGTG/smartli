package com.smartlib.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Strategy interface untuk kebijakan penghitungan denda. */
public interface FinePolicy {
    int overdueDays(LocalDate dueDate, LocalDate returnDate);
    BigDecimal calculateFine(int overdueDays);
    BigDecimal ratePerDay();
}
