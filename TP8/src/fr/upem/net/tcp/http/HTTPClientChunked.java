package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Logger;

public class HTTPClientChunked {
  private static final Charset ASCII = StandardCharsets.US_ASCII;
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final int BUFSIZ = 1024;
  private static final int PORT = 80;
  private static final Logger logger = Logger.getLogger(HTTPClientChunked.class.getName());
  private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFSIZ);
  private Charset bodyCharset = UTF8;
  private InetSocketAddress dst;
  private String resource;
  private HTTPReader reader;

  public HTTPClientChunked(String dst, String resource) {
    Objects.requireNonNull(dst);
    Objects.requireNonNull(resource);

    this.dst = new InetSocketAddress(dst.trim(), PORT);
    this.resource = resource.trim();
  }

  public void resource(String resource) {
    Objects.requireNonNull(resource);
    this.resource = resource;
  }

  public void dst(String dst) {
    Objects.requireNonNull(dst);
    this.dst = new InetSocketAddress(dst.trim(), PORT);
  }

  public void reader(SocketChannel sc, ByteBuffer buffer) {
    Objects.requireNonNull(sc);
    Objects.requireNonNull(buffer);
    this.reader = new HTTPReader(sc, buffer);
  }

  public static void usage() {
    System.out.println("Usage: java HTTPClient <dst_address> <resource>");
  }

  private void sendRequest(SocketChannel socketChannel) throws IOException {
    var payload = "GET " + resource + " HTTP/1.1\r\nHost: " + dst.getHostName() + "\r\n\r\n";
    buffer.clear();
    buffer.put(ASCII.encode(payload));
    buffer.flip();

    logger.info("Sending header:\n" + payload);
    socketChannel.write(buffer);
  }

  private HTTPHeader retrieveHeader(SocketChannel socketChannel) throws IOException {
    sendRequest(socketChannel);
    buffer.clear();
    return reader.readHeader();
  }

  private String limitedResponse(HTTPHeader header) throws IOException {
    logger.info("Retrieving response");
    var buffer = reader.readBytes(header.getContentLength());

    if (header.getCharset().isEmpty()) {
      logger.info("No charset specified, using UTF-8.");
    } else {
      logger.info("Charset specified: " + bodyCharset);
      bodyCharset = header.getCharset().get();
    }

    buffer.flip();
    logger.info("Received " + buffer.remaining() + " bytes from " + dst);
    return bodyCharset.decode(buffer).toString();
  }

  private String chunckedResponse(HTTPHeader header) throws IOException {
    logger.info("Retrieving chuncked response");
    var buffer = reader.readChunks();

    if (header.getCharset().isEmpty()) {
      logger.info("No charset specified, using UTF-8.");
    } else {
      logger.info("Charset specified: " + bodyCharset);
      bodyCharset = header.getCharset().get();
    }

    buffer.flip();
    logger.info("Received " + buffer.remaining() + " bytes from " + dst);
    return bodyCharset.decode(buffer).toString();
  }

  public String response() throws IOException {
    try (var sc = SocketChannel.open()) {
      sc.connect(dst);
      reader(sc, buffer);

      var header = retrieveHeader(sc);
      logger.info("Received header:\n" + header);
      if (header.getContentLength() < 0) {
        logger.warning("No content length specified, dropping...");
        throw new HTTPException();
      }

      if (header.getCode() == 301 || header.getCode() == 302) {
        var fields = header.getFields();
        URL url;
        try {
          url = new URI(fields.get("location")).toURL();
        } catch (URISyntaxException e) {
          logger.severe("Invalid URI in redirection: " + e.getMessage());
          throw new HTTPException();
        }

        dst(url.getHost());
        resource(url.getPath());
        return response();
      }

      if (header.isChunkedTransfer()) {
        return chunckedResponse(header);
      }

      return limitedResponse(header);
    }
  }

  public static void main(String[] args) {
    if (args.length < 2) {
      usage();
      return;
    }

    var client = new HTTPClientChunked(args[0], args[1]);
    try {
      System.out.println(client.response());
    } catch (IOException e) {
      logger.severe("Error while communicating with " + args[0] + ": " + e.getMessage());
    }
  }
}
