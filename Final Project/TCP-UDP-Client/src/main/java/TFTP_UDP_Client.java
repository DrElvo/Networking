
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.Scanner;

public class TFTP_UDP_Client {
    /**
     * The main method, which determines which type of request is to be sent
     * @param args args is the host name, determined automatically
     * @throws IOException throws an exception in the case the above is not done, and means the scanners dont have to be in separate try catch blocks
     */
    public static void main(String[] args) throws IOException {
        String hostname = "TFTP-UDP-CLIENT";
        args = hostname.split(" ");
        int min = 500;
        int offset = 4001;
        int portNum = new Random().nextInt(min) + offset; //Makes the random port number between 0-500 + the offset
        if (args.length != 1) {
            System.out.println("the hostname of the server is required");
            return;
        }
        Scanner input = new Scanner(System.in); //starts a scanner for the input of request type
        System.out.println("Enter 1 to read from the server, 0 to write to the server:");
        String choice = input.nextLine(); //takes the input from the user
        if (choice.equals("1")) { //Starts the read from method passing in the port num
            readFromServer(portNum);
        } else if (choice.equals("0")) { //Starts the write to method passing in the port num
            writeToServer(portNum);
        } else {
            System.out.println("Invalid choice."); //breaks out if an invalid request is made
        }
    }

    /**
     * This method writes to the server on the port number provided, it gets the port number and address of the server in the reply to its request.
     * @param portNum the port number is the randomly generated port number as seen above, between 4000 and 4500. This means that other clients can run on different ones without incrementing or having this as a handler the way the server is
     * @throws IOException Throws an IO exception rather than having more try catch blocks
     */
        public static void writeToServer(int portNum) throws IOException{

        // BEGIN FILE SETUP

        boolean fileFound = false;
        Scanner SCN = new Scanner(System.in); // new scanner for the user filename input
        String fileName = null;
        String subFolderName = "ClientSide";
        File subfolder = new File(subFolderName); // Makes the directory ClientSide if it doesn't exist already, though it should considering it has to get a filename from it later.
            if (!subfolder.exists()) {
                subfolder.mkdir();
            }
            while (!fileFound) { //starts a loop which requests for the user to put in a filename, if it doesn't exist it repeats.
                System.out.println("Enter the file name of the file you want to write to the server");
                fileName = SCN.nextLine(); //takes the input of the client for filename
                File file = new File(subFolderName + "/" + fileName);
                try {
                    Scanner SCN2 = new Scanner(file); //starts a new scanner to read in the file, reading it out to the user
                    while (SCN2.hasNextLine()) {
                        String line = SCN2.nextLine();
                        System.out.println(line);
                    }
                    SCN2.close();
                    fileFound = true;
                } catch (FileNotFoundException e) {//if the scanner cannot be made, then it catches as the file doesnt exist
                    System.out.println("file not found");
                }
            }

            FileInputStream fileInputStream = new FileInputStream(subFolderName + "/" + fileName); //because the filename exists, the input stream is made using the filename and the folder it should be in
            byte[] fileData = new byte[fileInputStream.available()]; //the new byte array for the file data
            fileInputStream.read(fileData); //puts the data into the data array
            fileInputStream.close();    // closes the input stream

            // END OF FILE SETUP

            // BEGIN SOCKET SETUP

            DatagramSocket clientSendSocket;
            clientSendSocket = new DatagramSocket(portNum); //establishes the new socket on the port number generated
            clientSendSocket.setSoTimeout(500); //sets up the timeout
            InetAddress initialSendToAddress = InetAddress.getLocalHost(); //gets the local address

            //InetAddress initialSendToAddress = InetAddress.getByName("10.108.1.187"); //THIS IS MY IP, PLEASE CHANGE IT TO MAKE IT WORK ON OTHER DEVICES

            // END SOCKET SETUP

            // BEGIN ASSEMBLY OF ACK PACKET

            int initialSendToPort = 69; //this is the port the server is running on
            int maxAckLen = 4;
            byte[] replyByteArray = new byte[maxAckLen];
            DatagramPacket replyPacket = new DatagramPacket(replyByteArray, maxAckLen); //creates a new packet for acks, which are 4 bytes long including the opcode and the block number its acknowledging

            // END ASSEMBLY OF ACK PACKET

            // BEGIN ASSEMBLY OF REQUEST PACKET

            int byteArrayLength = 516; //The file data length/request packet length
            int maxFileLength = 255; //The max length of the file name
            String mode = "octet"; //The file transfer mode as defined in the guidelines
            byte[] byteArrayWRQ = new byte[byteArrayLength];
            byteArrayWRQ[0] = 0;
            byteArrayWRQ[1] = 2;
            byteArrayWRQ[2] = 0;
            byteArrayWRQ[3] = 0; // This assembles the request packet using the above byte array of length 516
            byte[] filenameBytes = fileName.getBytes(); // the byte array of the filename
            byte[] modeBytes = mode.getBytes(); //the byte array of the mode
            System.arraycopy(filenameBytes, 0 , byteArrayWRQ, 2 , fileName.length()); //THIS PUTS THE FILENAME INTO THE REQUEST

            // BEGIN PADDING OF FILENAME

            byteArrayWRQ[fileName.length() + 2] = 0; //sets the 0 byte to know when to start looking for the mode

            // END PADDING OF FILENAME

            System.arraycopy(modeBytes, 0 , byteArrayWRQ, 2 + fileName.length() + 1 , mode.length()); //THIS PUTS THE MODE INTO THE REQUEST

            DatagramPacket request = new DatagramPacket(byteArrayWRQ,byteArrayLength); // INITIALISES THE NEW REQUEST PACKET
            request.setAddress(initialSendToAddress);
            request.setPort(initialSendToPort);
            clientSendSocket.send(request); // SEND REQUEST PACKET

            clientSendSocket.receive(replyPacket); // RECEIVE ACK ON NEW PORT

            int sendToPort = replyPacket.getPort(); // SAVES ITS NEW PORT
            InetAddress sendToAddress = replyPacket.getAddress(); // SAVES ITS NEW ADDRESS JUST IN CASE


            // BEGIN DATA TRANSFER

            int bytesSent = 0;
            int blockNumber = 1;
            boolean doneFinale = false; // ESTABLISHES BREAK CONDITIONS

            if(fileData.length == 0){ //THIS IS FOR THE EDGE CASE OF AN EMPTY FILE
                bytesSent-=1;
            }

            while (bytesSent <= fileData.length && !doneFinale) { //THIS IS THE LOOP WHICH RECEIVED DATA, UNDER THE CONDITION THAT THE LENGTH ISNT LESS THAN 516 AND THE FINALE CHECK HASNT BE DONE
                if(fileData.length == 0){ //UNDOES THE CONDITION REQUIRED TO GET IT INTO THE LOOP
                    bytesSent +=1;
                }
                int bytesRemaining = fileData.length - bytesSent; //ESTABLISHES THE REMAINING BYTES
                int currentPacketSize = Math.min(byteArrayLength, bytesRemaining + 4); //CHOOSES THE SHORTEST TO ALLOW A CHANGE IN BYTE ARRAY LENGTH
                byte[] currentPacketData = new byte[currentPacketSize]; //CREATES THE BYTE ARRAY LENGTH, THIS SHOULD ALWAYS BE 516 AS CHOSEN ABOVE OTHER THAN THE LAST PACKET

                //  BEGIN HEADER CONSTRUCTION

                currentPacketData[0] = 0;
                currentPacketData[1] = 3;
                currentPacketData[2] = (byte) ((blockNumber >> 8) &255);
                currentPacketData[3] = (byte) (blockNumber & 255); // THIS ESTABLISHES THAT THE PACKET IS A DATA PACKET AND THE BLOCK CODE IS SENT ALONG WITH IT

                // END HEADER CONSTRUCTION

                System.arraycopy(fileData, bytesSent, currentPacketData, 4 , (currentPacketSize - 4)); // COPIES THE DATA TO THE NEW PACKET MISSING OFF THE 4 CODE BYTES
                DatagramPacket dataPacket = new DatagramPacket(currentPacketData, currentPacketSize);
                dataPacket.setAddress(sendToAddress);
                dataPacket.setPort(sendToPort); // CREATES AND SETS THE PORT AND ADDRESS OF THE DATA PACKET, THE PORT IS THE SAME AS THAT TAKEN FROM THE REPLY PACKET

                // BEGIN ACK OF DATA PACKET

                boolean acked = false; //CREATES THE CONDITIONS TO CHECK FOR PACKAGE ACKNOWLEDGEMENT
                int count = 0;
                while(!acked) { //ENTERS A LOOP THAT RESENDS THE DATA AND BREAKS IF IT SENDS THE DATA MORE THAN 5 TIMES
                    if(count > 5){
                        System.out.println("Assume connection lost");
                        break; // BREAKS OUT ASSUMING THE CONNECTION IS LOST
                    }
                    clientSendSocket.send(dataPacket); // SENDS THE DATA PACKET
                    try {
                        clientSendSocket.receive(replyPacket); // RECEIVES THE REPLY
                    }catch (SocketTimeoutException e){
                        System.out.println("receive timeout"); //COUNTS TO 5 AND CONTINUES AFTER EACH COUNT OF 1
                        count+=1;
                        continue;
                    }
                    byte[] received = replyPacket.getData();
                    int receivedBlockNumber = ((received[2] & 255) << 8) | (received[3] & 255); //MAKES THE RECEIVED BLOCK NUMBER FROM THE ACK
                    if(blockNumber == receivedBlockNumber){ //CHECKS THE BLOCK NUMBER, IF ITS THE SAME IT PASSES OTHERWISE IT WILL RESEND
                        acked = true;
                        bytesSent += (currentPacketSize - 4);
                    }
                    if(bytesRemaining == 0 && (currentPacketData.length != 512)){
                        doneFinale = true; //THIS IS THE FINALE CHECK TO ENSURE ALL THE DATA HAS BEEN SENT AND IT IS NOT AN EDGE CASE
                    }
                }

                // END ACK OF DATA PACKET

                blockNumber++; //INCREMENTS UP THE BLOCK NUMBER
            }

            // END DATA TRANSFER

            clientSendSocket.close(); //CLOSES THE SOCKET

    }

    /**
     * THIS IS THE METHOD THAT READS FROM THE SERVER, IT CHECKS IT DOESN'T GET AN ERROR PACKET OTHERWISE IT IS MAINLY THE SAME AS ABOVE HOWEVER IN REVERSE
     * @param portNum THE RANDOM PORT NUMBER GENERATED BY THE CONSTRUCTOR
     * @throws IOException REMOVES SOME NEED FOR TRY CATCH BLOCKS
     */
    public static void readFromServer(int portNum) throws IOException{
        Scanner SCN = new Scanner(System.in); //NEW SCANNER FOR THE FILENAME
        String fileName = null;
        System.out.println("Enter the file name of the file you want to get from the server");
        fileName = SCN.nextLine(); //GETS THE FILENAME FROM THE USER
        File file = new File(fileName);
        DatagramSocket socket;
        socket = new DatagramSocket(portNum); //ESTABLISHES SOCKET AND TIMEOUT BELOW
        socket.setSoTimeout(500);
        InetAddress address = InetAddress.getLocalHost();
        //InetAddress address = InetAddress.getByName("10.108.1.187"); //THIS IS MY IP, PLEASE CHANGE IT TO MAKE IT WORK ON OTHER DEVICES
        int port = 69;
        int maxAckLen = 4;
        byte[] replyByteArray = new byte[maxAckLen];
        DatagramPacket replyPacket = new DatagramPacket(replyByteArray, maxAckLen); //CREATES THE ACK PACKET
        int byteArrayLength = 516;
        int maxFileLength = 255;
        String mode = "octet";
        byte[] byteArrayWRQ = new byte[byteArrayLength];
        byteArrayWRQ[0] = 0;
        byteArrayWRQ[1] = 1;
        byteArrayWRQ[2] = 0;
        byteArrayWRQ[3] = 0;
        byte[] filenameBytes = fileName.getBytes(); //CHANGES THE FIRST 4 BYTES TO THE PACKET TO BE THAT OF THE REQUEST
        byte[] modeBytes = mode.getBytes();
        System.arraycopy(filenameBytes, 0 , byteArrayWRQ, 2 , fileName.length());
        byteArrayWRQ[filenameBytes.length + 2] = 0;
        System.arraycopy(modeBytes, 0 , byteArrayWRQ, 2 + filenameBytes.length + 1 , mode.length()); //COPIES IN THE MODE, AND ABOVE IS THE FILENAME
        DatagramPacket request = new DatagramPacket(byteArrayWRQ,byteArrayLength);
        request.setAddress(address);
        request.setPort(port);

        socket.send(request); //SENDS THE REQUEST AFTER SETTING THE PORT AND ADDRESS

        boolean done = false;
        int dataPacketSize = 516;
        byte[] dataPacketData = new byte[dataPacketSize];
        byte[] actualDataPacketData = null;
        int actualDataPacketSize;
        DatagramPacket mainpacket = new DatagramPacket(dataPacketData, dataPacketSize); //ESTABLISHES THE NEW PACKET OF DATA IT WILL RECIEVE
        int writtenBlockNumber = 0;
        FileOutputStream fileOut = null;
        replyPacket.setAddress(address);
        replyPacket.setPort(port); //SETS UP THE REPLY
        boolean createdOutput = false;

        while(!done) { //STARTS THE WHILE LOOP TO WRITE DATA
            try {
                try {
                    socket.receive(mainpacket); //GETS THE FIRST DATA PACKET FROM THE SERVER
                }catch (SocketTimeoutException e){
                    socket.send(replyPacket);
                    continue;
                }
                actualDataPacketData = mainpacket.getData();

                // ERROR OF REQUEST

                if(actualDataPacketData[1] == 5) { //CHECKS THE PACKET ISNT AN ERROR AND SAYS IF IT IS AND THE TYPE
                    int errorMessageLength = actualDataPacketData.length;
                    int zeroByte = -1;
                    for (int i = 3; i < errorMessageLength; i++) {
                        if (actualDataPacketData[i] == 0) {
                            zeroByte = i;
                            break;
                        }
                    }
                    String errorMessage = (zeroByte == -1) ?
                            new String(actualDataPacketData) : new String(actualDataPacketData, 4, zeroByte - 4);
                    System.out.println(errorMessage);
                    done = true;
                    continue;
                }else if(!createdOutput) {
                    File subfolder = new File("ClientSide"); //Declaration of name clientside
                    if (!subfolder.exists()) {//In case the folder "clientSide" doesn't exist it will make it
                        subfolder.mkdir();    //Makes the directory
                    }
                    File outputFile = new File(subfolder, fileName); //Creates the fileoutput if the error message for the file not existing on the other side doesn't come through
                    fileOut = new FileOutputStream(outputFile);
                    createdOutput = true;
                }
                actualDataPacketSize = mainpacket.getLength();
                address = mainpacket.getAddress();
                port = mainpacket.getPort(); //GETS THE RANDOM PORT NUMBER AND ADDRESS THE REPLY CAME FROM
                replyByteArray = new byte[maxAckLen];
                int recievedBlockNumber = ((actualDataPacketData[2] & 255) << 8) | (actualDataPacketData[3] & 255);
                replyByteArray[0] = 0;
                replyByteArray[1] = 4;
                replyByteArray[2] = (byte) ((recievedBlockNumber >> 8) &255);
                replyByteArray[3] = (byte) (recievedBlockNumber& 255); //BUILDS THE REPLY AND ECHOS THE BLOCK NUMBER
                if(writtenBlockNumber >= recievedBlockNumber){
                    continue;
                }
                writtenBlockNumber = ((replyByteArray[2] & 255) << 8) | (replyByteArray[3] & 255);
                fileOut.write(actualDataPacketData, 4, (actualDataPacketSize - 4));
                replyPacket = new DatagramPacket(replyByteArray,maxAckLen);
                replyPacket.setPort(port);
                replyPacket.setAddress(address); //ASSIGNS THE RANDOM PORT NUMBER TO SEND THE ACK BACK TO
                try {
                    socket.send(replyPacket); //SENDS ITS REPLY
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(actualDataPacketSize < 516){
                    if(actualDataPacketSize == 4){
                        done = true; //CHANGES THE CONDITION TO ALLOW THE WRITING TO END
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        socket.close();


    }

}
