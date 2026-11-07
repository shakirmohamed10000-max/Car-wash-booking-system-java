package exception;

/** Input did not meet a format or range rule. */
public class ValidationException extends AppException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
