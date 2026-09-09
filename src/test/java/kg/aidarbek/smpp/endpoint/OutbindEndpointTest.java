package kg.aidarbek.smpp.endpoint;

import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.Outbind;
import org.junit.jupiter.api.Test;

class OutbindEndpointTest {
    @Test
    void explicitOutbindAuthenticatesBothRolesAndBindsOnceOnTheSameSocket() throws Exception {
        for (SmppVersion version : SmppVersion.values()) {
            for (BindMode mode : BindMode.values()) {
                if (version == SmppVersion.V3_4 && mode != BindMode.RECEIVER) continue;
                BindRequest bind = new BindRequest(mode, "esme", "esme-pw", "", version.interfaceVersion(), 0, 0, "");
                Outbind notification = new Outbind("mc", "mc-pass", EMPTY);
                CompletableFuture<BoundSession> esme = new CompletableFuture<>();
                AtomicInteger mcAuth = new AtomicInteger(), esmeAuth = new AtomicInteger();
                try (OutbindListener listener = new OutbindListener(
                                new OutbindListenerConfig(new InetSocketAddress("127.0.0.1", 0), bind, true),
                                EndpointOptions.defaults(),
                                (request, peer) -> {
                                    assertEquals(notification, request);
                                    assertTrue(Thread.currentThread().getName().startsWith("smpp-handler-"));
                                    mcAuth.incrementAndGet();
                                    return CompletableFuture.completedFuture(true);
                                },
                                esme::complete,
                                ExchangeConfig.defaults());
                        OutbindConnector connector = new OutbindConnector(
                                new OutbindConnectorConfig(Set.of(version), version, "mc", 1, 1),
                                EndpointOptions.defaults(),
                                (request, peer) -> {
                                    assertEquals(bind, request);
                                    esmeAuth.incrementAndGet();
                                    return CompletableFuture.completedFuture(BindDecision.ACCEPT);
                                },
                                ExchangeConfig.defaults())) {
                    InetSocketAddress address =
                            listener.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
                    BoundSession mc = connector
                            .connectAttempt(address, notification)
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS);
                    BoundSession receiver = esme.get(2, TimeUnit.SECONDS);
                    assertEquals(mode, mc.bindMode());
                    assertEquals(mode, receiver.bindMode());
                    assertEquals(address, mc.peer());
                    assertEquals(
                            version,
                            receiver.negotiation()
                                    .effectiveProfile()
                                    .orElseThrow()
                                    .version());
                    assertEquals(1, mcAuth.get());
                    assertEquals(1, esmeAuth.get());
                    assertEquals(
                            0,
                            receiver.enquireLink()
                                    .result()
                                    .toCompletableFuture()
                                    .get(2, TimeUnit.SECONDS)
                                    .commandStatus());
                }
            }
        }
    }
}
