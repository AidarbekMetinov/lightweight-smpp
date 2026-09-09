package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MessageTrafficTest {
    private static final String REVISION = "--revision=e79b9f48d7f2c94aad96b2bb734abe338d420a15";

    @Test
    void placesPayloadExactlyOnceAtTheShortFieldBoundaryAndForData() {
        var config = SimulatorArguments.parse("client", REVISION);
        for (int bytes : new int[] {0, 160, 254, 255, 4096, 65535}) {
            var original = new RawContent(bytes, 1).next(1);
            var message = MessageTraffic.message(config, original);
            assertNotNull(message);
            assertEquals(bytes <= 254 ? bytes : 0, message.shortMessage().length());
            assertEquals(
                    bytes <= 254 ? 0 : 1, message.optionalParameters().entries().size());
            assertEquals(original, MessageTraffic.content(message));
            var data = MessageTraffic.data(config, original);
            assertEquals(1, data.optionalParameters().entries().size());
            assertEquals(0x0424, data.optionalParameters().entries().getFirst().tag());
            assertEquals(original, MessageTraffic.content(data));
        }
    }

    @Test
    void rejectsImpossibleRolesBeforeNetworkingAndPreservesVersionSpecificDataRules() {
        assertTrue(MessageTraffic.find("submit", SimulatorArguments.parse("client", REVISION))
                .isPresent());
        assertTrue(MessageTraffic.find("deliver", SimulatorArguments.parse("server", REVISION))
                .isPresent());
        assertTrue(MessageTraffic.find("data", SimulatorArguments.parse("client", REVISION, "--bind=rx"))
                .isPresent());
        assertThrows(
                IllegalArgumentException.class,
                () -> MessageTraffic.find(
                        "data", SimulatorArguments.parse("client", REVISION, "--bind=rx", "--version=5.0")));
        assertThrows(
                IllegalArgumentException.class,
                () -> MessageTraffic.find("submit", SimulatorArguments.parse("server", REVISION)));
        assertThrows(
                IllegalArgumentException.class,
                () -> MessageTraffic.find("deliver", SimulatorArguments.parse("client", REVISION)));
        assertTrue(MessageTraffic.find("unrecognized", SimulatorArguments.parse("client", REVISION))
                .isEmpty());
    }
}
