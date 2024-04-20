package fr.upem.net.udp.exam2022.ex3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoRepeat {
  private static final Logger logger = Logger.getLogger(ServerEchoRepeat.class.getName());
  private static final int BUFFER_SIZE = 1020 + Integer.BYTES;
  private final DatagramChannel dc;
  private final Selector selector;
  private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
  private InetSocketAddress sender;
  private int port;
  private int counter; // = 0

  public ServerEchoRepeat(int port) throws IOException {
    this.port = port;
    selector = Selector.open();
    dc = DatagramChannel.open();
    dc.bind(new InetSocketAddress(port));
    dc.configureBlocking(false);
    dc.register(selector, SelectionKey.OP_READ);
  }

  public void serve() throws IOException {
    logger.info("ServerEchoRepeat started on port " + port);
    while (!Thread.interrupted()) {
      try {
        selector.select(this::treatKey);
      } catch (UncheckedIOException tunnel) {
        throw tunnel.getCause();
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
    } catch (IOException e) {
      logger.severe("IOException occurred");
      throw new UncheckedIOException(e);
    }
  }

  private void doRead(SelectionKey key) throws IOException {
    buffer.clear();
    sender = (InetSocketAddress) dc.receive(buffer);
    if (sender == null) {
      logger.warning("No packet received (No SocketAddress).");
      return;
    }
    buffer.flip();
    logger.info("Received " + buffer.remaining() + " bytes from " + sender);

    if (buffer.remaining() < Integer.BYTES) {
      logger.warning("Invalid packet format, dropping...");
      return;
    }
    counter = buffer.getInt();
    logger.info(counter + " repetitions.");
    key.interestOps(SelectionKey.OP_WRITE);
  }

  private void doWrite(SelectionKey key) throws IOException {
    dc.send(buffer, sender);
    if (buffer.hasRemaining()) {
      logger.warning("Packet could not be send.");
      return;
    }

    buffer.flip();
    buffer.position(Integer.BYTES);
    if (--counter < 1) {
      key.interestOps(SelectionKey.OP_READ);
    }
  }

  public static void usage() {
    System.out.println("Usage : ServerEchoRepeat port");
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      usage();
      return;
    }
    new ServerEchoRepeat(Integer.parseInt(args[0])).serve();
  }
}