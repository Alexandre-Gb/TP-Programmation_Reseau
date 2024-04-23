package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Logger;

public class ClientEOS {
  public static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;
  public static final int BUFFER_SIZE = 1024;
  public static final Logger logger = Logger.getLogger(ClientEOS.class.getName());

  /**
   * This method:
   * - connect to server
   * - writes the bytes corresponding to request in UTF8
   * - closes the write-channel to the server
   * - stores the bufferSize first bytes of server response
   * - return the corresponding string in UTF8
   *
   * @param request The request to encode in the buffer
   * @param server Socket corresponding to the server
   * @param bufferSize Size of receive buffer
   * @return the UTF8 string corresponding to bufferSize first bytes of server
   *         response
   * @throws IOException If any severe IOException occurs
   */

  public static String getFixedSizeResponse(String request, SocketAddress server, int bufferSize) throws IOException {
    Objects.requireNonNull(request);
    Objects.requireNonNull(server);
    Objects.checkIndex(bufferSize, BUFFER_SIZE);

    var encodeStr = UTF8_CHARSET.encode(request);
    var sendBuffer = ByteBuffer.allocate(encodeStr.remaining());
    var receiveBuffer = ByteBuffer.allocate(bufferSize);
    try (var sc = SocketChannel.open()) {
      sc.connect(server);
      sendBuffer.put(encodeStr);
      sendBuffer.flip();
      sc.write(sendBuffer);
      sc.shutdownOutput(); // Close write-channel

      var read = sc.read(receiveBuffer);
      if (read == -1) {
        logger.info("Connection closed for reading. Could not receive any data from " + server);
      } else {
        receiveBuffer.flip();
        logger.info("Received " + receiveBuffer.remaining() + " bytes from " + server);
      }
    }

    return UTF8_CHARSET.decode(receiveBuffer).toString();
  }

  /**
   * This method:
   * - connect to server
   * - writes the bytes corresponding to request in UTF8
   * - closes the write-channel to the server
   * - reads and stores all bytes from server until read-channel is closed
   * - return the corresponding string in UTF8
   *
   * @param request The request to send to the server
   * @param server Socket corresponding to the server
   * @return the UTF8 string corresponding the full response of the server
   * @throws IOException If any severe IOException occurs
   */
  public static String getUnboundedResponse(String request, SocketAddress server) throws IOException {
    Objects.requireNonNull(request);
    Objects.requireNonNull(server);

    var encodeStr = UTF8_CHARSET.encode(request);
    var sendBuffer = ByteBuffer.allocate(encodeStr.remaining());
    var receiveBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    try (var sc = SocketChannel.open()) {
      sc.connect(server);
      sendBuffer.put(encodeStr);
      sendBuffer.flip();
      sc.write(sendBuffer);
      sc.shutdownOutput(); // Close write-channel

      var builder = new StringBuilder();
      while (sc.read(receiveBuffer) != -1) {
        receiveBuffer.flip();
        logger.info("Received " + receiveBuffer.remaining() + " bytes from " + server);
        builder.append(UTF8_CHARSET.decode(receiveBuffer));
        receiveBuffer.clear();
      }
      logger.info("Connection closed for reading.");
      return builder.toString();
    }
  }

  /**
   * This method:
   * - connect to server
   * - writes the bytes corresponding to request in UTF8
   * - closes the write-channel to the server
   * - reads and stores all bytes from server until read-channel is closed
   * - return the corresponding string in UTF8
   *
   * @param request The request to send to the server
   * @param server Socket corresponding to the server
   * @return the UTF8 string corresponding the full response of the server
   * @throws IOException If any severe IOException occurs
   */
  public static String getUnboundedResponseWithReadFully(String request, SocketAddress server) throws IOException {
    Objects.requireNonNull(request);
    Objects.requireNonNull(server);

    var encodeStr = UTF8_CHARSET.encode(request);
    var sendBuffer = ByteBuffer.allocate(encodeStr.remaining());
    var receiveBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    try (var sc = SocketChannel.open()) {
      sc.connect(server);
      sendBuffer.put(encodeStr);
      sendBuffer.flip();
      sc.write(sendBuffer);
      sc.shutdownOutput(); // Close write-channel

      var builder = new StringBuilder();
      while (readFully(sc, receiveBuffer)) {
        receiveBuffer.flip();
        logger.info("Received " + receiveBuffer.remaining() + " bytes from " + server);
        builder.append(UTF8_CHARSET.decode(receiveBuffer));
        receiveBuffer.clear();
      }

      return builder.toString();
    }
  }

  /**
   * Fill the workspace of the Bytebuffer with bytes read from sc.
   *
   * @param sc Socket corresponding to the server
   * @param buffer ByteBuffer to fill
   * @return false if read returned -1 at some point and true otherwise
   * @throws IOException If any severe IOException occurs
   */
  static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      if (sc.read(buffer) == -1) {
        return false;
      }
    }

    return true;
  }

  public static void main(String[] args) throws IOException {
    var google = new InetSocketAddress("www.google.fr", 80);
    // System.out.println(getFixedSizeResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google, 512));
    // System.out.println(getUnboundedResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google));
    System.out.println(getUnboundedResponseWithReadFully("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google));
  }
}