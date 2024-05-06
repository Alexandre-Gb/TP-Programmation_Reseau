package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.logging.Logger;

public class ServerSumOneShot {

	private static final Logger logger = Logger.getLogger(ServerSumOneShot.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;

	public ServerSumOneShot(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		while (!Thread.interrupted()) {
			try {
				Helpers.printKeys(selector); // for debug
				System.out.println("Starting select");
				selector.select(this::treatKey);
				System.out.println("Select finished");
			} catch (UncheckedIOException tunnel) {
				throw tunnel.getCause();
			}
		}
	}

	private void treatKey(SelectionKey key) {
		Helpers.printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch (IOException e) {
			logger.severe("Error during acceptation");
			throw new UncheckedIOException(e);
		}

		try {
			if (key.isValid() && key.isWritable()) {
				doWrite(key);
			}
			if (key.isValid() && key.isReadable()) {
				doRead(key);
			}
		} catch (IOException e) {
			logger.info("Connection closed");
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		var sc = serverSocketChannel.accept();
		if (sc == null) {
			logger.info("Connection refused");
			return;
		}

		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(Integer.BYTES * 2));
		// var clientKey = sc.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(Integer.BYTES * 2));
		// clientKey.attach(ByteBuffer.allocate(Integer.BYTES * 2));
	}

	private void doRead(SelectionKey key) throws IOException {
		var sc = (SocketChannel) key.channel();
		var bb = (ByteBuffer) key.attachment();
		if (sc.read(bb) == -1) {
			silentlyClose(key);
			return;
		}
		if (bb.hasRemaining()) {
			return;
		}

		bb.flip();
		var sum = bb.getInt() + bb.getInt();
		logger.info("Sum is " + sum);
		bb.clear();
		bb.putInt(sum);
		bb.flip();
		key.interestOps(SelectionKey.OP_WRITE);
	}

	private void doWrite(SelectionKey key) throws IOException {
		var sc = (SocketChannel) key.channel();
		var bb = (ByteBuffer) key.attachment();
		sc.write(bb);
		if (bb.hasRemaining()) {
			return;
		}

		silentlyClose(key);
	}

	private void silentlyClose(SelectionKey key) {
		var sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		new ServerSumOneShot(Integer.parseInt(args[0])).launch();
	}

	private static void usage() {
		System.out.println("Usage : ServerSumOneShot port");
	}
}