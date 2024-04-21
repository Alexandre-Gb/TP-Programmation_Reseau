package fr.uge.net.udp.exam2223.ex3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerSlice {
  private static final int BUFSIZ = Long.BYTES * 128;
  private static final Logger logger = Logger.getLogger(ServerSlice.class.getName());
  private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFSIZ);
  private final DatagramChannel datagramChannel;
  private final Selector selector;
  private InetSocketAddress sender;
  private final int port;

  public ServerSlice(int port) throws IOException {
    this.port = port;
    this.selector = Selector.open();
    this.datagramChannel = DatagramChannel.open();
  }

  public void serve() throws IOException {
    datagramChannel.bind(new InetSocketAddress(port));
    datagramChannel.configureBlocking(false);
    datagramChannel.register(selector, SelectionKey.OP_READ);
    logger.info("ServerSlice started on port " + port);
    while (!Thread.interrupted()) {
      selector.select(this::treatKey);
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
      throw new UncheckedIOException(e);
    }
  }

  private void doRead(SelectionKey key) throws IOException {
    buffer.clear();
    sender = (InetSocketAddress) datagramChannel.receive(buffer);
    if (sender == null) {
      logger.warning("Could not receive any packet.");
      return;
    }

    buffer.flip();
    logger.info("Received " + buffer.remaining() + " bytes from " + sender);
    key.interestOps(SelectionKey.OP_WRITE);
  }

  private void doWrite(SelectionKey key) throws IOException {
    if (buffer.remaining() < Long.BYTES) {
      logger.info("All longs has been sent as slices.");
      key.interestOps(SelectionKey.OP_READ);
      return;
    }

    var limitTmp = buffer.limit();
    buffer.limit(buffer.position() + Long.BYTES);
    datagramChannel.send(buffer, sender);
    if (buffer.hasRemaining()) {
      logger.warning("Could not send any data to " + sender);
      buffer.position(buffer.position() - Long.BYTES);
    }
    buffer.limit(limitTmp);
  }

  public static void usage() {
    System.out.println("Usage : ServerSlice port");
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      usage();
      return;
    }
    new ServerSlice(Integer.parseInt(args[0])).serve();
  }
}