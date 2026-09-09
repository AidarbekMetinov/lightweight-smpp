package kg.aidarbek.smpp.spi;

/** Separately bounded scheduling classes; control traffic can overtake queued ordinary frames. */
public enum WriteClass {
    /** FIFO ordinary request or response traffic. */
    ORDINARY,
    /** Finite reserved capacity for control traffic. */
    CONTROL
}
