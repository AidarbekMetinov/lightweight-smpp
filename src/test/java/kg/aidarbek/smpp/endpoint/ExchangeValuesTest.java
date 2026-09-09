package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import org.junit.jupiter.api.Test;

class ExchangeValuesTest {
    @Test
    void acknowledgementValuesAndRegistrationsCannotEraseTheirBoundedContracts() {
        SubmitSmResponse response =
                new SubmitSmResponse(new MessageResponse(Optional.of("accepted"), new OptionalParameters(List.of())));
        assertEquals(0xffff_ffffL, new HandlerResponse<>(0xffff_ffffL, response).commandStatus());
        assertThrows(IllegalArgumentException.class, () -> new HandlerResponse<>(-1, response));
        assertThrows(IllegalArgumentException.class, () -> new ExchangeOptions(0, 1, 1, 16, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new ExchangeOptions(1, 1, 1, 16, Duration.ZERO));
        var builder = EndpointHandlers.builder()
                .on(
                        MessageOperations.SUBMIT_SM,
                        request -> CompletableFuture.completedFuture(new HandlerResponse<>(0, response)));
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.on(
                        MessageOperations.SUBMIT_SM,
                        request -> CompletableFuture.completedFuture(new HandlerResponse<>(0, response))));
    }
}
