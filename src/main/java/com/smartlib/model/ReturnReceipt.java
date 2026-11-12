package com.smartlib.model;

import java.math.BigDecimal;

public record ReturnReceipt(
        long loanItemId,
        int overdueDays,
        BigDecimal fineAmount,
        boolean copyReservedForNextMember,
        Long nextReservationId
) {
}
