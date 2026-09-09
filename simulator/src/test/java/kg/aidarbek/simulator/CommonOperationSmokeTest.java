package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kg.aidarbek.smpp.profile.SmppVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class CommonOperationSmokeTest {
    @Test
    void finiteOneWayScenariosUseRealEndpointsAndReleaseAllOwnedWork() throws Exception {
        for (SmppVersion version : SmppVersion.values())
            for (String operation : new String[] {"alert", "outbind"}) {
                var result = CommonOperationSmoke.run(operation, version);
                assertEquals(1, result.notifications());
                assertEquals(operation.equals("outbind") ? 2 : 1, result.authentications());
                assertEquals(2, result.boundSessions());
                assertTrue(result.cleanup());
            }
    }

    @Test
    void unsupportedScenarioNeverStartsAnEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> CommonOperationSmoke.run("retry-loop", SmppVersion.V3_4));
    }
}
