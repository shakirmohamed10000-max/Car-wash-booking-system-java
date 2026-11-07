package exception;

/** A record with the same unique value (phone, plate, service name) already exists. */
public class DuplicateException extends AppException {

    private static final long serialVersionUID = 1L;

    private final int existingId;

    public DuplicateException(String message, int existingId) {
        super(message);
        this.existingId = existingId;
    }

    /** ID of the record that already holds the value. */
    public int existingId() {
        return existingId;
    }
}
