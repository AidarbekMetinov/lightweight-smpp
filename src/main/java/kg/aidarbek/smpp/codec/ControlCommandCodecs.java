package kg.aidarbek.smpp.codec;

import java.util.ArrayList;
import java.util.List;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.ControlCommand;

/** Standard binding and control registrations for a full-PDU codec. */
public final class ControlCommandCodecs {
    private ControlCommandCodecs() {}

    /**
     * Returns immutable, thread-safe codecs for the six bind identities, unbind/enquiry pairs and
     * generic_nack. Incoming optional extensions are retained raw; outgoing rules remain explicit.
     * @return an immutable registration list to compose with other command families
     */
    public static List<CommandCodec<?>> all() {
        List<CommandCodec<?>> codecs = new ArrayList<>();
        for (BindMode mode : BindMode.values()) {
            codecs.add(new BindRequestCodec(mode));
            codecs.add(new BindResponseCodec(mode));
        }
        for (ControlCommand.Type type : ControlCommand.Type.values()) {
            codecs.add(new ControlBodyCodec(type));
        }
        return List.copyOf(codecs);
    }
}
