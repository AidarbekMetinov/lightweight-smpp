package kg.aidarbek.smpp.request;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class RequestOptionsTest {
    @Test
    void rejectsTimeoutsOutsideThePositiveNanoTimeDifferenceRange() {
        assertThrows(IllegalArgumentException.class, () -> RequestOptions.timeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> RequestOptions.timeout(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> RequestOptions.timeout(Duration.ofSeconds(Long.MAX_VALUE)));
        assertThrows(NullPointerException.class, () -> RequestOptions.timeout(null));
    }
}
