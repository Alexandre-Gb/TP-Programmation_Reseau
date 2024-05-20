package fr.upem.net.tcp.nonblocking;

import java.util.Objects;

public record Message(String login, String message) {
    public Message {
        Objects.requireNonNull(login);
        Objects.requireNonNull(message);
    }
}
