package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoRepeat {
    private static final Logger logger = Logger.getLogger(ServerEchoRepeat.class.getName());
    private final int BUFFER_SIZE = 1024;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private final DatagramChannel dc;
    private final Selector selector;
    private SocketAddress sender;
    private final int port;
    private int nbRepeatLeft;

    public ServerEchoRepeat(int port) throws IOException {
        this.port = port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
    }

    public void serve() throws IOException {
        logger.info("ServerEchoRepeat started on port " + port);
        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isWritable()) {
                doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                doRead(key);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private void doRead(SelectionKey key) throws IOException {
        buffer.clear();
        sender = dc.receive(buffer);
        if (sender == null) {
            logger.warning("No packet received (No SocketAddress).");
            return;
        }

        buffer.flip();
        if (buffer.remaining() < Integer.BYTES) {
            logger.warning("Invalid packet format. Dropping...");
            return;
        }
        nbRepeatLeft = buffer.getInt();
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {
        var sent = dc.send(buffer, sender);
        if (sent == 0) {
            logger.warning("Could not send packet to " + sender);
            return;
        }

        buffer.flip();
        if (--nbRepeatLeft < 1) {
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerEchoRepeat port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerEchoRepeat(Integer.parseInt(args[0])).serve();
    }
}
