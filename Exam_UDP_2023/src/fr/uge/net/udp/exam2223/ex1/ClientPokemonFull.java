package fr.uge.net.udp.exam2223.ex1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientPokemonFull {
  private static final int BUFSIZ_SEND = 1024;
  private static final int BUFSIZ_RECEIVE = 2048;
  private static final int TIMEOUT = 300;
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final Logger logger = Logger.getLogger(ClientPokemonFull.class.getName());

  private final String inFilename;
  private final String outFilename;
  private final InetSocketAddress server;
  private final DatagramChannel datagramChannel;

  private record Pokemon(String name, Map<String,Integer> characteristics){
    public Pokemon {
      Objects.requireNonNull(name);
      characteristics= Map.copyOf(characteristics);
    }

    @Override
    public String toString() {
      var stringBuilder = new StringBuilder();
      stringBuilder.append(name);
      for( var entry : characteristics.entrySet()){
        stringBuilder.append(';')
                .append(entry.getKey())
                .append(':')
                .append(entry.getValue());
      }
      return stringBuilder.toString();
    }
  }

  public static void usage() {
    System.out.println("Usage : ClientPokemon in-filename out-filename host port ");
  }

  public ClientPokemonFull(String inFilename, String outFilename,
                           InetSocketAddress server) throws IOException {
    this.inFilename = Objects.requireNonNull(inFilename);
    this.outFilename = Objects.requireNonNull(outFilename);
    this.server = server;
    this.datagramChannel = DatagramChannel.open();
  }

  private String pokemonName(ByteBuffer buffer) {
    var nameBuffer = ByteBuffer.allocate(BUFSIZ_RECEIVE);
    while (buffer.hasRemaining()) {
      var data = buffer.get();
      if (data == 0x00) {
        break;
      }

      nameBuffer.put(data);
    }

    nameBuffer.flip();
    return UTF8.decode(nameBuffer).toString();
  }

  private HashMap<String, Integer> pokemonCharacteristics(ByteBuffer buffer) {
    var characteristics = new HashMap<String, Integer>();
    var charactBuffer = ByteBuffer.allocate(BUFSIZ_RECEIVE);

    while (buffer.hasRemaining()) {
      var data = buffer.get();
      if (data == 0x00) {
        if (buffer.remaining() < Integer.BYTES) {
          logger.warning("Invalid packet format, stopping characteristics retrieve...");
          break;
        }

        charactBuffer.flip();
        characteristics.put(UTF8.decode(charactBuffer).toString(), buffer.getInt());
        charactBuffer.clear();
        continue;
      }
      charactBuffer.put(data);
    }

    return characteristics;
  }

  public void launch() throws IOException, InterruptedException {
    try {
      datagramChannel.bind(null);
      var pokemonNames = Files.readAllLines(Path.of(inFilename), UTF8);
      var pokemonQueue = new ArrayBlockingQueue<Pokemon>(pokemonNames.size());
      var pokemons = new ArrayList<Pokemon>();

      Thread.ofPlatform().start(() -> {
        var receiveBuffer = ByteBuffer.allocateDirect(BUFSIZ_RECEIVE);
        for (;;) {
          try {
            receiveBuffer.clear();
            var dst = (InetSocketAddress) datagramChannel.receive(receiveBuffer);
            receiveBuffer.flip();
            logger.info("Received " + receiveBuffer.remaining() + " bytes from " + dst);

            var name = pokemonName(receiveBuffer);
            if (!pokemonNames.contains(name)) {
              logger.warning("Invalid pokémon name:" + name + ". Dropping ...");
              continue;
            }
            logger.info("Received pokemon: " + name);
            pokemonQueue.add(new Pokemon(name, pokemonCharacteristics(receiveBuffer)));
          } catch (AsynchronousCloseException e) {
            logger.info("Channel closed, stopping receiver.");
            return;
          } catch (IOException e) {
            logger.warning("IOException occured on receiver.");
            return;
          }
        }
      });

      var sendBuffer = ByteBuffer.allocateDirect(BUFSIZ_SEND);

      for (var pokemonName : pokemonNames) {
        sendBuffer.clear();
        var encode = UTF8.encode(pokemonName);
        sendBuffer.putInt(encode.remaining());
        sendBuffer.put(encode);
        sendBuffer.flip();
        logger.info("Sending " + pokemonName + " to " + server);
        datagramChannel.send(sendBuffer, server);

        for (;;) {
          var response = pokemonQueue.poll(TIMEOUT, TimeUnit.MILLISECONDS);
          if (response == null) {
            logger.info("Timeout expired. Sending " + pokemonName + " again.");
            sendBuffer.flip();
            datagramChannel.send(sendBuffer, server);
            continue;
          }

          if (!response.name().equals(pokemonName)) {
            logger.info("Invalid pokemon, dropping....");
            continue;
          }

          logger.info("Received pokemon " + pokemonName + ". Adding to list.");
          pokemons.add(response);
          break;
        }
      }

      // Convert the pokemons to strings and write then in the output file
      var lines = pokemons.stream().map(Pokemon::toString).toList();
      Files.write(Paths.get(outFilename), lines , UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
    } finally {
      datagramChannel.close();
    }
  }


  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length != 4) {
      usage();
      return;
    }

    var inFilename = args[0];
    var outFilename = args[1];
    var server = new InetSocketAddress(args[2], Integer.parseInt(args[3]));

    // Create client with the parameters and launch it
    new ClientPokemonFull(inFilename, outFilename, server).launch();
  }
}