package exception;

/**
 * Reading or writing a data file failed. The message is deliberately generic;
 * the technical cause is written to the log file only.
 */
public class StorageException extends AppException {

    private static final long serialVersionUID = 1L;

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
