package fr.upem.net.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FixedPrestartedLongSumServer {

    private static final Logger logger = Logger.getLogger(FixedPrestartedLongSumServer.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private final ServerSocketChannel serverSocketChannel;
    private static final int NB_THREADS = 10;

    public FixedPrestartedLongSumServer(int port, int nbThreads) throws IOException {
        if (nbThreads < 1) { throw new IllegalArgumentException(); }
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        logger.info(this.getClass().getName() + " starts on port " + port);
    }

    /**
     * Iterative server main loop
     *
     * @throws IOException
     */
    public void launch() throws IOException, InterruptedException {
        logger.info("Server started");

        for (int i = 0; i < NB_THREADS; i++) {
            Thread.ofPlatform().start(() -> {
                for (;;) {
                    try {
                        SocketChannel client = serverSocketChannel.accept();
                        logger.info("Connection accepted from " + client.getRemoteAddress());
                        try {
                            serve(client);
                        } catch (IOException ioe) {
                            client.close();
                        }
                    } catch (IOException ioe) {
                        logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
                        silentlyClose(serverSocketChannel);
                        return;
                    }
                }
            });
        }
    }

    /**
     * Treat the connection sc applying the protocol. All IOException are thrown
     *
     * @param sc
     * @throws IOException
     */
    private void serve(SocketChannel sc) throws IOException {
        var receiveBuffer = ByteBuffer.allocate(Integer.BYTES);
        var sendBuffer = ByteBuffer.allocate(Long.BYTES);

        while (!Thread.interrupted()) {
            receiveBuffer.clear();
            sendBuffer.clear();
            // Receive
            if (!readFully(sc, receiveBuffer)) {
                logger.info("Connexion interrupted, stopping...");
                return;
            }

            receiveBuffer.flip();
            logger.info("Received " + receiveBuffer.remaining() + " bytes from " + sc.getRemoteAddress());

            var size = receiveBuffer.getInt();
            if (size < 0) {
                logger.info("Invalid size, dropping...");
                throw new IOException();
            }

            receiveBuffer = ByteBuffer.allocate(Long.BYTES * size);
            if (!readFully(sc, receiveBuffer)) {
                logger.info("Invalid format for longs, dropping...");
                throw new IOException();
            }

            receiveBuffer.flip();
            logger.info("Received " + receiveBuffer.remaining() / Long.BYTES + " longs from " + sc.getRemoteAddress());

            var sum = 0L;
            for (var i = 0; i < size; i++) {
                var value = receiveBuffer.getLong();
                logger.info("Long: " + value);
                sum += value;
            }

            // Send
            sendBuffer.putLong(sum);
            sendBuffer.flip();

            logger.info("Sending " + sum + " (sum) to " + sc.getRemoteAddress());
            sc.write(sendBuffer);
        }
    }

    /**
     * Close a SocketChannel while ignoring IOExecption
     *
     * @param sc
     */
    private void silentlyClose(Closeable sc) {
        if (sc != null) {
            try {
                sc.close();
            } catch (IOException e) {
                // Do nothing
            }
        }
    }

    static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (sc.read(buffer) == -1) {
                logger.info("Input stream closed");
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException {
        var server = new FixedPrestartedLongSumServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        server.launch();
    }
}