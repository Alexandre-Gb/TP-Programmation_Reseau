package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientConcatenation {
    private static final Logger logger = Logger.getLogger(ClientConcatenation.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    private static boolean checkResult(List<String> list, String result) {
        return String.join(",", list).equals(result);
    }

    private static List<String> stringsFromUser() {
        var scanner = new Scanner(System.in);
        var list = new ArrayList<String>();
        System.out.println("Enter your strings:");

        while (scanner.hasNextLine()) {
            var line = scanner.nextLine();
            if (line.isEmpty()) {
                break;
            }

            list.add(line);
        }

        scanner.close();
        return List.copyOf(list);
    }

    /**
     * Concats all the given strings into one and returns it
     * returns null if the protocol is not followed by the server but no
     * IOException is thrown
     *
     * @param sc      Server channel
     * @param strings A list containing all the strings to concat
     * @return Concat of all the given strings
     */
    private static String requestConcatForList(SocketChannel sc, List<String> strings) throws IOException {
        var dst = sc.getRemoteAddress();
        var encodedStrings = strings.stream()
                .map(UTF8::encode)
                .toList();

        var nbStrings = encodedStrings.size();
        var concatString = encodedStrings.stream().mapToInt(Buffer::remaining).sum();
        var senderSize = Integer.BYTES + (nbStrings * Integer.BYTES) + concatString;
        var sendBuffer = ByteBuffer.allocate(senderSize);
        var receiveBuffer = ByteBuffer.allocate(Integer.BYTES);

        logger.info("About to send " + nbStrings + " strings for a total size of " + senderSize);
        sendBuffer.putInt(nbStrings);
        for (var encodedString : encodedStrings) {
            sendBuffer.putInt(encodedString.remaining());
            sendBuffer.put(encodedString);
        }

        sendBuffer.flip();
        logger.info("Sending " + sendBuffer.remaining() + " bytes to " + dst);
        sc.write(sendBuffer);

        if (!ClientEOS.readFully(sc, receiveBuffer)) {
            logger.warning("Invalid format, dropping...");
            return null;
        }

        receiveBuffer.flip();
        var receiverSize = receiveBuffer.getInt();
        if (receiverSize < 0) {
            logger.warning("Invalid format, dropping...");
            return null;
        }
        logger.info("Size of response: " + receiverSize);
        receiveBuffer = ByteBuffer.allocate(receiverSize);

        if (!ClientEOS.readFully(sc, receiveBuffer)) {
            logger.warning("Invalid format, dropping...");
            return null;
        }

        receiveBuffer.flip();
        return UTF8.decode(receiveBuffer).toString();
    }

    public static void main(String[] args) throws IOException {
        var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        try (var sc = SocketChannel.open(server)) {
            var strings = stringsFromUser();

            var result = requestConcatForList(sc, strings);
            if (result == null) {
                logger.warning("Connection with server lost.");
                return;
            }

            logger.info("Expect. " + String.join(",", strings) + "\nReceiv. " + result);
            if (!checkResult(strings, result)) {
                logger.warning("Oups! Something wrong happened!");
                return;
            }
            logger.info("Everything seems ok");
        }
    }
}
