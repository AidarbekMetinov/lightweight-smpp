package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.EMPTY;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.SOURCE;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.V5;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.broadcast;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.parameters;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import org.junit.jupiter.api.Test;

class BroadcastResponseRulesTest {
    @Test
    void queryEchoAndConcretePairingAreCheckedWithoutInventingStorageContext() {
        Pdu<QueryBroadcastSm> request = new Pdu<>(0, 1, new QueryBroadcastSm("ID", SOURCE, EMPTY));
        QueryBroadcastSmResponse good = result("ID", "04270001010606000200410608000164");
        assertDoesNotThrow(() -> BroadcastResponseRules.validate(request, new Pdu<>(0, 1, good), V5));
        assertThrows(
                IllegalArgumentException.class,
                () -> BroadcastResponseRules.validate(
                        request, new Pdu<>(0, 1, result("id", "04270001010606000200410608000164")), V5));
        assertThrows(
                IllegalArgumentException.class,
                () -> BroadcastResponseRules.validate(
                        request, new Pdu<>(0, 1, new CancelBroadcastSmResponse(EMPTY)), V5));
        Pdu<QueryBroadcastSm> referenced =
                new Pdu<>(0, 1, new QueryBroadcastSm("", SOURCE, parameters("020400020009")));
        assertDoesNotThrow(() -> BroadcastResponseRules.validate(referenced, new Pdu<>(0, 1, good), V5));
        assertThrows(
                IllegalArgumentException.class,
                () -> BroadcastResponseRules.validate(
                        referenced, new Pdu<>(0, 1, result("ID", "04270001010606000200410608000164020400020008")), V5));
        assertDoesNotThrow(() -> BroadcastResponseRules.validate(
                request,
                new Pdu<>(0x144, 1, new QueryBroadcastSmResponse(new MessageResponse(Optional.empty(), EMPTY))),
                V5));
        assertThrows(
                IllegalArgumentException.class,
                () -> BroadcastResponseRules.validate(
                        request, new Pdu<>(0, 1, good), ProtocolProfile.forVersion(SmppVersion.V3_4)));
    }

    @Test
    void failedAreasMustComeFromTheSubmissionWithMultiplicityPreserved() {
        BroadcastSm original =
                broadcast(parameters("0606000200410606000200420601000300001006040002000106050003000000"));
        Pdu<BroadcastSm> request = new Pdu<>(0, 1, original);
        for (String bad : new String[] {"060600020043", "060600020041060600020041"}) {
            BroadcastSmResponse response =
                    new BroadcastSmResponse(new MessageResponse(Optional.of("id"), parameters(bad)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BroadcastResponseRules.validate(request, new Pdu<>(0, 1, response), V5));
        }
        BroadcastSmResponse failed = new BroadcastSmResponse(
                new MessageResponse(Optional.of("id"), parameters("0606000200420607000400000144")));
        assertDoesNotThrow(() -> BroadcastResponseRules.validate(request, new Pdu<>(0, 1, failed), V5));
    }

    @Test
    void queryAreaCorrelationUsesExplicitStoredSubmissionAndAllowsResultReordering() {
        BroadcastSm original =
                broadcast(parameters("0606000200410606000200420601000300001006040002000106050003000000"));
        QueryBroadcastSmResponse same = result("id", "042700010306060002004206080001000606000200410608000164");
        assertDoesNotThrow(() -> BroadcastResponseRules.validateQueryResult(original, same));
        for (String bad : new String[] {
            "04270001030606000200410608000164", "042700010306060002004106080001640606000200410608000100"
        })
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BroadcastResponseRules.validateQueryResult(original, result("id", bad)));
    }

    static QueryBroadcastSmResponse result(String id, String parameters) {
        return new QueryBroadcastSmResponse(new MessageResponse(Optional.of(id), parameters(parameters)));
    }
}
