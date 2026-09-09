package kg.aidarbek.smpp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommonValuesTest {
    private static final Address SOURCE = new Address(0, 0, "");
    private static final OptionalParameters EMPTY = new OptionalParameters(List.of());

    @Test
    void queryIdentityRejectsInvalidRepresentationWithoutExposingIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new QuerySm("x".repeat(65), SOURCE, EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new QuerySm("id\0tail", SOURCE, EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new QuerySm("é", SOURCE, EMPTY));
        assertThrows(NullPointerException.class, () -> new QuerySm(null, SOURCE, EMPTY));
        assertThrows(NullPointerException.class, () -> new QuerySm("id", null, EMPTY));
        assertThrows(NullPointerException.class, () -> new QuerySm("id", SOURCE, null));
    }

    @Test
    void commonValuesValidateRepresentationAndDoNotPrintCredentialsOrOpaqueIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new Outbind("x".repeat(16), "", EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new Outbind("", "x".repeat(9), EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new Outbind("", "pass\0word", EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new CancelSm("123456", "", SOURCE, SOURCE, EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new CancelSm("", "x".repeat(65), SOURCE, SOURCE, EMPTY));
        assertThrows(NullPointerException.class, () -> new AlertNotification(SOURCE, null, EMPTY));
        assertThrows(NullPointerException.class, () -> new CancelSmResponse(null));
        assertThrows(NullPointerException.class, () -> new ReplaceSmResponse(null));
        assertThrows(IllegalArgumentException.class, () -> new QuerySmResponse.Result("", "x".repeat(17), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuerySmResponse.Result("", "", 256, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuerySmResponse.Result("", "", 1, -1));
        OptionalParameters present = new OptionalParameters(List.of(new Tlv(0x1401, new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> new QuerySmResponse(Optional.empty(), present));
        assertThrows(IllegalArgumentException.class, () -> new SubmitMultiResponse(Optional.empty(), present));
        assertFalse(new Outbind("secret-system", "password", EMPTY).toString().contains("password"));
        assertFalse(new QuerySm("secret-message", SOURCE, EMPTY).toString().contains("secret-message"));
        assertFalse(new QuerySmResponse.Result("secret-message", "", 7, 0)
                .toString()
                .contains("secret-message"));
        assertFalse(
                new MultiDestination.DistributionList("secret-list").toString().contains("secret-list"));
    }

    @Test
    void multipleDestinationsAndFailuresOwnListsAndApplyUnsignedRepresentationBounds() {
        List<MultiDestination> proposed = new ArrayList<>(List.of(new MultiDestination.Sme(SOURCE)));
        SubmitMulti multi = multi(proposed, new byte[] {0, (byte) 255});
        proposed.clear();
        assertEquals(1, multi.destinations().size());
        assertThrows(
                UnsupportedOperationException.class, () -> multi.destinations().clear());
        assertThrows(IllegalArgumentException.class, () -> multi(List.of(), new byte[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> multi(Collections.nCopies(256, new MultiDestination.Sme(SOURCE)), new byte[0]));
        assertThrows(
                IllegalArgumentException.class, () -> multi(List.of(new MultiDestination.Sme(SOURCE)), new byte[256]));
        assertThrows(IllegalArgumentException.class, () -> new MultiDestination.DistributionList("x".repeat(21)));
        assertThrows(NullPointerException.class, () -> new MultiDestination.Sme(null));
        assertThrows(IllegalArgumentException.class, () -> new UnsuccessfulDestination(SOURCE, -1));
        assertThrows(IllegalArgumentException.class, () -> new UnsuccessfulDestination(SOURCE, 0x1_0000_0000L));
        List<UnsuccessfulDestination> failures =
                new ArrayList<>(List.of(new UnsuccessfulDestination(SOURCE, 0xffff_ffffL)));
        SubmitMultiResponse.Result result = new SubmitMultiResponse.Result("id", failures);
        failures.clear();
        assertEquals(1, result.unsuccessful().size());
        assertThrows(
                UnsupportedOperationException.class, () -> result.unsuccessful().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SubmitMultiResponse.Result(
                        "id", Collections.nCopies(256, new UnsuccessfulDestination(SOURCE, 1))));
    }

    @Test
    void replacementOwnsPayloadAndBoundsEveryRawOctet() {
        byte[] bytes = new byte[] {0, (byte) 255};
        ReplaceSm replacement = replace(new OctetString(bytes), 255, 255);
        bytes[1] = 0;
        assertEquals(255, replacement.shortMessage().value()[1] & 255);
        assertThrows(IllegalArgumentException.class, () -> replace(new OctetString(new byte[256]), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> replace(new OctetString(new byte[0]), -1, 0));
        assertThrows(IllegalArgumentException.class, () -> replace(new OctetString(new byte[0]), 0, 256));
    }

    private static SubmitMulti multi(List<MultiDestination> destinations, byte[] payload) {
        return new SubmitMulti("", SOURCE, destinations, 0, 0, 0, "", "", 0, 0, 0, 0, new OctetString(payload), EMPTY);
    }

    private static ReplaceSm replace(OctetString payload, int registered, int defaultId) {
        return new ReplaceSm("id", SOURCE, "", "", registered, defaultId, payload, EMPTY);
    }
}
