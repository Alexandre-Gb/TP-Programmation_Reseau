package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.logging.Logger;

public class ClientLongSum {
    private static final Logger logger = Logger.getLogger(ClientLongSum.class.getName());

    private static List<Long> randomLongList(int size) {
        return new Random().longs(size).boxed().toList();
    }

    private static boolean checkSum(List<Long> list, long response) {
        return list.stream().reduce(Long::sum).orElse(0L) == response;
    }

    /**
     * Write all the longs in list in BigEndian on the server and read the long sent
     * by the server and returns it
     * returns null if the protocol is not followed by the server but no
     * IOException is thrown
     *
     * @param sc Server channel
     * @param list The long values to send to the server
     * @return Result of the operation
     */
    private static Long requestSumForList(SocketChannel sc, List<Long> list) throws IOException {
        var dst = sc.getRemoteAddress();
        var sendBuffer = ByteBuffer.allocate(Integer.BYTES + (list.size() * Long.BYTES));
        var receiveBuffer = ByteBuffer.allocate(Long.BYTES);

        sendBuffer.putInt(list.size());
        list.forEach(sendBuffer::putLong);
        sendBuffer.flip();
        logger.info("Sending " + sendBuffer.remaining() + " bytes to " + dst);
        sc.write(sendBuffer);

        if (!ClientEOS.readFully(sc, receiveBuffer)) {
            logger.warning("Invalid format, dropping...");
            return null;
        }

        receiveBuffer.flip();
        logger.info("Received " + receiveBuffer.remaining() + " bytes from " + dst);
        return receiveBuffer.getLong();
    }

    public static void main(String[] args) throws IOException {
        var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        try (var sc = SocketChannel.open(server)) {
            for (var i = 0; i < 5; i++) {
                var list = randomLongList(50);

                var sum = requestSumForList(sc, list);
                if (sum == null) {
                    logger.warning("Connection with server lost.");
                    return;
                }
                if (!checkSum(list, sum)) {
                    logger.warning("Oups! Something wrong happened!");
                }
            }
            logger.info("Everything seems ok");
        }
    }
}