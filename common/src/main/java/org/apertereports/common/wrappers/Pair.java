package org.apertereports.common.wrappers;

/**
 * Represents a pair of objects.
 *
 * @param <T1> First object.
 * @param <T2> Second object.
 */
public class Pair<T1, T2> {
    private T1 key;
    private T2 entry;

    public Pair() {
    }

    public Pair(T1 key, T2 entry) {
        this.key = key;
        this.entry = entry;
    }

    public T2 getEntry() {
        return entry;
    }

    public T1 getKey() {
        return key;
    }

    public void setEntry(T2 entry) {
        this.entry = entry;
    }

    public void setKey(T1 key) {
        this.key = key;
    }
}
