package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.BindResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

final class BindCommandCodecTest {
    private static final PduLimits LIMITS = new PduLimits(1024, 256, 8);
    private static final PduCodec CODEC = new PduCodec(ControlCommandCodecs.all(), LIMITS);

    @ParameterizedTest
    @CsvSource({
        "V3_4,RECEIVER,00000001,34",
        "V3_4,TRANSMITTER,00000002,34",
        "V3_4,TRANSCEIVER,00000009,34",
        "V5_0,RECEIVER,00000001,50",
        "V5_0,TRANSMITTER,00000002,50",
        "V5_0,TRANSCEIVER,00000009,50"
    })
    void bindRequestMatchesKnownWireBytes(SmppVersion version, BindMode mode, String id, String versionOctet) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        BindRequest request = new BindRequest(mode, "esme", "secret", "", version.interfaceVersion(), 1, 1, "123");
        byte[] fixture = HexFormat.of()
                .parseHex("00000024" + id + "0000000012345678" + "65736d65007365637265740000" + versionOctet
                        + "010131323300");
        Pdu<BindRequest> pdu = new Pdu<>(0, 0x12345678L, request);
        assertArrayEquals(fixture, CODEC.encode(pdu, profile));
        assertEquals(pdu, CODEC.decode(fixture, profile));
    }

    @ParameterizedTest
    @CsvSource({
        "V3_4,RECEIVER,80000001,34",
        "V3_4,TRANSMITTER,80000002,34",
        "V3_4,TRANSCEIVER,80000009,34",
        "V5_0,RECEIVER,80000001,50",
        "V5_0,TRANSMITTER,80000002,50",
        "V5_0,TRANSCEIVER,80000009,50"
    })
    void bindResponseMatchesKnownWireBytes(SmppVersion version, BindMode mode, String id, String versionOctet) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        BindResponse response =
                new BindResponse(mode, Optional.of("smsc"), new OptionalParameters(List.of(new Tlv(0x0210, new byte[] {
                    (byte) version.interfaceVersion()
                }))));
        byte[] fixture =
                HexFormat.of().parseHex("0000001a" + id + "0000000012345678" + "736d73630002100001" + versionOctet);
        Pdu<BindResponse> pdu = new Pdu<>(0, 0x12345678L, response);
        assertArrayEquals(fixture, CODEC.encode(pdu, profile));
        assertEquals(pdu, CODEC.decode(fixture, profile));
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void failedBindOmitsBodyAndPreservesUnknownStatus(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        for (BindMode mode : BindMode.values()) {
            BindResponse response = new BindResponse(mode, Optional.empty(), new OptionalParameters(List.of()));
            String id =
                    switch (mode) {
                        case RECEIVER -> "80000001";
                        case TRANSMITTER -> "80000002";
                        case TRANSCEIVER -> "80000009";
                    };
            byte[] fixture = HexFormat.of().parseHex("00000010" + id + "ffffffff00000001");
            Pdu<BindResponse> pdu = new Pdu<>(0xffffffffL, 1, response);
            assertEquals(pdu, CODEC.decode(fixture, profile));
            assertArrayEquals(fixture, CODEC.encode(pdu, profile));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CODEC.encode(
                            new Pdu<>(
                                    5,
                                    1,
                                    new BindResponse(mode, Optional.of("ignored"), new OptionalParameters(List.of()))),
                            profile));
        }
        byte[] forbiddenBody = HexFormat.of().parseHex("00000013800000090000000500000001ff1234");
        if (version == SmppVersion.V5_0) {
            assertEquals(
                    new Pdu<>(
                            5,
                            1,
                            new BindResponse(
                                    BindMode.TRANSCEIVER, Optional.empty(), new OptionalParameters(List.of()))),
                    CODEC.decode(forbiddenBody, profile));
        } else {
            assertThrows(IllegalArgumentException.class, () -> CODEC.decode(forbiddenBody, profile));
        }
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void validatesKnownBindTlvsAndPreservesUnexpectedEntries(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(bindResponseFrame("00021000023450"), profile));
        assertThrows(
                IllegalArgumentException.class,
                () -> CODEC.decode(bindResponseFrame("0002100001340210000150"), profile));
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(bindResponseFrame("002fff0002ff"), profile));
        BindResponse response = (BindResponse) CODEC.decode(bindResponseFrame("002fff0001012fff000102"), profile)
                .command();
        assertEquals(
                List.of(new Tlv(0x2fff, new byte[] {1}), new Tlv(0x2fff, new byte[] {2})),
                response.optionalParameters().entries());
        assertThrows(IllegalArgumentException.class, () -> CODEC.encode(new Pdu<>(0, 1, response), profile));
        if (version == SmppVersion.V5_0) {
            assertThrows(
                    IllegalArgumentException.class, () -> CODEC.decode(bindResponseFrame("00042800025000"), profile));
        } else {
            BindResponse uninterpreted = (BindResponse)
                    CODEC.decode(bindResponseFrame("00042800025000"), profile).command();
            assertEquals(
                    2, uninterpreted.optionalParameters().entries().getFirst().valueLength());
        }
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void requestedAndAdvertisedVersionsRemainRawAndMissingAdvertisementStaysAbsent(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        for (int rawVersion : new int[] {0x34, 0x50, 0xfe}) {
            BindRequest request = new BindRequest(BindMode.RECEIVER, "", "", "", rawVersion, 0, 0, "");
            assertEquals(
                    request,
                    CODEC.decode(CODEC.encode(new Pdu<>(0, 1, request), profile), profile)
                            .command());
            BindResponse response = (BindResponse) CODEC.decode(
                            bindResponseFrame("0002100001" + HexFormat.of().toHexDigits((byte) rawVersion)), profile)
                    .command();
            assertEquals(
                    Optional.of(rawVersion),
                    TypedTlvRegistry.standard()
                            .decode(
                                    version,
                                    response.commandId(),
                                    response.optionalParameters().entries().getFirst(),
                                    Integer.class));
        }
        BindResponse missing =
                (BindResponse) CODEC.decode(bindResponseFrame("00"), profile).command();
        assertEquals(Optional.of(""), missing.systemId());
        assertEquals(List.of(), missing.optionalParameters().entries());
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(bindResponseFrame(""), profile));
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void incomingUnexpectedBindRequestTlvsAreRetainedWithoutGrantingOutgoingPermission(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        byte[] frame = HexFormat.of()
                .parseHex("00000022000000090000000000000001" + "000000" + "50000000" + "2fff0001012fff00020203");
        BindRequest request = new BindRequest(
                BindMode.TRANSCEIVER,
                "",
                "",
                "",
                0x50,
                0,
                0,
                "",
                new OptionalParameters(List.of(new Tlv(0x2fff, new byte[] {1}), new Tlv(0x2fff, new byte[] {2, 3}))));
        assertEquals(new Pdu<>(0, 1, request), CODEC.decode(frame, profile));
        assertThrows(IllegalArgumentException.class, () -> CODEC.encode(new Pdu<>(0, 1, request), profile));
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void reservedAddressOctetsAreRetainedOnInputAndRejectedOnOutput(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        byte[] reserved = HexFormat.of().parseHex("0000001700000009000000000000000100000050070200");
        BindRequest decoded = (BindRequest) CODEC.decode(reserved, profile).command();
        assertEquals(7, decoded.addrTon());
        assertEquals(2, decoded.addrNpi());
        assertThrows(IllegalArgumentException.class, () -> CODEC.encode(new Pdu<>(0, 1, decoded), profile));
        for (int ton : new int[] {7, 255}) {
            BindRequest request = new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x50, ton, 0, "");
            assertThrows(IllegalArgumentException.class, () -> CODEC.encode(new Pdu<>(0, 1, request), profile));
        }
        for (int npi : new int[] {2, 5, 7, 11, 255}) {
            BindRequest request = new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x50, 0, npi, "");
            assertThrows(IllegalArgumentException.class, () -> CODEC.encode(new Pdu<>(0, 1, request), profile));
        }
        for (int ton = 0; ton <= 6; ton++) {
            for (int npi : new int[] {0, 1, 3, 4, 6, 8, 9, 10, 14, 18}) {
                Pdu<BindRequest> pdu =
                        new Pdu<>(0, 1, new BindRequest(BindMode.RECEIVER, "", "", "", 0x50, ton, npi, ""));
                assertEquals(pdu, CODEC.decode(CODEC.encode(pdu, profile), profile));
            }
        }
    }

    private static byte[] bindResponseFrame(String bodyHex) {
        return HexFormat.of()
                .parseHex(HexFormat.of().toHexDigits(16 + bodyHex.length() / 2) + "800000090000000000000001" + bodyHex);
    }
}
