package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientUpperCaseUDPFile {
  private final static Charset UTF8 = StandardCharsets.UTF_8;
  private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPFile.class.getName());
  private final static int BUFFER_SIZE = 1024;

  private static void usage() {
    System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 5) {
      usage();
      return;
    }

    var inFilename = args[0];
    var outFilename = args[1];
    var timeout = Integer.parseInt(args[2]);
    var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));

    // Read all lines of inFilename opened in UTF-8
    var lines = Files.readAllLines(Path.of(inFilename), UTF8);
    var upperCaseLines = new ArrayList<String>();
    var blockingQueue = new ArrayBlockingQueue<String>(10);

    try (var dc = DatagramChannel.open()) {
      dc.bind(null);

      Thread.ofPlatform().start(() -> {
        var receiverBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        for (;;) {
          try {
            receiverBuffer.clear();
            var dst = (InetSocketAddress) dc.receive(receiverBuffer);

            receiverBuffer.flip();
            System.out.println("Received " + receiverBuffer.remaining() + " bytes from " + dst);
            blockingQueue.put(UTF8.decode(receiverBuffer).toString());
          } catch (InterruptedException e) {
            logger.log(Level.INFO, "Listener throws interrupted on put, closing...");

            // The sender will interrupt, note that "return" can be ignored if its outside of the while
            return;
          } catch (ClosedByInterruptException e) {
            logger.info("Listener thread interrupted while waiting in receive, closing receiver...");
            return;
          } catch (AsynchronousCloseException e ) {
            logger.info("Datagram closed on receive, closing receiver...");
            return;
          } catch (IOException e) {
            logger.severe("IOException occured while waiting on receive.");
            throw new AssertionError(e);
          }
        }
      });

      for (var line : lines) {
        try {
          var encodedLine = UTF8.encode(line);

          dc.send(encodedLine, server);
          String response;
          while ((response = blockingQueue.poll(timeout, TimeUnit.MILLISECONDS)) == null) {
            System.out.println("Nothing received, sending again: " + line);
            dc.send(encodedLine, server);
          }

          System.out.println("String: " + response);
          upperCaseLines.add(response);
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
        /*
        * We do not catch any other exception as we cant really do anything smart in order to correct them
        * we only specify it in the main method signature.
        */
      }
    }

    // Write upperCaseLines to outFilename in UTF-8
    Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
  }
}