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

public class ServerEcho {
    private static final Logger logger = Logger.getLogger(ServerEcho.class.getName());

    private final DatagramChannel dc;
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress sender;
    private final int port;

    public ServerEcho(int port) throws IOException {
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
                // On récupère l'UncheckedIO de treatKey et on en revoi la cause
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
            // Etant donné que l'on passe par une lambda (treatkey depuis serve()), l'IOException ne va
            // pas se propager. On va donc renvoyer une UncheckedIO qui va bien se propager
            throw new UncheckedIOException(ioe);
        }
    }

    private void doRead(SelectionKey key) throws IOException {
        buffer.clear();
        sender = dc.receive(buffer);
        if (sender == null) {
            // Par convention on met en warning, mais on peut aussi mettre en info car la doc du Selector
            // prévoit que ça peut mal fonctionner
            logger.warning("No packet received (No SocketAddress).");
            return;
        }
        logger.info("Received packet from " + sender);
        key.interestOps(SelectionKey.OP_WRITE);

        // Attention, si l'envoi échoue dans le doWrite, on va re-flip, ce qui va poser un énorme problème évident
        // On flip donc dans le read car on n'y passe systématiquement qu'une fois
        buffer.flip();
    }

    private void doWrite(SelectionKey key) throws IOException {
        // buffer.flip(); NON!!!!!
        var sent = dc.send(buffer, sender);
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
