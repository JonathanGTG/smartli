package com.smartlib.model;

import java.time.LocalDate;

public record BorrowReceipt(long loanId, long loanItemId, LocalDate dueDate, boolean fromReservation) {
}
