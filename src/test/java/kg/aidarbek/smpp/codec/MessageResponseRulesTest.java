package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.MessageOptionalParametersTest.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class MessageResponseRulesTest {
    @Test
    void transactionDiagnosticsRequireOriginalRequestModeBut34AdditionalTextDoesNot() {
        for (SmppVersion version : SmppVersion.values()) {
            for (Tlv parameter :
                    List.of(tlv(0x0425, "00"), tlv(0x0423, "030001"), tlv(0x0420, "01"), tlv(0x001d, "4100"))) {
                DataSmResponse response =
                        new DataSmResponse(new MessageResponse(Optional.of("id"), parameters(parameter)));
                if (version == SmppVersion.V3_4 && parameter.tag() == 0x001d)
                    assertDoesNotThrow(() -> validate(data(0), response, version));
                else assertThrows(IllegalArgumentException.class, () -> validate(data(0), response, version));
                assertDoesNotThrow(() -> validate(data(2), response, version));
            }
            DataSmResponse reserved =
                    new DataSmResponse(new MessageResponse(Optional.of("id"), parameters(tlv(0x0425, "ff"))));
            assertDoesNotThrow(() -> validate(data(0), reserved, version));
        }
    }

    @Test
    void definiteDeliverySuccessRejectsFailureDiagnosticsAndMismatchedOperationsFail() {
        ProtocolProfile profile = ProtocolProfile.forVersion(SmppVersion.V3_4);
        for (Tlv parameter : List.of(tlv(0x0425, "01"), tlv(0x0423, "030001"), tlv(0x0420, "01"))) {
            Pdu<DataSmResponse> response =
                    new Pdu<>(0, 7, new DataSmResponse(new MessageResponse(Optional.of("id"), parameters(parameter))));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MessageResponseRules.validate(
                            new Pdu<>(0, 7, data(2)), response, profile, MessageDirection.SUBMISSION, true));
            assertDoesNotThrow(() -> MessageResponseRules.validate(
                    new Pdu<>(0, 7, data(2)), response, profile, MessageDirection.SUBMISSION, false));
        }
        SubmitSmResponse wrong = new SubmitSmResponse(new MessageResponse(Optional.empty(), parameters()));
        assertThrows(IllegalArgumentException.class, () -> validate(data(0), wrong, SmppVersion.V3_4));
    }

    @Test
    void data34NegativeBodyPreservesFailureDiagnosticsWhile50CanonicalErrorIsHeaderOnly() {
        DataSmResponse response = new DataSmResponse(
                new MessageResponse(Optional.of("id"), parameters(tlv(0x0425, "00"), tlv(0x0420, "01"))));
        PduCodec codec = MessageCodecValidationTest.codec();
        Pdu<DataSmResponse> negative = new Pdu<>(0xfe, 7, response);
        byte[] expected =
                java.util.HexFormat.of().parseHex("0000001d80000103000000fe0000000769640004250001000420000101");
        assertArrayEquals(expected, codec.encode(negative, ProtocolProfile.forVersion(SmppVersion.V3_4)));
        assertEquals(negative, codec.decode(expected, ProtocolProfile.forVersion(SmppVersion.V3_4)));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.encode(negative, ProtocolProfile.forVersion(SmppVersion.V5_0)));
        DataSmResponse ignored = (DataSmResponse) codec.decode(expected, ProtocolProfile.forVersion(SmppVersion.V5_0))
                .command();
        assertTrue(ignored.fields().messageId().isEmpty());
    }

    private static DataSm data(int esm) {
        return new DataSm("", new Address(0, 0, ""), new Address(0, 0, ""), esm, 0, 0, parameters());
    }

    private static void validate(Command request, Command response, SmppVersion version) {
        MessageResponseRules.validate(
                new Pdu<>(0, 7, request),
                new Pdu<>(0, 7, response),
                ProtocolProfile.forVersion(version),
                MessageDirection.SUBMISSION);
    }
}
