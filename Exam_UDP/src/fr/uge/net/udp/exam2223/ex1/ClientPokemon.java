package fr.uge.net.udp.exam2223.ex1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;


import static java.nio.file.StandardOpenOption.*;

public class ClientPokemon {
  private static final int BUFSIZ_SEND = 1024;
  private static final int BUFSIZ_RECEIVE = 2048;
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final Logger logger = Logger.getLogger(ClientPokemon.class.getName());

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

  public ClientPokemon(String inFilename, String outFilename,
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
      var pokemons = new ArrayList<Pokemon>();

      var buffer = ByteBuffer.allocateDirect(BUFSIZ_SEND);

      for (var pokemonName : pokemonNames) {
        buffer.clear();
        var encode = UTF8.encode(pokemonName);
        buffer.putInt(encode.remaining());
        buffer.put(encode);
        buffer.flip();
        System.out.println("Sending " + pokemonName + " to " + server);
        datagramChannel.send(buffer, server);

        buffer.clear();
        var dst = (InetSocketAddress) datagramChannel.receive(buffer);
        buffer.flip();
        System.out.println("Received " + buffer.remaining() + " bytes from " + dst);

        var name = pokemonName(buffer);
        if (!pokemonName.contains(name)) {
          System.out.println("Invalid pokémon:" + name + ". Dropping ...");
          continue;
        }
        System.out.println("Received pokemon: " + name);
        pokemons.add(new Pokemon(name, pokemonCharacteristics(buffer)));
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
    new ClientPokemon(inFilename, outFilename, server).launch();
  }
}