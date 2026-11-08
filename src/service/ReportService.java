package service;

import app.Config;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import model.Booking;
import model.BookingStatus;
import model.Payment;
import storage.BookingRepository;
import storage.PaymentRepository;

/** Aggregates operational activity for one local business date. */
public final class ReportService {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final ZoneId zone;

    public ReportService(BookingRepository bookings, PaymentRepository payments) {
        this(bookings, payments, Config.ZONE);
    }
    public ReportService(BookingRepository bookings, PaymentRepository payments, ZoneId zone) {
        this.bookings = bookings;
        this.payments = payments;
        this.zone = zone;
    }

    public DailyReport forDate(LocalDate date) {
        List<Booking> bs = bookings.all();
        int created = (int) bs.stream().filter(b -> localDate(b.createdAt()).equals(date)).count();
        List<Booking> completed = bs.stream().filter(b -> b.status() == BookingStatus.COMPLETED
                && b.completedAt() != null && localDate(b.completedAt()).equals(date)).toList();
        int cancelled = (int) bs.stream().filter(b -> b.status() == BookingStatus.CANCELLED
                && b.cancelledAt() != null && localDate(b.cancelledAt()).equals(date)).count();
        long washValue = completed.stream().mapToLong(Booking::total).sum();
        List<Payment> dayPayments = payments.all().stream().filter(p -> localDate(p.createdAt()).equals(date)).toList();
        long cash = sum(dayPayments, Payment.Type.PAYMENT, Payment.Method.CASH);
        long mpesa = sum(dayPayments, Payment.Type.PAYMENT, Payment.Method.MPESA);
        long card = sum(dayPayments, Payment.Type.PAYMENT, Payment.Method.CARD);
        long refunds = dayPayments.stream().filter(p -> p.type() == Payment.Type.REFUND).mapToLong(Payment::amount).sum();
        return new DailyReport(date, created, completed.size(), cancelled, washValue, cash, mpesa, card, refunds);
    }

    private LocalDate localDate(java.time.Instant instant) { return instant.atZone(zone).toLocalDate(); }
    private static long sum(List<Payment> list, Payment.Type type, Payment.Method method) {
        return list.stream().filter(p -> p.type() == type && p.method() == method).mapToLong(Payment::amount).sum();
    }
}
