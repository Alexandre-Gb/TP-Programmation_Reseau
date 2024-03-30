package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientUpperCaseUDPRetry {
    public static final int BUFFER_SIZE = 1024;
    private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPRetry.class.getName());

    private static void usage() {
        System.out.println("Usage : NetcatUDP host port charset");
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            usage();
            return;
        }

        var blockingQueue = new ArrayBlockingQueue<String>(10);
        var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        var cs = Charset.forName(args[2]);

        try (var scanner = new Scanner(System.in)) {
            try (var dc = DatagramChannel.open()) {
                dc.bind(null);

                Thread.ofPlatform().start(() -> {
                    var receiverBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
                    for (;;) {
                        try {
                            receiverBuffer.clear(); // Always clear the buffer before receiving
                            var dst = (InetSocketAddress) dc.receive(receiverBuffer);
                            receiverBuffer.flip();

                            System.out.println("Received " + receiverBuffer.remaining() + " bytes from " + dst);
                            blockingQueue.put(cs.decode(receiverBuffer).toString());
                        } catch (InterruptedException e) {
                            throw new AssertionError(e); // We did not interrupt manually thus far, so we dont simply "return" as it is not planned
                        } catch (IOException e) {
                            logger.log(Level.WARNING, "///", e);
                            throw new AssertionError(e);
                        }
                    }
                });

                while (scanner.hasNextLine()) {
                    var line = scanner.nextLine();
                    dc.send(cs.encode(line), server);

                    String response;
                    while ((response = blockingQueue.poll(1000L, TimeUnit.MILLISECONDS)) == null) {
                        System.out.println("Nothing received, sending again: " + line);
                        dc.send(cs.encode(line), server);
                    }

                    System.out.println("String: " + response);
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            } catch (IOException e) {
                logger.log(Level.WARNING, "\\\\\\", e);
                throw new AssertionError(e);
            }
        }
    }
}
