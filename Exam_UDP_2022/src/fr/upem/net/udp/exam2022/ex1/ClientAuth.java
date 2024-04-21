package fr.upem.net.udp.exam2022.ex1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;

public class ClientAuth {
  private static final int BUFFER_SIZE = 1024;
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;
  private static final Logger logger = Logger.getLogger(ClientAuth.class.getName());

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

  private final String inFilename;
  private final String outFilename;
  private final InetSocketAddress server;
  private final DatagramChannel dc;

  public static void usage() {
    System.out.println("Usage : ClientAuth in-filename out-filename host port ");
  }

  public ClientAuth(String inFilename, String outFilename, InetSocketAddress server) throws IOException {
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

  public void launch() throws IOException, InterruptedException {
    try {
      var lines = Files.readAllLines(Path.of(inFilename), UTF8);
      var users = lines.stream().map(User::fromLine).collect(Collectors.toList());
      var answers = new ArrayList<String>();
      var buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
      var id = 0L;

      for (var user : users) {
        // Send
        buffer.clear();
        buffer.putLong(id);
        var fname = UTF8.encode(user.firstName);
        var lname = UTF8.encode(user.lastName);
        buffer.putInt(fname.remaining());
        buffer.put(fname);
        buffer.putInt(lname.remaining());
        buffer.put(lname);

        buffer.flip();
        logger.info("Sending request to " + server + " for user: " + user.firstName() + " " + user.lastName());
        dc.send(buffer, server);

        // Receive
        buffer.clear();
        var dst = (InetSocketAddress) dc.receive(buffer);
//        if (!dst.equals(server)) {
//          logger.warning("Response from incorrect socket.");
//          continue;
//        }

        buffer.flip();
        logger.info("Received " + buffer.remaining() + " bytes from " + dst);

        if (buffer.remaining() < (Long.BYTES + Integer.BYTES)) {
          logger.warning("Invalid format, dropping...");
          continue;
        }
        var receiveId = buffer.getLong();
        if (receiveId != id) {
          logger.warning("Invalid exchange id, dropping...");
          continue;
        }

        var usernameSize = buffer.getInt();
        if (buffer.remaining() < usernameSize) {
          logger.warning("Invalid format for username, dropping...");
          continue;
        }
        var username = strLatinFromBuf(buffer, usernameSize);

        if (buffer.remaining() < Integer.BYTES) {
          logger.warning("Invalid format for password length, dropping...");
          continue;
        }
        var passwdSize = buffer.getInt();
        if (buffer.remaining() < passwdSize) {
          logger.warning("Invalid format for password, dropping...");
          continue;
        }
        logger.info("Received username and password for " + user.firstName() + " " + user.lastName() + ".\nAdding to file...");
        answers.add(user.firstName() + ";" + user.lastName() + ";" + username + ";" + strLatinFromBuf(buffer, passwdSize));
        id++;
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
    new ClientAuth(inFilename, outFilename, server).launch();
  }
}