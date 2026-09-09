package kg.aidarbek.smpp.endpoint;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Independent bounded socket fixture using raw headers, without library framing or command codecs. */
final class RawPeer implements AutoCloseable {
    private final Socket socket;

    private RawPeer(Socket socket) throws SocketException {
        this.socket = socket;
        socket.setSoTimeout(2000);
        socket.setTcpNoDelay(true);
    }

    static RawPeer accept(ServerSocket listener) throws IOException {
        listener.setSoTimeout(2000);
        return new RawPeer(listener.accept());
    }

    static RawPeer connect(InetSocketAddress address) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(address, 2000);
            return new RawPeer(socket);
        } catch (IOException failure) {
            socket.close();
            throw failure;
        }
    }

    void send(byte[] frame) throws IOException {
        socket.getOutputStream().write(frame);
    }

    byte[] read() throws IOException {
        byte[] header = socket.getInputStream().readNBytes(16);
        if (header.length != 16) throw new EOFException("Peer closed before a complete header");
        int length = ByteBuffer.wrap(header).getInt();
        if (length < 16 || length > 1048576) throw new IOException("Fixture frame bound exceeded");
        byte[] frame = Arrays.copyOf(header, length);
        byte[] body = socket.getInputStream().readNBytes(length - 16);
        if (body.length != length - 16) throw new EOFException("Peer closed within a frame");
        System.arraycopy(body, 0, frame, 16, body.length);
        return frame;
    }

    boolean closedByEndpoint() throws IOException {
        try {
            return socket.getInputStream().read() < 0;
        } catch (SocketException closed) {
            return true;
        }
    }

    void timeout(int milliseconds) throws SocketException {
        socket.setSoTimeout(milliseconds);
    }

    static byte[] header(long command, long status, long sequence) {
        return ByteBuffer.allocate(16)
                .putInt(16)
                .putInt((int) command)
                .putInt((int) status)
                .putInt((int) sequence)
                .array();
    }

    static long command(byte[] frame) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(frame).getInt(4));
    }

    static long sequence(byte[] frame) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(frame).getInt(12));
    }

    static long status(byte[] frame) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(frame).getInt(8));
    }

    static byte[] bind(long command, long sequence, int version) {
        return ByteBuffer.allocate(23)
                .putInt(23)
                .putInt((int) command)
                .putInt(0)
                .putInt((int) sequence)
                .put(new byte[] {0, 0, 0, (byte) version, 0, 0, 0})
                .array();
    }

    static byte[] bindResponse(long command, long status, long sequence, Integer advertisement) {
        if (status != 0) return header(command, status, sequence);
        ByteBuffer frame = ByteBuffer.allocate(advertisement == null ? 17 : 22);
        frame.putInt(frame.capacity())
                .putInt((int) command)
                .putInt(0)
                .putInt((int) sequence)
                .put((byte) 0);
        if (advertisement != null)
            frame.putShort((short) 0x0210).putShort((short) 1).put((byte) (int) advertisement);
        return frame.array();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
