package storage;

import java.time.Instant;
import java.util.List;
import model.Payment;

/** Append-only payment ledger persisted in payments.txt. */
public final class PaymentRepository extends Repository<Payment> {
    public PaymentRepository(FileStore store) {
        super(store, "payments.txt", "id,bookingId,type,method,amount,tendered,reference,createdAt");
    }
    @Override protected int idOf(Payment p) { return p.id(); }
    @Override protected List<String> toFields(Payment p) {
        return List.of(String.valueOf(p.id()), String.valueOf(p.bookingId()), p.type().name(), p.method().name(),
                String.valueOf(p.amount()), String.valueOf(p.tendered()), p.reference(), p.createdAt().toString());
    }
    @Override protected Payment fromFields(List<String> f) {
        expect(f, 8);
        return new Payment(Integer.parseInt(f.get(0)), Integer.parseInt(f.get(1)), Payment.Type.valueOf(f.get(2)),
                Payment.Method.valueOf(f.get(3)), Long.parseLong(f.get(4)), Long.parseLong(f.get(5)), f.get(6),
                Instant.parse(f.get(7)));
    }
}
