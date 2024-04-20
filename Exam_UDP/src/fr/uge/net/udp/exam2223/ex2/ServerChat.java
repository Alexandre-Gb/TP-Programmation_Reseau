package fr.uge.net.udp.exam2223.ex2;


import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class ServerChat {
  private static final int BUFFER_SIZE = 2048;
  private static final Logger logger = Logger.getLogger(ServerChat.class.getName());
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private final SessionHolder sessionHolder = new SessionHolder();
  private final DatagramChannel datagramChannel;
  private final int port;

  public ServerChat(int port) throws IOException {
    this.datagramChannel = DatagramChannel.open();
    this.port = port;
  }

  private static class SessionHolder {
    private final Map<String, InetSocketAddress> users = new HashMap<>();

    Optional<InetSocketAddress> getAddr(String user) {
      return Optional.ofNullable(users.get(user));
    }

    boolean userExists(String user) {
      return users.containsKey(user);
    }

//    boolean socketExists(InetSocketAddress dst) {
//      return users.containsValue(dst);
//    }

    void add(String user, InetSocketAddress dst) {
      if (!userExists(user)) {
        users.put(user, dst);
      }
    }

    boolean isSocketValid(String user, InetSocketAddress dst) {
      return userExists(user) && users.get(user).equals(dst);
    }
  }

  private String getStrFromBuffer(ByteBuffer buffer, int dst) {
    var tmpLimit = buffer.limit();
    buffer.limit(buffer.position() + dst);
    var str = UTF8.decode(buffer).toString();
    buffer.limit(tmpLimit);
    return str;
  }

  public void serve() throws IOException {
    datagramChannel.bind(new InetSocketAddress(port));
    System.out.println("ServerChat started on port " + port);
    try {
      var buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
      while (!Thread.interrupted()) {
        buffer.clear();
        var dst = (InetSocketAddress) datagramChannel.receive(buffer);
        buffer.flip();
        logger.info("Received " + buffer.remaining() + " bytes from " + dst);

        if (buffer.remaining() < Integer.BYTES) {
          logger.warning("Invalid packet format for sender name len, dropping...");
          continue;
        }
        var senderSize = buffer.getInt();
        if (buffer.remaining() < senderSize) {
          logger.warning("Invalid packet format for sender name, dropping...");
          continue;
        }
        var sender = getStrFromBuffer(buffer, senderSize);

        if (buffer.remaining() < Integer.BYTES) {
          logger.warning("Invalid packet format for recipient name len, dropping...");
          continue;
        }
        var recipientSize = buffer.getInt();
        if (buffer.remaining() < recipientSize) {
          logger.warning("Invalid packet format for recipient name, dropping...");
          continue;
        }
        var recipient = getStrFromBuffer(buffer, recipientSize);

        if (buffer.remaining() < Integer.BYTES) {
          logger.warning("Invalid packet format for message len, dropping...");
          continue;
        }
        var msgSize = buffer.getInt();
        if (buffer.remaining() < msgSize) {
          logger.warning("Invalid packet format for message, dropping...");
          continue;
        }
        var message = getStrFromBuffer(buffer, msgSize);
        logger.info("Packet from " + sender + " to " + recipient + " :\n" + message);

        if (!sessionHolder.userExists(sender)) {
          sessionHolder.add(sender, dst);
          logger.info("Adding sender " + sender + " to sessionHolder.");
        } else {
          if (!sessionHolder.isSocketValid(sender, dst)) {
            logger.warning("Socket invalid for user " + sender + ", dropping...");
          }
        }

        var recipientAddress = sessionHolder.getAddr(recipient);
        if (recipientAddress.isEmpty()) {
          logger.warning("User " + recipient + " could not be found, dropping...");
          continue;
        }

        logger.info("Forwarding message to " + recipient);
        buffer.clear();
        buffer.putInt(senderSize)
                .put(UTF8.encode(sender))
                .putInt(msgSize)
                .put(UTF8.encode(message));

        buffer.flip();
        datagramChannel.send(buffer, recipientAddress.get());
      }
    } finally {
      datagramChannel.close();
    }
  }

  public static void usage() {
    System.out.println("Usage : ServerChat port");
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      usage();
      return;
    }
    int port = Integer.valueOf(args[0]);
    if (!(port >= 1024) & port <= 65535) {
      System.out.println("The port number must be between 1024 and 65535");
      return;
    }

    var server=new ServerChat(port);
    try {
      server.serve();
    } catch (BindException e) {
      System.err.println("Server could not bind on " + port + "\nAnother server is probably running on this port.");
    }
  }
}