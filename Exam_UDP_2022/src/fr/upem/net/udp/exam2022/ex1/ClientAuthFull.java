package fr.upem.net.udp.exam2022.ex1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;

public class ClientAuthFull {
  private static final int BUFFER_SIZE = 1024;
  private static final int TIMEOUT = 300;
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;
  private static final Logger logger = Logger.getLogger(ClientAuthFull.class.getName());
  private final SynchronousQueue<Response> synchronousQueue = new SynchronousQueue<>();

  private record User(String firstName, String lastName) {
    public User {
      Objects.requireNonNull(firstName);
      Objects.requireNonNull(lastName);
    }

    public static User fromLine(String line) {
      var t = line.split(";");
      if (t.length != 2) {
        throw new IllegalArgumentException("Invalid line : " + line);
      }
      return new User(t[0], t[1]);
    }
  }

  private record Response(long id, String body) {
    public Response {
      if (id < 0) { throw new IllegalArgumentException(); }
      Objects.requireNonNull(body);
    }
  }

  private final String inFilename;
  private final String outFilename;
  private final InetSocketAddress server;
  private final DatagramChannel dc;

  public static void usage() {
    System.out.println("Usage : ClientAuth in-filename out-filename host port ");
  }

  public ClientAuthFull(String inFilename, String outFilename, InetSocketAddress server) throws IOException {
    this.inFilename = Objects.requireNonNull(inFilename);
    this.outFilename = Objects.requireNonNull(outFilename);
    this.server = server;
    this.dc = DatagramChannel.open();
    dc.bind(null);
  }

  private String strLatinFromBuf(ByteBuffer buffer, int nb) {
    var tmpLimit = buffer.limit();
    buffer.limit(buffer.position() + nb);
    var str = LATIN1.decode(buffer).toString();
    buffer.limit(tmpLimit);
    return str;
  }

  private void listenerThreadRun() {
    var bb = ByteBuffer.allocateDirect(BUFFER_SIZE);
    for (;;) {
      try {
        bb.clear();
        var dst = (InetSocketAddress) dc.receive(bb);

        bb.flip();
        logger.info("Received " + bb.remaining() + " bytes from " + dst);

        if (bb.remaining() < (Long.BYTES + Integer.BYTES)) {
          logger.warning("Invalid format, dropping...");
          continue;
        }
        var receiveId = bb.getLong();

        var usernameSize = bb.getInt();
        if (bb.remaining() < usernameSize) {
          logger.warning("Invalid format for username, dropping...");
          continue;
        }
        var username = strLatinFromBuf(bb, usernameSize);

        if (bb.remaining() < Integer.BYTES) {
          logger.warning("Invalid format for password length, dropping...");
          continue;
        }
        var passwdSize = bb.getInt();
        if (bb.remaining() < passwdSize) {
          logger.warning("Invalid format for password, dropping...");
          continue;
        }

        logger.info("Received response for username " + username + ". Adding to queue");
        synchronousQueue.put(new Response(receiveId, ";" + username + ";" + strLatinFromBuf(bb, passwdSize)));
      } catch (InterruptedException | AsynchronousCloseException e) {
        logger.info("DatagramChannel closed, exiting receiver...");
        return;
      } catch (IOException e) {
        logger.severe("IOException occured in receiver:\n" + e);
        return;
      }
    }
  }

  public void launch() throws IOException, InterruptedException {
    try {
      var lines = Files.readAllLines(Path.of(inFilename), UTF8);
      var users = lines.stream().map(User::fromLine).collect(Collectors.toList());
      var answers = new ArrayList<String>();
      var queue = new ArrayBlockingQueue<Response>(users.size());
      var exchangeId = 0L;

      Thread.ofPlatform().start(this::listenerThreadRun);

      var byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
      for (var user : users) {
        // Send
        byteBuffer.clear();
        byteBuffer.putLong(exchangeId);
        var fname = UTF8.encode(user.firstName);
        var lname = UTF8.encode(user.lastName);
        byteBuffer.putInt(fname.remaining());
        byteBuffer.put(fname);
        byteBuffer.putInt(lname.remaining());
        byteBuffer.put(lname);

        byteBuffer.flip();
        logger.info("Sending request to " + server + " for user: " + user.firstName() + " " + user.lastName());
        dc.send(byteBuffer, server);

        for (;;) {
          var response = synchronousQueue.poll(TIMEOUT, TimeUnit.MILLISECONDS);
          if (response == null) {
            logger.info("No answer retrieved for " + user.firstName() + " " + user.lastName() + ", sending again (id " + exchangeId + ")...");
            byteBuffer.flip();
            dc.send(byteBuffer, server);
            continue;
          }
          logger.info("Retrieved " + response.body() + " with id " + response.id());

          if (response.id() != exchangeId) {
            logger.info("Invalid id, dropping...");
            continue;
          }

          answers.add(user.firstName() + ";" + user.lastName() + response.body());
          exchangeId++;
          break;
        }
      }

      Files.write(Paths.get(outFilename), answers, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
    } finally {
      dc.close();
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length != 4) {
      usage();
      return;
    }

    var inFilename = args[0];
    var outFilename = args[1];
    var server = new InetSocketAddress(args[2], Integer.parseInt(args[3]));

    // Create client with the parameters and launch it
    new ClientAuthFull(inFilename, outFilename, server).launch();
  }
}