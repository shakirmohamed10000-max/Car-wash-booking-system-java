package exception;

/** The request is well formed but a business rule forbids it (for example, deleting a customer who still has vehicles). */
public class BusinessRuleException extends AppException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super(message);
    }
}
