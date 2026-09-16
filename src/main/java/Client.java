

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ThreadPoolExecutor;

public class Client {


    private Socket socket;
    private BufferedReader in;
    private DataInputStream dis;
    private DataOutputStream out;

    public Client(String address, int port) throws IOException {

        socket = new Socket(address,port);

        in = new BufferedReader(new InputStreamReader(System.in));

        out = new DataOutputStream(socket.getOutputStream());

        dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        System.out.println("Client Started");


        System.out.print("redis-cli ");
        String readLine = "";


        while(!readLine.equals("STOP")){

            try {

                //Reads user command
                readLine = in.readLine();

                //Serializes this data using RESP protocol and writes it to socket
                out.write(serializer(readLine));

                if(!readLine.equals("STOP")) {
                    //Wait for response from server
                    System.out.println(deserializer(dis));
                }
            } catch (SocketException e) {
                e.printStackTrace();
                break;
            }

        }

        System.out.println("Client Disconnecting...");

        try{
            in.close();
            out.close();
            socket.close();
        } catch (IOException e){
            e.printStackTrace();
        }

        System.out.println("Client Disconnected");

    }

    public byte[] serializer(String clientCommands) throws IOException {

        //For now

        ByteArrayOutputStream out = new ByteArrayOutputStream();


        if(clientCommands.isEmpty()){

            out.write("-ERR no commands given\r\n".getBytes());
            return out.toByteArray();
        }

        char cr = '\r';

        char lf = '\n';

        String[] commands = clientCommands.split(" ");


        char commandLen = Character.forDigit(commands.length,10);


        out.write('*');
        out.write(commandLen);
        out.write(cr);
        out.write(lf);

        for (String command : commands) {

            out.write('$');
            out.write(Character.forDigit(command.length(),10));
            out.write(cr);
            out.write(lf);
            for(char c : command.toCharArray()){
                out.write(c);
            }
            out.write(cr);
            out.write(lf);



        }

        return out.toByteArray();
    }

    public String deserializer(InputStream in) throws IOException {

        String deserializedResponse = "";

        char type = (char) in.read();


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
            return "ERR invalid type/no type given";
        }

        return deserializedResponse;
    }

    private String deserializeSimpleString(InputStream input) throws IOException {


        StringBuilder deserializedResponse = new StringBuilder();

        int readByte = 0;

        while ((readByte = input.read()) != '\r') {

            if ((readByte == -1)) {
                return "ERR client timed-out";
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
                return "ERR client timed-out";
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

            if ((readByte == '.')) {
                return "ERR no decimal separator";
            }

            if ((readByte == -1)) {
                return "ERR client disconnected";
            }

            if ((readByte == '\n')) {
                return "ERR '\\n' or '\\r' cannot exist in message";
            }

            if (Character.isLetter((char) readByte)) {
                return "ERR integer cannot contain string";
            }

            if (readByte != ':') {
                deserializedResponse.append((char) readByte);
            }
        }

        readByte = input.read();

        //If no numbers could be parsed return error
        if (deserializedResponse.toString().isEmpty()) {
            return "ERR no number";
        }

        if (readByte != '\n') {
            return "ERR incorrect termination command";
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
                return "ERR client disconnected";
            }
            if ((readByte == '\n')) {
                return "ERR '\\n' or '\\r' cannot exist in message";
            }

            if (Character.isDigit((char) readByte) || readByte == '-') {
                ln.append((char) readByte);
            }

            if (Character.isLetter((char) readByte)) {
                return "ERR integer cannot contain string";
            }
        }


        if (ln.toString().isEmpty()) {
            return "ERR no length given";
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
                return "ERR client disconnected/No Termination Command";
            }
            if ((readByte == '\n')) {
                return "ERR '\\n' or '\\r' cannot exist in message";
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
                return "ERR client disconnected";
            }
            if ((readByte == '\n')) {
                return "ERR '\\n' or '\\r' cannot exist in message";
            }

            if (Character.isDigit((char) readByte) || readByte == '-') {
                ln.append((char) readByte);
            }
        }

        readByte = input.read();

        if (ln.toString().isEmpty()) {
            return "ERR no length found";
        }

        if (readByte != '\n') {
            return "ERR incorrect termination command";
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

            } else {
                return "ERR no valid type found/Arr Len > Num of Elements";
            }


            if ((i + 1) != arrLen) {
                deserializedResponse.append(" ");
            }


        }

        return deserializedResponse.toString();
    }

    public static void main(String[] args) throws IOException {




        Client client = new Client("127.0.0.1",6379);

    }

}
