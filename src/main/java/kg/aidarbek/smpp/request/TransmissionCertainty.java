package kg.aidarbek.smpp.request;

/** What local request tracking can prove about transmission; neither value proves peer acceptance. */
public enum TransmissionCertainty {
    /** The before-write guard has never authorized physical writing. */
    NOT_SENT,
    /** Writing has been authorized; any amount, including zero bytes, might have reached the peer. */
    MAY_HAVE_BEEN_SENT
}
