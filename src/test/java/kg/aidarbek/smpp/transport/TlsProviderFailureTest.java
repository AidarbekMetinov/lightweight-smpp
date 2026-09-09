package kg.aidarbek.smpp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import kg.aidarbek.smpp.spi.TransportFailure;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TlsProviderFailureTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void providerSetupFailuresKeepTlsClassificationAndReleaseTheConnectedSocket(boolean runtime) throws Exception {
        Exception cause = runtime
                ? new IllegalArgumentException("fixture unsupported TLS setup")
                : new IOException("fixture TLS layering failure");
        SSLContext context = new FailingContext(TlsTestMaterial.context(false, true), cause);
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                TcpTransport transport = TcpTransport.connect(
                        (InetSocketAddress) listener.getLocalSocketAddress(),
                        TcpTransportConfig.defaults(),
                        TcpTransportTest.deadline(),
                        TlsConfig.client(context, "localhost", Duration.ofSeconds(1)))) {
            listener.setSoTimeout(2000);
            TcpTransportTest.Events events = new TcpTransportTest.Events();
            transport.start(events);
            try (Socket peer = listener.accept()) {
                peer.setSoTimeout(2000);
                TransportFailure failure = events.closed.get(2, TimeUnit.SECONDS);
                assertEquals(TransportFailure.Kind.TLS_HANDSHAKE_FAILED, failure.kind());
                assertSame(cause, failure.getCause());
                assertFalse(events.connected.isDone());
                assertEquals(-1, peer.getInputStream().read());
                transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            }
        }
    }

    /** A real initialized JSSE context with only layered-socket provider setup faulted. */
    static final class FailingContext extends SSLContext {
        FailingContext(SSLContext delegate, Exception cause) {
            super(new FailingSpi(delegate, cause), delegate.getProvider(), delegate.getProtocol());
        }
    }

    static final class FailingSpi extends SSLContextSpi {
        private final SSLContext delegate;
        private final SSLSocketFactory factory;

        FailingSpi(SSLContext delegate, Exception cause) {
            this.delegate = delegate;
            factory = new FailingFactory(delegate.getSocketFactory(), cause);
        }

        @Override
        protected void engineInit(KeyManager[] keys, TrustManager[] trust, SecureRandom random)
                throws KeyManagementException {
            delegate.init(keys, trust, random);
        }

        @Override
        protected SSLSocketFactory engineGetSocketFactory() {
            return factory;
        }

        @Override
        protected SSLServerSocketFactory engineGetServerSocketFactory() {
            return delegate.getServerSocketFactory();
        }

        @Override
        protected SSLEngine engineCreateSSLEngine() {
            return delegate.createSSLEngine();
        }

        @Override
        protected SSLEngine engineCreateSSLEngine(String host, int port) {
            return delegate.createSSLEngine(host, port);
        }

        @Override
        protected SSLSessionContext engineGetServerSessionContext() {
            return delegate.getServerSessionContext();
        }

        @Override
        protected SSLSessionContext engineGetClientSessionContext() {
            return delegate.getClientSessionContext();
        }
    }

    static final class FailingFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final Exception cause;

        FailingFactory(SSLSocketFactory delegate, Exception cause) {
            this.delegate = delegate;
            this.cause = cause;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            if (cause instanceof IOException io) throw io;
            throw (RuntimeException) cause;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return delegate.createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
            return delegate.createSocket(host, port, local, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return delegate.createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
            return delegate.createSocket(host, port, local, localPort);
        }
    }
}
