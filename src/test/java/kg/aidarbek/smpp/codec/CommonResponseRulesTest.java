package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.CommonCommandCodecsTest.EMPTY;
import static kg.aidarbek.smpp.codec.CommonMessageValidationTest.multi;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.CancelSmResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class CommonResponseRulesTest {
    @Test
    void queryRequiresTheQueriedIdentityAndTheCorrespondingOperation() {
        for (SmppVersion version : SmppVersion.values()) {
            ProtocolProfile profile = ProtocolProfile.forVersion(version);
            Pdu<QuerySm> request = new Pdu<>(0, 1, new QuerySm("asked", new Address(0, 0, ""), EMPTY));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CommonResponseRules.validate(request, new Pdu<>(0, 1, new CancelSmResponse(EMPTY)), profile));
            QuerySmResponse unrelated =
                    new QuerySmResponse(Optional.of(new QuerySmResponse.Result("different", "", 7, 0)), EMPTY);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CommonResponseRules.validate(request, new Pdu<>(0, 1, unrelated), profile));
            CommonResponseRules.validate(
                    request,
                    new Pdu<>(
                            0,
                            1,
                            new QuerySmResponse(Optional.of(new QuerySmResponse.Result("asked", "", 7, 0)), EMPTY)),
                    profile);
        }
    }

    @Test
    void multiResponseDiagnosticsRequireTransactionModeOnlyWhereInterpreted() {
        for (int tag : new int[] {0x001d, 0x0420, 0x0423, 0x0425}) {
            byte[] value = tag == 0x0423 ? new byte[] {1, 0, 0} : new byte[] {0};
            SubmitMultiResponse response = new SubmitMultiResponse(
                    Optional.of(new SubmitMultiResponse.Result("id", List.of())),
                    new OptionalParameters(List.of(new Tlv(tag, value))));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CommonResponseRules.validate(
                            new Pdu<>(0, 1, multi(1, 0, 0, 0, 0, EMPTY)),
                            new Pdu<>(0, 1, response),
                            ProtocolProfile.forVersion(SmppVersion.V5_0)));
            CommonResponseRules.validate(
                    new Pdu<>(0, 1, multi(1, 0, 2, 0, 0, EMPTY)),
                    new Pdu<>(0, 1, response),
                    ProtocolProfile.forVersion(SmppVersion.V5_0));
            CommonResponseRules.validate(
                    new Pdu<>(0, 1, multi(1, 0, 0, 0, 0, EMPTY)),
                    new Pdu<>(0, 1, response),
                    ProtocolProfile.forVersion(SmppVersion.V3_4));
        }
        SubmitMultiResponse congestion = new SubmitMultiResponse(
                Optional.of(new SubmitMultiResponse.Result("id", List.of())),
                new OptionalParameters(List.of(new Tlv(0x0428, new byte[] {100}))));
        CommonResponseRules.validate(
                new Pdu<>(0, 1, multi(1, 0, 0, 0, 0, EMPTY)),
                new Pdu<>(0, 1, congestion),
                ProtocolProfile.forVersion(SmppVersion.V5_0));
    }
}
