package service;

import java.util.Locale;

/** All money is a whole number of Kenyan shillings held in a long. */
public final class Money {

    private Money() {
    }

    public static String format(long amount) {
        return "KSh " + String.format(Locale.US, "%,d", amount);
    }

    /** percent of amount, rounded half up to a whole shilling. */
    public static long percentOf(long amount, int percent) {
        return (amount * percent + 50) / 100;
    }
}
