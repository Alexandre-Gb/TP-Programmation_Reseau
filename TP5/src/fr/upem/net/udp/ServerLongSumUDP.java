package fr.upem.net.udp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class ServerLongSumUDP {
  private static final Logger logger = Logger.getLogger(ServerLongSumUDP.class.getName());
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final int BUFFER_SIZE = 1024;

  private final DatagramChannel dc;
  private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

  private static final int OP = 1;
  private static final int ACK = 2;
  private static final int RES = 3;

  private static class Session {
    private final BitSet status;
    private long sum;

    Session(long totalOper) {
      if (totalOper < 0) {
        throw new IllegalArgumentException();
      }

      this.status = new BitSet(Math.toIntExact(totalOper));
      this.status.flip(0, Math.toIntExact(totalOper));
    }

    boolean tryAdd(long idPosOper, long opValue) {
      if (!status.get(Math.toIntExact(idPosOper))) {
        return false;
      }

      status.set(Math.toIntExact(idPosOper), false);
      sum += opValue;

      return true;
    }

    boolean isComplete() {
      return status.cardinality() == 0;
    }

    long sum() {
      return sum;
    }
  }

  private static class SessionHolder {
    private final HashMap<InetSocketAddress, HashMap<Long, Session>> sessionOfAdress = new HashMap<>();

    /**
     * Opens a new session if it does not already exist, and returns it.
     *
     * @param dst       The destination address of the session
     * @param sessionId The id of the session
     * @param totalOper Total of operations
     * @return The session
     */
    Session getOrCreateSession(InetSocketAddress dst, long sessionId, long totalOper) {
      var sessions = sessionOfAdress.computeIfAbsent(dst, k -> new HashMap<>());
      return sessions.computeIfAbsent(sessionId, k -> new Session(totalOper));
    }

    /**
     * Adds a new operand to a session.
     *
     * @param dst       The destination address of the session
     * @param sessionId The id of the session
     * @param idPosOper The id of the operand
     * @param totalOper The total of operations
     * @param opValue   The value of the operand
     * @return True if the session could complete, false otherwise
     */
    boolean tryAdd(InetSocketAddress dst, long sessionId, long idPosOper, long totalOper, long opValue) {
      var session = getOrCreateSession(dst, sessionId, totalOper);
      session.tryAdd(idPosOper, opValue);

      return session.isComplete();
    }
  }

  void submitAck(InetSocketAddress dst, long sessionId, long idPosOper) throws IOException {
    buffer.clear();
    buffer.put((byte) ACK);
    buffer.putLong(sessionId);
    buffer.putLong(idPosOper);
    buffer.flip();
    dc.send(buffer, dst);
  }

  void submitResult(InetSocketAddress dst, long sessionId, long result) throws IOException {
    buffer.clear();
    buffer.put((byte) RES);
    buffer.putLong(sessionId);
    buffer.putLong(result);
    buffer.flip();
    dc.send(buffer, dst);
  }

  public void serve() throws IOException {
    try {
      var sessions = new SessionHolder();
      while (!Thread.interrupted()) {
        buffer.clear();
        var dst = (InetSocketAddress) dc.receive(buffer);
        buffer.flip();

        if (buffer.remaining() < Long.BYTES * 4 + Byte.BYTES) {
          logger.info("Invalid packet format. Dropping...");
          continue;
        }

        var op = buffer.get();
        if (op != OP) {
          logger.info("Unsupported operator. Dropping...");
          continue;
        }

        var sessionId = buffer.getLong();
        var idPosOper = buffer.getLong();
        var totalOper = buffer.getLong();
        var opValue = buffer.getLong();
        logger.info("Received " + op + " " + sessionId + " " + idPosOper + " " + totalOper + " " + opValue + " from " + dst + "\nSending ACK to " + dst + ".");

        try {
          submitAck(dst, sessionId, idPosOper);
        } catch (IOException e) {
          logger.severe("Failed to send ACK to " + dst + ".");
          continue;
        }

        if (sessions.tryAdd(dst, sessionId, idPosOper, totalOper, opValue)) {
          var result = sessions.getOrCreateSession(dst, sessionId, totalOper).sum();

          logger.info("Session " + sessionId + " is complete. Sending result (RES) to " + dst + ".");
          try {
            submitResult(dst, sessionId, result);
          } catch (IOException e) {
            logger.severe("Failed to send result (RES) to " + dst + ".");
            // continue;
          }
        }
      }
    } finally {
      dc.close();
    }
  }

  public ServerLongSumUDP(int port) throws IOException {
    dc = DatagramChannel.open();
    dc.bind(new InetSocketAddress(port));
    logger.info("ServerBetterUpperCaseUDP started on port " + port);
  }

  public static void usage() {
    System.out.println("Usage : ServerIdUpperCaseUDP port");
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      usage();
      return;
    }

    var port = Integer.parseInt(args[0]);

    if (!(port >= 1024) & port <= 65535) {
      logger.severe("The port number must be between 1024 and 65535");
      return;
    }

    try {
      new ServerLongSumUDP(port).serve();
    } catch (BindException e) {
      logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
    }
  }
}
