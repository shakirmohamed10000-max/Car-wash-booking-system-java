package ui;

import app.Config;
import java.io.PrintStream;
import java.util.List;

/** Prints aligned text tables, a page at a time. */
public final class TablePrinter {

    private static final int MAX_CELL = 32;

    private final PrintStream out;
    private final InputReader in;

    public TablePrinter(PrintStream out, InputReader in) {
        this.out = out;
        this.in = in;
    }

    public void print(String[] headers, List<String[]> rows) {
        print(headers, new boolean[headers.length], rows);
    }

    /**
     * @param rightAligned one flag per column, true for columns that hold amounts
     */
    public void print(String[] headers, boolean[] rightAligned, List<String[]> rows) {
        int cols = headers.length;
        int[] width = new int[cols];
        for (int c = 0; c < cols; c++) {
            width[c] = headers[c].length();
            for (String[] row : rows) {
                width[c] = Math.max(width[c], Math.min(MAX_CELL, row[c].length()));
            }
        }
        printRow(headers, width, rightAligned);
        printRule(width);
        int shown = 0;
        for (String[] row : rows) {
            if (shown > 0 && shown % Config.PAGE_SIZE == 0) {
                if (!in.pause("-- " + shown + " of " + rows.size() + " shown. Enter for more, 0 to stop")) {
                    out.println(rows.size() + " record(s), " + shown + " shown.");
                    return;
                }
            }
            printRow(row, width, rightAligned);
            shown++;
        }
        out.println(rows.size() + " record(s).");
    }

    private void printRow(String[] cells, int[] width, boolean[] right) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < cells.length; c++) {
            String cell = cells[c];
            if (cell.length() > width[c]) {
                cell = cell.substring(0, Math.max(0, width[c] - 3)) + "...";
            }
            if (c > 0) {
                sb.append("  ");
            }
            String pad = " ".repeat(width[c] - cell.length());
            if (right[c]) {
                sb.append(pad).append(cell);
            } else {
                sb.append(cell).append(c == cells.length - 1 ? "" : pad);
            }
        }
        out.println(sb);
    }

    private void printRule(int[] width) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < width.length; c++) {
            if (c > 0) {
                sb.append("  ");
            }
            sb.append("-".repeat(width[c]));
        }
        out.println(sb);
    }
}
