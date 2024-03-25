package fr.upem.net.buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

public class ReadStandardInputWithEncoding {
  private static final int BUFFER_SIZE = 1024;

  private static void usage() {
    System.out.println("Usage: ReadStandardInputWithEncoding charset");
  }

  private static String stringFromStandardInput(Charset cs) throws IOException {
    var buffer = ByteBuffer.allocate(BUFFER_SIZE);

    var in = Channels.newChannel(System.in);
    while (in.read(buffer) != -1) {
      if (!buffer.hasRemaining()) {
        var newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
        buffer.flip();
        newBuffer.put(buffer);
        buffer = newBuffer;
      }
    }

    buffer.flip();
    return cs.decode(buffer).toString();
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      usage();
      return;
    }
    Charset cs = Charset.forName(args[0]);
    System.out.print(stringFromStandardInput(cs));
  }
}