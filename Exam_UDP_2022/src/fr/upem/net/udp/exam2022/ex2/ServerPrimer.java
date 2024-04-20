package fr.upem.net.udp.exam2022.ex2;


import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.*;
import java.util.logging.Logger;

public class ServerPrimer {
  private static final int BUFSIZ_IN = Long.BYTES * 3;
  private final DatagramChannel dc;
  private final Logger logger = Logger.getLogger(ServerPrimer.class.getName());
  private final Map<InetSocketAddress, UserValues> values = new HashMap<>();
  private final Set<Long> discovered = new HashSet<>();

  public ServerPrimer(int port) throws IOException {
    dc = DatagramChannel.open();
    dc.bind(new InetSocketAddress(port));
    System.out.println("ServerPrimer started on port " + port);
  }

  private static class UserValues {
    private final Set<Long> values = new HashSet<>();
    private final Set<Long> discoveredValues = new HashSet<>();

    public void addValue(long value) {
      values.add(value);
    }

    public void addDiscoveredValue(long value) {
      discoveredValues.add(value);
    }

    public long avgDiscoveries() {
      return avg(discoveredValues);
    }
  }

  private static boolean isPrime(long n) {
    if (n <= 1)
      return false;
    for (int i = 2; i <= Math.sqrt(n); i++) {
      if (n % i == 0)
        return false;
    }
    return true;
  }

  private static long avg(Set<Long> values) {
    if (values.isEmpty()) {
      return 0;
    }

    return values.stream().mapToLong(Long::longValue).sum() / values.size();
  }

  public void serve() throws IOException {
    try {
      var buffer = ByteBuffer.allocateDirect(BUFSIZ_IN);
      while (!Thread.interrupted()) {
        buffer.clear();
        var dst = (InetSocketAddress) dc.receive(buffer);
        buffer.flip();
        logger.info("Received " + buffer + " bytes from " + dst);

        if (buffer.remaining() < Long.BYTES) {
          logger.warning("Invalid packet format, dropping...");
          continue;
        }
        var value = buffer.getLong();
        logger.info("Value (L): " + value);

        if (isPrime(value)) {
          logger.info("Value " + value + " sent by " + dst + " is prime.");
          values.computeIfAbsent(dst, k -> new UserValues()).addValue(value);

          if (!discovered.contains(value)) {
            logger.info("New prime value discovered!");
            discovered.add(value);
            values.computeIfAbsent(dst, k -> new UserValues()).addDiscoveredValue(value);
          }
        }

        buffer.clear();
        buffer.putLong(value);
        buffer.putLong(values.computeIfAbsent(dst, k -> new UserValues()).avgDiscoveries());
        buffer.putLong(avg(discovered));
        buffer.flip();
        logger.info("Sending values to " + dst);
        dc.send(buffer, dst);
      }
    } finally {
      dc.close();
    }
  }

  public static void usage() {
    System.out.println("Usage : ServerPrimer port");
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      usage();
      return;
    }
    ServerPrimer server;
    int port = Integer.valueOf(args[0]);
    if (!(port >= 1024) & port <= 65535) {
      System.out.println("The port number must be between 1024 and 65535");
      return;
    }
    try {
      server = new ServerPrimer(port);
    } catch (BindException e) {
      System.out
              .println("Server could not bind on " + port + "\nAnother server is probably running on this port.");
      return;
    }
    server.serve();
  }
}