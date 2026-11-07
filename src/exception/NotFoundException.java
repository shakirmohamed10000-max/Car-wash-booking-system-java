package exception;

/** A requested record does not exist (or is no longer visible). */
public class NotFoundException extends AppException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
