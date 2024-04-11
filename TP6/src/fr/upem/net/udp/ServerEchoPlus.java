package fr.upem.net.udp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoPlus {
    private static final Logger logger = Logger.getLogger(ServerEchoPlus.class.getName());

    private final DatagramChannel dc;
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private final ByteBuffer bufferIn = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress sender;
    private int port;

    public ServerEchoPlus(int port) throws IOException {
        this.port = port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
    }

    public void serve() throws IOException {
        logger.info("ServerEcho started on port " + port);
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
        bufferIn.clear();
        sender = dc.receive(bufferIn);
        if (sender == null) {
            logger.warning("No packet received (No SocketAddress).");
            return;
        }
        logger.info("Received packet from " + sender);
        key.interestOps(SelectionKey.OP_WRITE);

        bufferIn.flip();

        bufferOut.clear();
        while (bufferIn.hasRemaining()) {
            bufferOut.put((byte) (bufferIn.get() + 1));
        }
        bufferOut.flip();
    }

    private void doWrite(SelectionKey key) throws IOException {
        var sent = dc.send(bufferOut, sender);
        if (sent == 0) {
            logger.warning("Could not send packet to " + sender);
            return;
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    public static void usage() {
        System.out.println("Usage : ServerEcho port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerEcho(Integer.parseInt(args[0])).serve();
    }
}

