

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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

        try(FileInputStream fis = new FileInputStream("DB.db");){

            TypeReference<ConcurrentHashMap<String,Value>> ref = new TypeReference<ConcurrentHashMap<String, Value>>() {
            };

           database = mapper.readValue(fis, ref);

        }catch(FileNotFoundException e){
            System.out.println("DB does not exist");
        }

        try(FileInputStream fis = new FileInputStream("EXCACHE.db");){

            TypeReference<ConcurrentHashMap<String,String>> ref = new TypeReference<ConcurrentHashMap<String, String>>() {
            };

            expiryCache = mapper.readValue(fis, ref);

        }catch(FileNotFoundException e){
            System.out.println("DB Expiry Cache does not exist");
        }

    }

    //Holds Server Connection
    private ServerSocket serverSocket;

    private ConcurrentHashMap<String, Value> database;

    private ConcurrentHashMap<String,String> expiryCache;

    private BufferedReader in;

    private ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

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
    private void commandExecutor(String command, DataOutputStream dos) throws IOException {

//        Check what the command starts with as this determines what command implementation to use

        String cmd = command.split(" ")[0];

        System.out.println("Received command: [" + command + "]");


        if(("PING").equals(cmd)){
            commandPing(dos);
        } else if(("ECHO").equals(cmd)){
            commandEcho(command,dos);
        } else if(("SET").equals(cmd)){
            commandSet(command, dos);
        } else if(("GET").equals(cmd)){
            commandGet(command,dos);
        } else if(("EXISTS").equals(cmd)){
            commandExists(command,dos);
        } else if(("DEL").equals(cmd)){
            commandDelete(command,dos);
        } else if (("INCR").equals(cmd)) {
            commandIncrement(command,dos);
        } else if (("DECR").equals(cmd)){
            commandDecrement(command,dos);
        } else if(("ERR").equals(cmd)){
            dos.write("ERR something went wrong".getBytes());
        } else if (("LPUSH").equals(cmd)){
            commandLPUSH(command,dos);
        } else if (("RPUSH").equals(cmd)){
            commandRPUSH(command,dos);
        } else if (("LRANGE").equals(cmd)){
            commandLRange(command,dos);
        } else if(("SAVE").equals(cmd)){
            commandSave(dos);
        } else if(("LCS").equals(cmd)){
            commandLCS(command,dos);
        } else if(("GETRANGE").equals(cmd)){
            commandGETRANGE(command,dos);
        } else if(("LMOVE").equals(cmd)){
            commandLMOVE(command, dos);
        } else if(("LMOVEM").equals(cmd)){
            commandLMOVEM(command,dos);
        } else if(("LLEN").equals(cmd)){
            commandLLEN(command,dos);
        }else{
            String err = "-err invalid command given!\r\n";
            dos.write(err.getBytes());
        }



    }

    public void commandPing(DataOutputStream dos) throws IOException {

        dos.write(new byte[]{'+','P','O','N','G','\r','\n'});

    }

    public void commandEcho(String command, DataOutputStream dos) throws IOException {

        String[] data = command.split(" ");

        //Add the string to print together
        StringBuilder str = new StringBuilder();

        //Build the final response
        StringBuilder response = new StringBuilder();

        int counter = 0;


        for(int i = 0; i < data.length; i++){

            if(!data[i].equals("ECHO")){

                str.append(data[i]);

                if(!(i + 1 == data.length)){
                    str.append(" ");
                }

            }

        }
        response.append("$")
                .append(str.toString().length())
                .append('\r')
                .append('\n')
                .append(str)
                .append('\r')
                .append('\n');


        //Write response to client
        dos.write(response.toString().getBytes());

    }

    public Value createValue(String key, String[] data) throws IOException {

        String option = "";

        String value = "";

        Value val = new Value();

        Long timeToExpire = null;

        StringBuilder str = new StringBuilder();



            for (int i = 2; i < data.length; i++) {

                if(!containsExpiryOption(data[i])) {
                    str.append(data[i]);
                    str.append(" ");
                } else {

                    //Save the option for later
                    option = data[i];

                    //Get the time to expire next
                    i++;
                    timeToExpire = Long.parseLong(data[i]);

                    //bad impl
                    // make sure there is nothing else after ex option as this should be final option
                    if(i + 1 == data.length - 1) {
                        return  null;
                    }
                }
            }


            value = str.toString().strip();

            //Once we have successfully stored the kv pair, set the expiry option if it exists
            val.setType("STRING");
            val.setValue(value);

            if(timeToExpire != null) {
                setExpiry(key, timeToExpire, option);
            }

        return val;
    }

    public void commandSet(String command, DataOutputStream dos) throws IOException {

        String res = "";

        String[] data = command.split(" ");

        String key = "";

        Value val = null;




            //If there are more than 2 items, then first is the command name, the second is the key and the rest
            // is the value
            if (data.length > 2) {


                key = data[1];

                //Get the lock associated with key
                ReentrantLock keyLock = getLock(key);

                //Lock access to the key, only one thread can work on this key at a time
                keyLock.lock();
                try {

                    val = createValue(key,data);

                    if(val != null) {
                        database.put(key,val);
                        res = "+OK\r\n";
                    }  else {
                        res = "-ERR something went wrong!\r\n";
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    res = ("-ERR " + "something went wrong please revise command" + "\r\n");
                } finally {
                    //Whether or not the thread passess, release the lock to prevent deadlock scenario
                    keyLock.unlock();
                }




            } else {
                res = "-ERR invalid command\r\n";
            }



        dos.write(res.getBytes());
    }



    public void commandGet(String command, DataOutputStream dos) throws IOException {

        String[] data = command.split(" ");

        String res = "";

        String key = "";

        StringBuilder str = new StringBuilder();

        System.out.println(data.length);
        //that means we only have our command and key, anything else is an error
        if(data.length != 2){
            res = "+nil\r\n";
            dos.write(res.getBytes());
            return;
        } else {
            key = data[1];
        }

        Value value = null;

        ReentrantLock keyLock = getLock(key);

        keyLock.lock();
        try {
            value = database.get(key);

            if (value == null) {
                res = "+nil\r\n";
                dos.write(res.getBytes());
                return;
            }

            if ("STRING".equals(value.getType())) {

                if (checkExpiry(key)) {
                    expiryCache.remove(key);
                    res = "+nil\r\n";
                    dos.write(res.getBytes());
                    return;
                }

                System.out.println("Value: " + value);
                str.append('$')
                        .append(value.getValue().length())
                        .append('\r')
                        .append('\n')
                        .append(value.getValue())
                        .append('\r')
                        .append('\n');
                res = str.toString();
            } else {

                res = "-ERR type must be of STRING\r\n";
                dos.write(res.getBytes());
                return;
            }

        } finally {
            keyLock.unlock();
        }



        dos.write(res.getBytes());
    }

    public void commandExists(String command, DataOutputStream dos) throws IOException {

        String[] data = command.split(" ");

        String key = "";

        if(!(data.length >= 2)){
            dos.write("-ERR no keys received\r\n".getBytes());
            return;
        }

        int count = 0;

        for(int i = 1; i < data.length; i++){


            key = data[i];

            ReentrantLock keyLock = getLock(data[i]);


            keyLock.lock();
            try {

                if(database.containsKey(key)){
                    count++;
                }

            } finally {
                keyLock.unlock();
            }



        }



        dos.write((":" + count + "\r\n").getBytes());

    }



    public void commandIncrement(String command, DataOutputStream dos) throws IOException {


        String[] data = command.split(" ");

        int res = 0;

        String key = "";

        Value val = null;

        if(data.length != 2){
            dos.write("-ERR invalid command execution\r\n".getBytes());
            return;
        }

        key =  data[1];

        try {

            ReentrantLock keyLock = getLock(key);

            keyLock.lock();
            try {
                if (checkExpiry(key)) {
                    expiryCache.remove(key);
                    dos.write("+nil\r\n".getBytes(StandardCharsets.UTF_8));
                    return;
                }

                if (database.containsKey(key)) {

                    val = database.get(key);

                    if (!"STRING".equals(val.getType())) {
                        dos.write("-ERR cannot increment non integer\r\n".getBytes());
                        return;
                    } else {
                        res = Integer.parseInt(val.getValue());
                        res++;
                        val.setValue(String.valueOf(res));


                    }


                } else {

                    val = new Value();
                    val.setType("STRING");
                    res++;
                    val.setValue(String.valueOf(res));
                    database.put(key,val);

                }
            } finally {
                keyLock.unlock();
            }

        } catch (Exception e) {
            dos.write("-ERR cannot increment non integer\r\n".getBytes());
            return;
        }

        dos.write((":" + res + "\r\n").getBytes());


    }

    public void commandDecrement(String command, DataOutputStream dos) throws IOException {


        String[] data = command.split(" ");

        int res = 0;

        String key = "";

        Value val = null;

        if(data.length != 2){
            dos.write("-ERR invalid command execution\r\n".getBytes());
            return;
        }

        key =  data[1];

        try {

            ReentrantLock keyLock = getLock(key);

            keyLock.lock();
            try {
                if (checkExpiry(key)) {
                    expiryCache.remove(key);
                    dos.write("+nil\r\n".getBytes(StandardCharsets.UTF_8));
                    return;
                }

                if (database.containsKey(key)) {

                    val = database.get(key);

                    if (!"STRING".equals(val.getType())) {
                        dos.write("-ERR cannot decrement non integer\r\n".getBytes());
                        return;
                    } else {
                        res = Integer.parseInt(val.getValue());
                        res--;
                        val.setValue(String.valueOf(res));


                    }


                } else {

                    val = new Value();
                    val.setType("STRING");
                    res--;
                    val.setValue(String.valueOf(res));
                    database.put(key,val);

                }
            } finally {
                keyLock.unlock();
            }

        } catch (Exception e) {
            dos.write("-ERR cannot decrement non integer\r\n".getBytes());
            return;
        }

        dos.write((":" + res + "\r\n").getBytes());


    }


    public void commandDelete(String command, DataOutputStream dos) throws IOException {

        String[] data = command.split(" ");

        String key = "";

        int count = 0;

        if(!(data.length >= 2)){
            dos.write("-ERR no keys received\r\n".getBytes());
            return;
        }


        for(int i = 1; i < data.length; i++){


            key = data[i];
            ReentrantLock keyLock = getLock(key);

            keyLock.lock();
            try {
                if (database.containsKey(key)) {
                    database.remove(key);
                    expiryCache.remove(key);
                    count++;
                }
            } finally {
                keyLock.unlock();
            }


        }


        dos.write((":" + count + "\r\n").getBytes());

    }

    public void commandLRange(String command, DataOutputStream dos) throws IOException {

        String[] data = command.split(" ");

        StringBuilder builder = new StringBuilder();

        String res = "";

        LinkedList<String> list = new LinkedList<>();

        String key = "";

        Value val = null;

        int start = 0;

        int end = 0;

        int arrLen = 0;

        //the command list does not include the correct args so return an error
        if(data.length != 4){
            dos.write("-ERR invalid command execution\r\n".getBytes());
            return;
        }

        key =  data[1];

        try {

            ReentrantLock keyLock = getLock(key);

            keyLock.lock();
            try {


                if (!database.containsKey(key)) {
                    dos.write(("*0\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    return;
                } else {

                    val = database.get(key);

                    if (!"LIST".contains(val.getType())) {
                        dos.write("-ERR value must be of list type\r\n".getBytes(StandardCharsets.UTF_8));
                        return;
                    }

                    start = Integer.parseInt(data[2]);

                    end = Integer.parseInt(data[3]);

                    list = val.getList();


                    // Redis allows negative numbers to point to items in their lists
                    // e.g. -1 = last element in list
                    // however this wont work for java as using -1 to get an element will cause an exception
                    // so we use modular arithmetic to transform the negative number to the positive index that points to
                    // the element that the negative number would in redis
                    // e.g. if the list is of size 6 and we passed -1 (last element of list) if we did -1 mod 6
                    // we would get 5 which is the positive index that points to the last element
                    // this calculation will prevent errors.

                    if (start < 0) {
                        start = Math.floorMod(start, list.size());
                    }
                    if (end < 0) {
                        end = (Math.floorMod(end, list.size()));
                    }

                    // range checks
                    if (start > list.size()) {
                        dos.write("*0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                        return;
                    }
                    if (end >= list.size()) {
                        end = (list.size() - 1);
                    }

                    //calculate length of string
                    // as the end is inclusive we add 1 to get the accurate length
                    arrLen = ((end - start) + 1);

                    //set the len of array for response
                    builder.append('*')
                            .append(arrLen)
                            .append('\r')
                            .append('\n');

                    for (int i = start; i <= end; i++) {
                        builder.append('$')
                                .append(list.get(i).length())
                                .append('\r')
                                .append('\n')
                                .append(list.get(i))
                                .append('\r')
                                .append('\n');
                    }

                    res = builder.toString();

                }
            } finally {
                keyLock.unlock();
            }




        }  catch (Exception e){

            res = "-ERR something went wrong\r\n";
            e.printStackTrace();
            dos.write(res.getBytes());

        }

        dos.write(res.getBytes());

    }

    //Longest Common Subsequence
    public void commandLCS(String command, DataOutputStream dos) throws IOException {


        String[] data = command.split(" ");

        if(!(data.length == 3)){

            dos.write("-ERR invalid command input".getBytes());
            return;
        }

        AtomicInteger res = new AtomicInteger(0);

        AtomicReference<String> key1 = new AtomicReference<>("");

        AtomicReference<String> key2 = new AtomicReference<>("");

        key1.set(data[1]);

        key2.set(data[2]);


        database.compute(key1.get(),(k,v) -> {

            if(v == null){
                return null;
            }

            Value v2 = database.get(key2.get());

            if(!"STRING".equals(v.getType()) && !"STRING".equals(v2.getType())){
                return null;
            }

            String s1 = v.getValue();

            String s2 = v2.getValue();

            int s1len = s1.length();

            int s2len = s2.length();

            int[][] lcs = new int[s1len + 1][s2len + 1];

            for (int i = 1; i <= s1len ; i++) {
                for(int j = 1; j <= s2len; j++){

                    if(s1.charAt(i - 1) == s2.charAt(j-1)){

                        lcs[i][j] = lcs[i-1][j-1] + 1;
                    } else {
                        lcs[i][j] = Math.max(lcs[i-1][i],lcs[i][j-1]);
                    }

                }

            }

            res.set(lcs[s1len][s2len]);

            return v;
        });


        dos.write((":" + res.get() + "\r\n").getBytes());

    }

    public void commandSave(DataOutputStream dos) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();

        String res = "";

        //For DB
        try(FileOutputStream fos = new FileOutputStream("DB.db")){

            objectMapper.writeValue(fos, database);



        } catch (Exception e){
            res = "-ERR something went wrong\r\n";
            dos.write(res.getBytes());
            e.printStackTrace();
        }

        //For expiry Cache
        try(FileOutputStream fos = new FileOutputStream("EXCACHE.db")){

            objectMapper.writeValue(fos, expiryCache);



        } catch (Exception e){
            res = "-ERR something went wrong\r\n";
            dos.write(res.getBytes());
            e.printStackTrace();
        }

        res = "+OK\r\n";
        dos.write(res.getBytes());

    }

    //Insert values to head of list
    public void commandLPUSH(String command, DataOutputStream dos) throws IOException {
        String[] data = command.split(" ");

        AtomicReference<String> res = new AtomicReference<>("");

        String key = "";

        Value val = null;

        LinkedList<String> list = null;

        if(!(data.length > 2)){
            res.set("-ERR invalid command\r\n");
            dos.write(res.get().getBytes());
            return;
        }

        key = data[1];


        ReentrantLock keyLock = getLock(key);

        keyLock.lock();

        try {
            //If key does not exist, create it and set new value
            if (!database.containsKey(key)) {

                val = new Value();
                val.setType("LIST");
                list = new LinkedList<>();

                for (int i = 2; i < data.length; i++) {

                    list.addFirst(data[i]);

                }

                val.setList(list);

                database.put(key, val);

            } else {

                val = database.get(key);

                if (!"LIST".equals(val.getType())) {
                    dos.write("-ERR value must be of LIST type\r\n".getBytes(StandardCharsets.UTF_8));
                    return;
                }

                list = val.getList();

                for (int i = 2; i < data.length; i++) {
                    list.addFirst(data[i]);
                }

                val.setList(list);

            }
        } finally {
            keyLock.unlock();
        }



        res.set(":" + list.size() + "\r\n");
        dos.write(res.get().getBytes());

    }

    //Insert values to tail of list
    public void commandRPUSH(String command, DataOutputStream dos) throws IOException {
        String[] data = command.split(" ");

        AtomicReference<String> res = new AtomicReference<>("");

        String key = "";

        Value val = null;

        LinkedList<String> list = null;

        if(!(data.length > 2)){
            res.set("-ERR invalid command\r\n");
            dos.write(res.get().getBytes());
            return;
        }

        key = data[1];


        ReentrantLock keyLock = getLock(key);

        keyLock.lock();

        try {
            //If key does not exist, create it and set new value
            if (!database.containsKey(key)) {

                val = new Value();
                val.setType("LIST");
                list = new LinkedList<>();

                for (int i = 2; i < data.length; i++) {

                    list.addLast(data[i]);

                }

                val.setList(list);

                database.put(key, val);

            } else {

                val = database.get(key);

                if (!"LIST".equals(val.getType())) {
                    dos.write("-ERR value must be of LIST type\r\n".getBytes(StandardCharsets.UTF_8));
                    return;
                }

                list = val.getList();

                for (int i = 2; i < data.length; i++) {
                    list.addLast(data[i]);
                }

                val.setList(list);

            }
        } finally {
            keyLock.unlock();
        }



        res.set(":" + list.size() + "\r\n");
        dos.write(res.get().getBytes());

    }

    public void commandGETRANGE(String command, DataOutputStream dos) throws IOException {

        System.out.println("hey");

        String[] data = command.split(" ");

        String key = "";

        Value val = null;

        String res = "";

        if(data.length != 4){
            dos.write("-ERR invalid arguments\r\n".getBytes(StandardCharsets.UTF_8));
            return;
        }

        key = data[1];

        ReentrantLock keyLock = getLock(key);

        keyLock.lock();

        try {
            if (!database.containsKey(key)) {
                dos.write("$0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            val = database.get(key);

            if(!"STRING".equals(val.getType())){
                dos.write("-ERR value must be of STRING type\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }


            String value = "";

            String temp = "";

            StringBuilder subStr = new StringBuilder();

            int start = Integer.parseInt(data[2]);

            int end = Integer.parseInt(data[3]);

            value = val.getValue();

            if(start < 0){
                start =  Math.floorMod(start,value.length());
            } else if(start > value.length()){
                dos.write("$0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            if(end < 0){
                end = Math.floorMod(end,value.length());
            } else if (end >= value.length()){
                System.out.println(end);
                end = value.length() - 1;
            }

            temp = value.substring(start, end + 1);

            subStr.append('$')
                    .append(temp.length())
                    .append('\r')
                    .append('\n')
                    .append(temp)
                    .append('\r')
                    .append('\n');

            res = subStr.toString();


        } catch (Exception e){

            res = "-ERR something went wrong\r\n";


        }
        finally {
            keyLock.unlock();
        }


        dos.write(res.getBytes(StandardCharsets.UTF_8));

    }

    public void commandLMOVE(String command, DataOutputStream dos) throws IOException {

        String[] data = command.split(" ");

        String res = "";

        if(data.length != 5){
            dos.write("-ERR invalid arguments\r\n".getBytes(StandardCharsets.UTF_8));
            return;
        }

        String srcKey = data[1];

        String destKey = data[2];

        //Where From LEFT/RIGHT for src
        String whereFrom = data[3];

        //Where To LEFT/RIGHT for dest
        String whereTo = data[4];

        String key1 = "";
        String key2 = "";

        ReentrantLock keyLock1 = null;
        ReentrantLock keyLock2 = null;



        //For alphabetical locking order to prevent deadlock
        int comp = srcKey.compareTo(destKey);

        if(comp == 0){

            keyLock1 = getLock(srcKey);

        } else {
             key1  = comp <= 0 ? srcKey  : destKey;
             key2 = comp <= 0 ? destKey : srcKey;

            keyLock1 = getLock(key1);
            keyLock2 = getLock(key2);
        }


        keyLock1.lock();
        if(keyLock2 != null)
            keyLock2.lock();

        try{

            if(!(database.containsKey(srcKey) && database.containsKey(destKey))){
                dos.write("$0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            Value v1 = database.get(srcKey);
            Value v2 = database.get(destKey);

            if(!("LIST".equals(v1.getType()) && "LIST".equals(v2.getType()))){
                dos.write("-ERR values must be of LIST type\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            LinkedList<String> src;

            LinkedList<String> dest;


            if(srcKey.equals(destKey)){
                src = v1.getList();
                dest = v1.getList();
            } else {
                src = v1.getList();
                dest = v2.getList();
            }

            String srcElem = "";

            StringBuilder builder = new StringBuilder();


            if("LEFT".equals(whereFrom)){
                srcElem = src.removeFirst();
            } else if ("RIGHT".equals(whereFrom)){
                srcElem = src.removeLast();
            } else {
                dos.write("-ERR invalid argument\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            if("LEFT".equals(whereTo)){
                dest.addFirst(srcElem);
            } else if ("RIGHT".equals(whereTo)){
                dest.addLast(srcElem);
            } else {
                dos.write("-ERR invalid argument\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            builder.append('$')
                    .append(srcElem.length())
                    .append('\r')
                    .append('\n')
                    .append(srcElem)
                    .append('\r')
                    .append('\n');

            res = builder.toString();

        } finally {
            keyLock1.unlock();
            if(keyLock2 != null)
                keyLock2.unlock();
        }


        dos.write(res.getBytes(StandardCharsets.UTF_8));

    }

    //multiple-element version of LMOVE
    public void commandLMOVEM(String command, DataOutputStream dos) throws IOException {

        String[] data = command.split(" ");

        String res = "";

        if(data.length != 5 && data.length != 8){
            dos.write("-ERR invalid arguments\r\n".getBytes(StandardCharsets.UTF_8));
            return;
        }


        AtomicBoolean hasOptional = new AtomicBoolean(data.length == 8);

        String srcKey = data[1];

        String destKey = data[2];

        //Where From LEFT/RIGHT for src
        String whereFrom = data[3];

        //Where To LEFT/RIGHT for dest
        String whereTo = data[4];

        String key1 = "",key2 = "";
        ReentrantLock keyLock1 = null, keyLock2 = null;

        //Alphabetical locking order for keys to prevent deadlock

        int comp = srcKey.compareTo(destKey);

        if(comp == 0){
            keyLock1 = getLock(srcKey);
        } else {

            key1 = comp < 0 ? srcKey : destKey;
            key2 = comp < 0 ? destKey : srcKey;

            keyLock1 = getLock(key1);
            keyLock2 = getLock(key2);
        }


        keyLock1.lock();
        if(keyLock2 != null)
            keyLock2.lock();

        try {

            if(!(database.containsKey(srcKey) && database.containsKey(destKey))){
                dos.write("$0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            Value v1 = database.get(srcKey);
            Value v2 = database.get(destKey);

            if(!("LIST".equals(v1.getType()) && "LIST".equals(v2.getType()))){
                dos.write("-ERR values must be of LIST type\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            LinkedList<String> src;

            LinkedList<String> dest;


            if(srcKey.equals(destKey)){
                src = v1.getList();
                dest = v1.getList();
            } else {
                src = v1.getList();
                dest = v2.getList();
            }

            if(hasOptional.get()){
                try {
                    //execute the LMOVEM functionality using optional arguments
                    res = executeOptional(src,dest,whereFrom,whereTo,data);
                    dos.write(res.getBytes(StandardCharsets.UTF_8));
                    return;
                } catch (Exception e){
                    dos.write("-ERR something went wrong\r\n".getBytes(StandardCharsets.UTF_8));
                    return;
                }
            }


            String srcElem = "";

            StringBuilder builder = new StringBuilder();


            if("LEFT".equals(whereFrom)){
                srcElem = src.removeFirst();
            } else if ("RIGHT".equals(whereFrom)){
                srcElem = src.removeLast();
            } else {
                dos.write("-ERR invalid argument\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            if("LEFT".equals(whereTo)){
                dest.addFirst(srcElem);
            } else if ("RIGHT".equals(whereTo)){
                dest.addLast(srcElem);
            } else {
                dos.write("-ERR invalid argument\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            v1.setList(src);
            v2.setList(dest);

            builder.append('*')
                    .append(1)
                    .append('\r')
                    .append('\n')
                    .append('$')
                    .append(srcElem.length())
                    .append('\r')
                    .append('\n')
                    .append(srcElem)
                    .append('\r')
                    .append('\n');

            res = builder.toString();

        } finally {

            keyLock1.unlock();

            if(keyLock2 != null)
                keyLock2.unlock();
        }




        dos.write(res.getBytes(StandardCharsets.UTF_8));

    }

    public void commandLLEN(String command, DataOutputStream dos) throws IOException{

        String[] data = command.split(" ");

        int res = 0;

        String key = "";

        Value val = null;

        if(data.length != 2){
            dos.write("-ERR incorrect command arguments\r\n".getBytes(StandardCharsets.UTF_8));
            return;
        }

        key = data[1];

        ReentrantLock keyLock = getLock(key);

        keyLock.lock();

        try {

            if(database.containsKey(key)){

                val = database.get(key);

                if(!"LIST".equals(val.getType()))
                {
                    dos.write("-ERR value must be of type LIST\r\n".getBytes(StandardCharsets.UTF_8));
                    return;
                }

                res = val.getList().size();
            }



        } finally {
            keyLock.unlock();
        }



        dos.write((":" + res + "\r\n").getBytes(StandardCharsets.UTF_8));


    }

    // Execution with optional args
    public String executeOptional(LinkedList<String> src,LinkedList<String> dest, String whereFrom,String whereTo,String[] data){

        String res = "";

        StringBuilder builder = new StringBuilder();

        ArrayList<String> elems = new ArrayList<>();

        String option = data[5];

        int by = Integer.parseInt(data[6]);

        boolean destOrderReversed = false;

        String order = data[7];

        if("COUNT".equals(option)){
            if(src.size() < by){
                by = src.size();
            }
        }

        if("EXACTLY".equals(option)){
            if(src.size() < by){
                return "$3\r\nnil\r\n";
            }
        }

        if("LEFT".equals(whereFrom)){
            for (int i = 0; i < by; i++) {
                String temp = src.removeFirst();
                elems.add(temp);
            }
        } else if("RIGHT".equals(whereFrom)){
            for (int i = 0; i < by; i++) {
                String temp = src.removeLast();
                elems.add(temp);
            }
        }

        //OBO == One By One
        if("OBO".equals(order)){

            if("LEFT".equals(whereTo)){
                for(String e : elems){
                    dest.addFirst(e);
                }
                destOrderReversed = true;
            } else if("RIGHT".equals(whereTo)){
                for(String e : elems){
                    dest.addLast(e);
                }
            }
        }

        if("BULK".equals(order)){
            if("LEFT".equals(whereTo)){

                dest.addAll(0,elems);

            } else if("RIGHT".equals(whereTo)){
                dest.addAll(elems);
            }
        }
        builder.append('*')
                .append(elems.size())
                .append("\r\n");

        // For when we get the condition LEFT + OBO, this reads the elems list in reverse so that the correct
        // destination order is returned
        if(destOrderReversed){


            for(int i = elems.size() - 1; i >= 0; i--){
                builder.append('$')
                        .append(elems.get(i).length())
                        .append("\r\n")
                        .append(elems.get(i))
                        .append("\r\n");

            }

        } else {

            for(String e : elems){
                builder.append('$')
                        .append(e.length())
                        .append("\r\n")
                        .append(e)
                        .append("\r\n");

            }
        }



        res = builder.toString();
        return res;
    }

    //Expiry Functions
    private void setExpiry(String key, long expireTime, String option) throws IOException {


        //Calc when key will expire and save the expiry option with that time
        AtomicReference<String> expire = new AtomicReference<>("");




        //Add the key and the expiry time to cache
        expiryCache.compute(key, (k,v) -> {

            //sets expiry time based on option
            if("EX".equals(option)) {
                expire.set("EX:" + ((System.currentTimeMillis() / 1000) + expireTime));
            } else if("PX".equals(option)) {

                expire.set("PX:" + (System.currentTimeMillis() + expireTime));

            } else if ("EXAT".equals(option)) {

                LocalDate date = LocalDate.now();
                Instant instant  = date.atTime(LocalTime.now()).atZone(ZoneId.of("UTC")).toInstant();
                expire.set("EXAT:" + (instant.getEpochSecond() + expireTime));

            } else if("PXAT".equals(option)) {
                LocalDate date = LocalDate.now();
                Instant instant  = date.atTime(LocalTime.now()).atZone(ZoneId.of("UTC")).toInstant();
                expire.set("PXAT:" + (instant.toEpochMilli() + expireTime));
            }


            return expire.get();

        });

    }

    private boolean checkExpiry(String key) {

        String expire = expiryCache.get(key);
        if (expire == null) {
            return false;
        }
        String[] data = expire.split(":");

        if ("EX".equals(data[0])) {

            return (Long.parseLong(data[1])) < (System.currentTimeMillis() / 1000);

        } else if ("PX".equals(data[0])) {

            return (Long.parseLong(data[1])) < (System.currentTimeMillis());

        } else if ("EXAT".equals(data[0])) {

            LocalDate date = LocalDate.now();
            Instant instant = date.atTime(LocalTime.now()).atZone(ZoneId.of("UTC")).toInstant();
            return (Long.parseLong(data[1])) < instant.getEpochSecond();

        } else if ("PXAT".equals(data[0])) {

            LocalDate date = LocalDate.now();
            Instant instant = date.atTime(LocalTime.now()).atZone(ZoneId.of("UTC")).toInstant();
            return (Long.parseLong(data[1])) < instant.toEpochMilli();
        }


        return false;
    }

    private boolean containsExpiryOption(String option){
        return "EX".equals(option) || "PX".equals(option) || "EXAT".equals(option) || "PXAT".equals(option);
    }


    //Assigns a lock to a key, is persistent throughout the server
    private ReentrantLock getLock(String key){

        return locks.computeIfAbsent(key,(k -> new ReentrantLock()));
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
                commandExecutor(command, out);

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

    //Handler Methods


    public static void main(String[] args) throws IOException {

        Server server = new Server(6379);

    }

}
