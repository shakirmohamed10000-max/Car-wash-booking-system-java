package service;

/** What actually happened when a record was deleted. */
public enum DeleteOutcome {
    /** The record was removed from the file. */
    DELETED,
    /** The record has history, so it was hidden but kept. */
    ARCHIVED
}
