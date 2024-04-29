package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class HTTPClient {
  private static final Charset ASCII = StandardCharsets.US_ASCII;
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final int PORT = 80;
  private static final Logger logger = Logger.getLogger(HTTPClient.class.getName());

  public static void usage() {
    System.out.println("Usage: java HTTPClient <dst_address> <resource>");
  }

  /*
  Example of arguments to use:
    arg0: www.w3.org
    arg1: /mission/
   */
  public static void main(String[] args) {
    if (args.length < 2) {
      usage();
      return;
    }

    var buffer = ByteBuffer.allocate(50);
    var request = "GET " + args[1].trim() + " HTTP/1.1\r\nHost: " + args[0].trim() + "\r\n\r\n";
    logger.info("Request to send:\n" + request);

    try (var sc = SocketChannel.open()) {
      sc.connect(new InetSocketAddress(args[0], PORT));

      var sendBody = ASCII.encode(request);
      logger.info("Sending " + sendBody.remaining() + " bytes to " + sc.getRemoteAddress() + " on port " + PORT);
      sc.write(sendBody);

      var reader = new HTTPReader(sc, buffer);
      var header = reader.readHeader();
      logger.info("Received header:\n" + header);
      if (header.getContentLength() < 0) {
        logger.warning("No content length specified, dropping...");
      }

      var bodyCharset = UTF8;
      var body = reader.readBytes(header.getContentLength());
      if (header.getCharset().isEmpty()) {
        logger.info("No charset specified, using UTF-8.");
      } else {
        bodyCharset = header.getCharset().get();
        logger.info("Charset specified: " + bodyCharset);
      }

      body.flip();
      logger.info("Received " + body.remaining() + " bytes from " + sc.getRemoteAddress());
      if (header.getContentType().isEmpty()) {
        logger.warning("Content type unknown, dropping...");
        return;
      }

      if (header.getContentType().get().equals("text/html")) {
        System.out.println(bodyCharset.decode(body));
      } else {
        logger.info("Received content is not HTML.");
      }
    } catch (IOException e) {
      logger.severe("Error while communicating with " + args[0] + ": " + e.getMessage());
    }
  }
}
