

import utilities.CommandExecutor;
import DatabaseObjects.Value;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import utilities.MultiKeyLocker;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class Server {


    //Load database from file if it exists
    {




            ObjectMapper mapper = new ObjectMapper();

            try (FileInputStream fis = new FileInputStream("DB.db");) {

                TypeReference<ConcurrentHashMap<String, Value>> ref = new TypeReference<ConcurrentHashMap<String, Value>>() {
                };

                database = mapper.readValue(fis, ref);

            } catch (FileNotFoundException e) {
                System.out.println("DB does not exist");
            }

            try (FileInputStream fis = new FileInputStream("EXCACHE.db");) {

                TypeReference<ConcurrentHashMap<String, String>> ref = new TypeReference<ConcurrentHashMap<String, String>>() {
                };

                expiryCache = mapper.readValue(fis, ref);

            } catch (FileNotFoundException e) {
                System.out.println("DB Expiry Cache does not exist");
            }


    }

    //Holds Server Connection
    private ServerSocket serverSocket;

    private ConcurrentHashMap<String, Value> database;

    private ConcurrentHashMap<String,String> expiryCache;

    private BufferedReader in;

    private ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private CommandExecutor commandExecutor;

    public Server(int port) throws IOException {

        //Start server
        serverSocket = new ServerSocket(port);


        if(database == null){
            //Initialise database
            database = new ConcurrentHashMap<String, Value>();
        }

        if(expiryCache == null){

            //Intialise expiry cache
            expiryCache = new ConcurrentHashMap<>();
        }

        commandExecutor = new CommandExecutor(database,expiryCache,locks);


        in = new BufferedReader(new InputStreamReader(System.in));

        //Mark for study tomorrow
        ExecutorService executor = Executors.newCachedThreadPool();

        //If no client has been accepted within 10 seconds shutdown
        //Temp measure
        while(true){


            try {
                //We will understand how this works tomorrow, just want to get this part done
                //Wait for client connection
                System.out.println("Waiting for client connection...");
                Socket socket = serverSocket.accept();
                System.out.println("Client Connected");

                executor.submit( () -> {
                    try {
                        handleClient(socket);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                });
            } catch (SocketTimeoutException e) {
                break;
            }



        }


        try {
            System.out.println("Server Disconnecting...");
            serverSocket.close();
            System.out.println("Server Disconnected");
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //Deserializers
    public String deserializer(InputStream in) throws IOException {

        String deserializedResponse = "";

        int type = in.read();

        if(type == -1){
            throw new SocketException("Client Disconnected");
        }

        if (type == '+') {
            deserializedResponse = deserializeSimpleString(in);
        } else if (type == '-') {
            deserializedResponse = deserializeSimpleError(in);
        } else if (type == ':') {
            deserializedResponse = deserialize64BitInt(in);
        } else if (type == '$') {
            deserializedResponse = deserializeBulkString(in);
        } else if (type == '*') {
            deserializedResponse = deserializeArray(in);
        } else {
            deserializedResponse = ("-ERR no valid type given\r\n");
        }

        return deserializedResponse;
    }

    private String deserializeSimpleString(InputStream input) throws IOException {


        StringBuilder deserializedResponse = new StringBuilder();

        int readByte = 0;

        while ((readByte = input.read()) != '\r') {

            if ((readByte == -1)) {
                throw new SocketException("ERR client timed-out");
            }

            if ((readByte == '\n')) {
                return "ERR incorrect termination command";
            }

            deserializedResponse.append((char) readByte);

        }

        if ((readByte = input.read()) != '\n') {
            return "ERR incorrect termination command";
        }

        return deserializedResponse.toString();
    }

    private String deserializeSimpleError(InputStream input) throws IOException {


        StringBuilder deserializedResponse = new StringBuilder();

        int readByte = 0;

        while ((readByte = input.read()) != '\r') {

            if ((readByte == -1)) {
                throw new SocketException("ERR client timed-out");
            }

            if ((readByte == '\n')) {
                return "ERR incorrect termination command";
            }

            deserializedResponse.append((char) readByte);

        }

        if ((readByte = input.read()) != '\n') {
            return "ERR incorrect termination command";
        }

        return deserializedResponse.toString();
    }

    private String deserialize64BitInt(InputStream input) throws IOException {

        StringBuilder deserializedResponse = new StringBuilder();

        int readByte = 0;


        while ((readByte = input.read()) != '\r') {

            if ((readByte == -1)) {
                throw new SocketException("ERR client timed-out");
            }

            if ((readByte == '\n')) {
                return "ERR incorrect termination command";
            }

            if (Character.isLetter((char) readByte)) {
                return "ERR not an int";
            }

                deserializedResponse.append((char) readByte);

        }

        readByte = input.read();

        if (readByte != '\n') {
            return "ERR incorrect termination command";
        }

        //If no numbers could be parsed return error
        if (deserializedResponse.toString().isEmpty()) {
            return "0";
        }

        return deserializedResponse.toString();
    }

    private String deserializeBulkString(InputStream input) throws IOException {

        StringBuilder deserializedResponse = new StringBuilder();
        StringBuilder ln = new StringBuilder();

        int readByte = 0;

        //First retrieve length of the string
        while ((readByte = input.read()) != '\r') {

            if ((readByte == -1)) {
                throw new SocketException("ERR client disconnected");
            }

            if (Character.isDigit((char) readByte) || readByte == '-') {
                ln.append((char) readByte);
            } else {
                return "ERR not an int";
            }

        }


        if (ln.toString().isEmpty()) {
            return "ERR no length";
        }

        readByte = input.read();

        if (readByte != '\n') {
            return "ERR incorrect termination command";
        }

        int strLen = Integer.parseInt(ln.toString());

        if (strLen == -1) {
            return "nil";
        }

        int counter = 0;
        while ((readByte = input.read()) != '\r') {
            if ((readByte == -1)) {
                throw new SocketException("ERR client disconnected/No Termination Command");
            }

            deserializedResponse.append((char) readByte);
            counter++;

        }

        readByte = input.read();
        if (readByte != '\n') {
            return "ERR incorrect termination command";
        }

        if (counter != strLen) {
            return "ERR string:" + deserializedResponse + " is not of size:" + strLen;
        }

        return deserializedResponse.toString();
    }

    private String deserializeArray(InputStream input) throws IOException {

        StringBuilder deserializedResponse = new StringBuilder();

        StringBuilder ln = new StringBuilder();

        int readByte = 0;

        int arrLen = 0;

        while ((readByte = input.read()) != '\r') {

            if ((readByte == -1)) {
                throw new SocketException("ERR client disconnected");
            }

            if (Character.isDigit((char) readByte) || readByte == '-') {
                ln.append((char) readByte);
            } else {
                return "ERR not an int";
            }
        }

        readByte = input.read();

        if (ln.toString().isEmpty()) {
            return "ERR no length found";
        }

        if (readByte != '\n') {
            throw new IOException("ERR incorrect termination command");
        }


        arrLen = Integer.parseInt(ln.toString());

        if (arrLen == -1) {
            return "nil";
        }

        for (int i = 0; i < arrLen; i++) {

            readByte = input.read();

            if (readByte == '+') {
                deserializedResponse.append(deserializeSimpleString(input));

            } else if (readByte == '-') {
                deserializedResponse.append(deserializeSimpleError(input));

            } else if (readByte == ':') {
                deserializedResponse.append(deserialize64BitInt(input));

            } else if (readByte == '$') {
                deserializedResponse.append(deserializeBulkString(input));

            } else if(readByte == '*'){
                deserializedResponse.append(deserializeArray(input));
            }else {
                throw new IOException("ERR no valid type found/Arr Len > Num of Elements");
            }


            if ((i + 1) != arrLen) {
                deserializedResponse.append(" ");
            }


        }

        return deserializedResponse.toString();
    }

    //Command Executors
    private void execute(String command, DataOutputStream dos) throws IOException {

//      Check what the command starts with as this determines what command implementation to use

        System.out.println("Received command: [" + command + "]");

        commandExecutor.executeCommand(command,dos);

    }

    //Client Handler
    public void handleClient(Socket socket) throws IOException {

        String command = "";

        //To read client message
        DataInputStream in;

        //To respond to client
        DataOutputStream out;

        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        out = new DataOutputStream(socket.getOutputStream());

        while (true) {

            try {

                command = deserializer(in);

                if("STOP".equals(command)) {
                    break;
                }

                if (command == null || command.trim().isEmpty()) {
                    break;
                }

                //write a response to client
                execute(command, out);

            } catch (SocketException se) {
                System.out.println("Client disconnected: " + se.getMessage());
                break;
            } catch (Exception e) {
                System.out.println("Error from command: " + command);
                e.printStackTrace();
            }


        }

        System.out.println("Client Disconnecting...");

        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Client Disconnected");

    }

    public static void main(String[] args) throws IOException {
        Server server = new Server(6379);
    }

}
