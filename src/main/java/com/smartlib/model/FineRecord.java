package com.smartlib.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FineRecord(
        long fineId,
        long loanItemId,
        String bookTitle,
        int overdueDays,
        BigDecimal ratePerDay,
        BigDecimal amount,
        String status,
        LocalDateTime assessedAt,
        LocalDateTime paidAt
) {
}
