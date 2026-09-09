package kg.aidarbek.smpp.protocol;

/**
 * Immutable SMPP command data, independent of transport, profile selection and response correlation.
 * Implementations must own mutable inputs and supply a stable unsigned command identity.
 */
public interface Command {
    /**
     * Returns this command's unsigned four-octet wire identity.
     *
     * @return the stable command identifier in 0..0xffffffff
     */
    long commandId();
}
