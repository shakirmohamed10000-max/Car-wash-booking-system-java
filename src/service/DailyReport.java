package service;

import java.time.LocalDate;

public record DailyReport(LocalDate date, int bookingsCreated, int washesCompleted, int cancellations,
                          long completedWashValue, long cashReceived, long mpesaReceived,
                          long cardReceived, long refunds) {
    public long totalReceived() { return cashReceived + mpesaReceived + cardReceived; }
    public long netPayments() { return totalReceived() - refunds; }
}
