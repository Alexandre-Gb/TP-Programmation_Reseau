package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
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

  public static void main(String[] args) throws IOException, InterruptedException {
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

      var receiver = Thread.ofPlatform().start(() -> {
        var receiverBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        for (;;) {
          try {
            receiverBuffer.clear();
            var dst = (InetSocketAddress) dc.receive(receiverBuffer);

            receiverBuffer.flip();
            System.out.println("Received " + receiverBuffer.remaining() + " bytes from " + dst);
            blockingQueue.put(UTF8.decode(receiverBuffer).toString());
          } catch (IOException e ) {
            logger.log(Level.WARNING, "IOException occured", e);
            throw new AssertionError(e);
          } catch (InterruptedException e) {
            return; // The sender will interrupt
          }
        }
      });

      for (var line : lines) {
        dc.send(UTF8.encode(line), server);

        String response;
        while ((response = blockingQueue.poll(timeout, TimeUnit.MILLISECONDS)) == null) {
          System.out.println("Nothing received, sending again: " + line);
          dc.send(UTF8.encode(line), server);
        }

        System.out.println("String: " + response);
        upperCaseLines.add(response);
      }

      receiver.interrupt();
    }

    // Write upperCaseLines to outFilename in UTF-8
    Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
  }
}