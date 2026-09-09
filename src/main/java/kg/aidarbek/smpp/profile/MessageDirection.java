package kg.aidarbek.smpp.profile;
/** Explicit data_sm request direction; a response travels in the opposite direction. */
public enum MessageDirection {
    /** An ESME submits a message to a message center. */
    SUBMISSION,
    /** A message center delivers a message to an ESME. */
    DELIVERY
}
