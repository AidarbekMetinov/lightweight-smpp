package kg.aidarbek.smpp.session;

/** SMPP endpoint identity, independent of which endpoint opens the network connection. */
public enum EndpointRole {
    /** External Short Messaging Entity. */
    ESME,
    /** Message center, also called an SMSC. */
    MESSAGE_CENTER
}
