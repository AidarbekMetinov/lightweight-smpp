package kg.aidarbek.smpp.profile;

/** Explicit implemented profile identities; an unknown peer advertisement is retained separately. */
public enum SmppVersion {
    /** SMPP 3.4, interface version 0x34. */
    V3_4(0x34),
    /** SMPP 5.0, interface version 0x50. */
    V5_0(0x50);

    private final int interfaceVersion;

    SmppVersion(int interfaceVersion) {
        this.interfaceVersion = interfaceVersion;
    }

    /**
     * Returns the unsigned interface-version octet for this profile.
     *
     * @return the unsigned interface-version octet for this profile
     */
    public int interfaceVersion() {
        return interfaceVersion;
    }
}
