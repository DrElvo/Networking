import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class TFTP_UDP_SERVER_WRITING extends Thread {
    private DatagramSocket DedSoc = null;
    private DatagramPacket RecPac;
    private int MyPort;
    InetAddress inetAddress = InetAddress.getLocalHost();

    /**
     * CONSTRUCTS THE NEW CLASS TO HANDLE THE WRITING
     * @param TransPort //THIS IS THE RANDOM PORT NUMBER THAT WAS CREATED
     * @param TransPac //THIS IS THE PACKET TAKEN OVER FROM THE REQUEST
     * @throws IOException
     */
    public TFTP_UDP_SERVER_WRITING(int TransPort, DatagramPacket TransPac) throws IOException {
        RecPac = TransPac;
        MyPort = TransPort;
        DedSoc = new DatagramSocket(MyPort, inetAddress);
        DedSoc.setSoTimeout(500); //ESTABLISHES A NEW SOCKET AND ITS TIMEOUT UNDER THE NEW PORT NUMBER MADE
    }

    /**
     * THIS IS THE MAIN THREAD THAT ALLOWS THE SERVER TO KEEP RUNNING
     */
    @Override
    public void run(){
        try{
        int maxFileNameLen = 255;
        int maxDataFileLen = 516;
        int bytesSent = 0;
        int blockNumber = 1;
        int maxAckLen = 4;
        byte[] replyByteArray = new byte[maxAckLen];
        DatagramPacket replyPacket = new DatagramPacket(replyByteArray, maxAckLen); //CREATES THE NEW DATAGRAM PACKET FOR THE DATA
        replyPacket.setAddress(RecPac.getAddress());
        replyPacket.setPort(RecPac.getPort()); //SETS UP THE PORT INFORMATION BASED ON THE REQUEST PORT
        byte[] fileNameByteArray = new byte[maxFileNameLen];
        byte[] recData = RecPac.getData();
        System.arraycopy(recData, 2, fileNameByteArray, 0, maxFileNameLen);
        int zeroByte = -1;
        for (int i = 0; i < maxFileNameLen; i++) {
            if (fileNameByteArray[i] == 0) {
                zeroByte = i;
                break;
            } //READS THE FILE NAME UP TO THE FIRST 0 AND THEN SAVES THE BYTE THAT IS THE 0 BYTE
        }
        String fileName = (zeroByte == -1) ?
            new String(fileNameByteArray) : new String(fileNameByteArray, 0, zeroByte); //GETS THE FILENAME FROM REQUEST PACKET AND KNOWING THE 0 BYTE
            System.out.println(fileName);
        try {
            String subFolderName = "ServerSide";
            File subfolder = new File(subFolderName);
            if (!subfolder.exists()) {
                subfolder.mkdir(); //TRYS TO MAKE THE DIRECTORY SERVERSIDE IF IT DOESN'T EXIST TO WRITE THE FILE INTO IT
            }
            fileName = subFolderName + "/" + fileName;
            FileInputStream fileInputStream = new FileInputStream(fileName); //CREATES THE FILE INPUT STREAM FOR THE FILE IT IS ABOUT TO RECEIVE
            byte[] fileData = new byte[fileInputStream.available()];
            fileInputStream.read(fileData);
            fileInputStream.close();
            boolean doneFinale = false;
            while (bytesSent <= fileData.length && !doneFinale) { //STARTS THE WHILE LOOP THAT SENDS DATA UNTIL THE BYTES SENT IS GREATER THAN THE FILE LENGTH
                int bytesRemaining = fileData.length - bytesSent;
                int currentPacketSize = Math.min(maxDataFileLen, bytesRemaining + 4);
                byte[] currentPacketData = new byte[currentPacketSize];
                currentPacketData[0] = 0;
                currentPacketData[1] = 3;
                currentPacketData[2] = (byte) ((blockNumber >> 8) &255); //SENDS THE BLOCK NUMBER AND THE DATA OP CODE
                currentPacketData[3] = (byte) (blockNumber &255);
                System.arraycopy(fileData, bytesSent, currentPacketData, 4 , (currentPacketSize - 4));
                DatagramPacket dataPacket = new DatagramPacket(currentPacketData, currentPacketSize); //CREATES THE NEW DATA PACKET TO SEND USING THE BYTE ARRAY OF FILE DATA
                dataPacket.setAddress(replyPacket.getAddress());
                dataPacket.setPort(replyPacket.getPort()); //SETS UP THE PORT AND ADDRESS FOR THE PACKET
                boolean acked = false;
                int count = 0;
                while(!acked) { //INITIATES A WHILE LOOP TO ACKNOWLEDGE DATA
                    if(count > 5){
                        System.out.println("timeout more than 5, breaking");
                        acked = true;
                    }
                    DedSoc.send(dataPacket);
                    try {
                        DedSoc.receive(replyPacket);
                        count = 0;
                    }catch (SocketTimeoutException e){
                        System.out.println("timeout on receive");
                        count+=1; //COUNTS TO 5 AND BREAKS OUT AND ACK ISN'T RECIEVED
                        continue;
                    }
                    byte[] received = replyPacket.getData();
                    int receivedBlockNumber = ((received[2] & 255) << 8) | (received[3] & 255);
                    if(blockNumber == receivedBlockNumber){ //CHECKS THE BLOCK NUMBER AGAINST THE RECEIVED BLOCK NUMBER
                        acked = true;
                        bytesSent += (currentPacketSize - 4);
                    }
                    if(bytesRemaining == 0 && (currentPacketData.length != 512)){
                        doneFinale = true; //SENDS ONE LAST PACKET IF THE REMAINING BYTES IS 0
                    }
                }
                if(count > 5){
                    break; //THE ACTUAL BREAK FOR A BROKEN CONNECTION
                }
                blockNumber++;
        }
            DedSoc.close();
        }catch (FileNotFoundException e) {
            String errorMessage = "The file requested doesn't exist";
            System.out.println(errorMessage); //IN THE CASE WHERE THE FILE DOESN'T EXIST, THIS SENDS A PACKET WITH THE ERROR DATA FOR THE CLIENT TO GET
            byte[] errorData = new byte[2 + 2 + errorMessage.length() + 1];
            byte[] errorMessageByte = errorMessage.getBytes();
            errorData[0] = 0;
            errorData[1] = 5;
            errorData[2] = 0;
            errorData[3] = 1; // ERROR CODE FOR FILE NOT FOUND
            errorData[errorMessage.length() + 4] = 0;
            System.arraycopy(errorMessageByte, 0, errorData, 4, (errorMessage.length()));
            DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length);
            try {
                errorPacket.setAddress(replyPacket.getAddress());
                errorPacket.setPort(replyPacket.getPort());
                DedSoc.send(errorPacket); //SENDS THE ERROR PACKET AND CLOSES OUT
            } catch (IOException f) {
                System.out.println("Unable to send error Packet");
                f.printStackTrace();
            }
            DedSoc.close(); //CLOSES THE SOCKET
        }
        }catch (IOException g){
            g.printStackTrace();
        }
    }
}