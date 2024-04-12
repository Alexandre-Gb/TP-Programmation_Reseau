package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoMultiport {
    private static final Logger logger = Logger.getLogger(ServerEchoMultiport.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private final Selector selector;

    public ServerEchoMultiport(int portBegin, int portEnd) throws IOException {
        if ((portBegin < 1024 || portBegin > 65535) && (portEnd < 1024 || portEnd > 65535)) {
            throw new IllegalArgumentException("Port out of range [1024, 65535]");
        }

        if (portBegin > portEnd) { throw new IllegalArgumentException("Port range is invalid."); }

        selector = Selector.open();
        for (int i = portBegin; i <= portEnd; i++) {
            try {
                registerPort(i);
            } catch (AlreadyBoundException abe) {
                logger.severe("Port " + i + " unavailable.");
            }
        }
    }

    private static class Context {
        private final ByteBuffer bb = ByteBuffer.allocateDirect(BUFFER_SIZE);
        private SocketAddress address;
    }

    private void registerPort(int port) throws IOException, AlreadyBoundException {
        var dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ, new Context());
    }

    public void serve() throws IOException {
        logger.info("ServerEchoMultiport started.");

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
        var dc = (DatagramChannel) key.channel();
        var context = (Context) key.attachment();
        context.bb.clear();

        context.address = dc.receive(context.bb);
        if (context.address == null) {
            logger.warning("No packet received (No SocketAddress).");
            return;
        }

        context.bb.flip();
        logger.info("Received packet from " + context.address);
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {
        var dc = (DatagramChannel) key.channel();
        var context = (Context) key.attachment();

        dc.send(context.bb, context.address);
        if (context.bb.hasRemaining()) {
            logger.warning("Could not send packet to " + context.address);
            return;
        }

        key.interestOps(SelectionKey.OP_READ);
    }

    public static void usage() {
        System.out.println("Usage : ServerEchoRepeat port_range_begin port_range_end");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }

        new ServerEchoMultiport(Integer.parseInt(args[0]), Integer.parseInt(args[1])).serve();
    }
}
