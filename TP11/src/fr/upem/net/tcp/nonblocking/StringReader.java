package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class StringReader implements Reader<String> {
  private enum State {
    DONE, WAITING_INT, WAITING_STRING, ERROR
  };

  private static final int BUFSIZ = 1024;
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private final IntReader intReader = new IntReader();
  private final ByteBuffer internalBuffer = ByteBuffer.allocate(BUFSIZ);
  private State state = State.WAITING_INT;
  private int size;
  private String value;

  @Override
  public ProcessStatus process(ByteBuffer buffer) {
    Objects.requireNonNull(buffer);
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }

    if (state == State.WAITING_INT) {
      var readSize = intReader.process(buffer);
      if (readSize == ProcessStatus.REFILL) {
        return ProcessStatus.REFILL;
      }

      size = intReader.get();
      if (size < 0 || size > BUFSIZ) {
        state = State.ERROR;
        return ProcessStatus.ERROR;
      }

      state = State.WAITING_STRING;
    }

    if (state == State.WAITING_STRING) {
      buffer.flip();
      var missing = size - internalBuffer.position();
      try {
        if (buffer.remaining() <= missing) {
          internalBuffer.put(buffer);
        } else {
          var tmpLimit = buffer.limit();
          buffer.limit(buffer.position() + missing);
          internalBuffer.put(buffer);
          buffer.limit(tmpLimit);
        }
      } finally {
        buffer.compact();
      }

      if (internalBuffer.position() < size) {
        return ProcessStatus.REFILL;
      }

      state = State.DONE;
      value = UTF8.decode(internalBuffer.flip()).toString();
    }

    return ProcessStatus.DONE;
  }

  @Override
  public String get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }

    return value;
  }

  @Override
  public void reset() {
    state = State.WAITING_INT;
    intReader.reset();
    internalBuffer.clear();
  }
}
