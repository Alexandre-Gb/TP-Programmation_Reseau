package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.util.Objects;

public class MessageReader implements Reader<Message> {
  private enum State {
    DONE, WAITING_LOGIN, WAITING_MESSAGE, ERROR
  };

  private final StringReader reader = new StringReader();
  private State state = State.WAITING_LOGIN;
  private Message value;
  private String login;
  private String message;

  @Override
  public ProcessStatus process(ByteBuffer buffer) {
    Objects.requireNonNull(buffer);
    switch (state) {
      case WAITING_LOGIN -> {
        var readerLogin = reader.process(buffer);
        if (readerLogin == ProcessStatus.ERROR) {
          state = State.ERROR;
          return ProcessStatus.ERROR;
        }
        if (readerLogin == ProcessStatus.REFILL) {
          return ProcessStatus.REFILL;
        }
        state = State.WAITING_MESSAGE;
        login = reader.get();
        reader.reset();
      }
      case WAITING_MESSAGE -> {
        var readerMessage = reader.process(buffer);
        if (readerMessage == ProcessStatus.ERROR) {
          state = State.ERROR;
          return ProcessStatus.ERROR;
        }
        if (readerMessage == ProcessStatus.REFILL) {
          return ProcessStatus.REFILL;
        }
        message = reader.get();
        reader.reset();
      }
      default -> throw new IllegalStateException();
    }

    state = State.DONE;
    value = new Message(login, message);
    return ProcessStatus.DONE;
  }

  @Override
  public Message get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }

    return value;
  }

  @Override
  public void reset() {
    state = State.WAITING_LOGIN;
    reader.reset();
  }
}
