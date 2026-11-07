package ui;

/** The user typed 0 at a prompt to abandon the current action. Not an error. */
public class CancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CancelledException() {
        super("Cancelled.", null, false, false);
    }
}
