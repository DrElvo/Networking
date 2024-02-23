import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class TFTP_UDP_SERVER_READING extends Thread {
    private DatagramSocket DedSoc = null;
    private DatagramPacket RecPac;
    private int MyPort;
    InetAddress inetAddress = InetAddress.getLocalHost();

    /**
     * THE CONSTRUCTOR FOR THE SERVER READING SIDE WHICH OPENS THE THREAD PROPER IN RUN();
     * @param TransPort THIS IS THE RANDOM PORT NUMBER THAT WAS MADE AND PASSED INSIDE
     * @param TransPac THIS IS THE FIRST REQUEST PACKET THAT IS USED TO GET THE PORT AND ADDRESS OF THE SENDER
     * @throws IOException THE EXCEPTION THROWN WHEN THERE ARE ISSUES LATER IN THE CODE, RATHER THAN MORE TRY CATCH BLOCKS
     */
    public TFTP_UDP_SERVER_READING(int TransPort, DatagramPacket TransPac) throws IOException {
        RecPac = TransPac;
        MyPort = TransPort;
        DedSoc = new DatagramSocket(MyPort, inetAddress);// THE SOCKET ESTABLISHED ON THE NEW RANDOM PORT AND THE LOCAL HOST ADDRESS
        DedSoc.setSoTimeout(500);
    }

    /**
     * THIS IS THE MAIN RUN METHOD WHICH ACTUALLY HANDLES THE THREAD
     */
    @Override
    public void run() {
        File subfolder = new File("ServerSide"); //THE NEW FOLDER OF WHICH THE FILE WILL BE WRITTEN TOO
        if (!subfolder.exists()) {
            subfolder.mkdir();
        }

        // ACK ASSEMBLY

        int SendPort = RecPac.getPort();
        InetAddress SendAddr = RecPac.getAddress(); //THE PORT AND ADDRESS ARE STRIPPED FROM THE REQUEST PACKET
        int ackLength = 4;
        byte[] ackPacketByte = new byte[ackLength];
        ackPacketByte[0] = 0;
        ackPacketByte[2] = 0;
        ackPacketByte[3] = 0;
        ackPacketByte[1] = 4; //ASSEMBLY OF THE FIRST ACK PACKET ALLOWING FOR DATA TRANSFER TO COMMENCE ONCE SENT
        DatagramPacket ackPacket = new DatagramPacket(ackPacketByte,ackLength);
        ackPacket.setPort(SendPort);
        ackPacket.setAddress(SendAddr); //SETS THE ADDRESS AND PORT OF FROM THE DEVICE IT CAME FROM

        // END ACK ASSEMBLY

        int maxFileNameLen = 255;
        byte[] fileNameByteArray = new byte[maxFileNameLen];
        byte[] recData = RecPac.getData(); //GETS THE DATA FROM THE PACKET, IN WHICH IS STORED THE FILE TRANSFER METHOD AND THE FILENAME
        System.arraycopy(recData, 2, fileNameByteArray, 0, maxFileNameLen);
        int zeroByte = -1;
        for (int i = 0; i < maxFileNameLen; i++) {
            if (fileNameByteArray[i] == 0) {
                zeroByte = i;
                break;
            }
        } //FINDS THE FILE NAME DEPENDENT ON THE FIRST 0 AFTER THE FILENAME STARTS TO BE ESTABLISHED
        String fileName = (zeroByte == -1) ?
                new String(fileNameByteArray) : new String(fileNameByteArray, 0, zeroByte); //SETS THE FILE NAME FOR LATER USE
        try {
            DedSoc.send(ackPacket); //SENDS THE PACKET TO START DATA TRANSFER
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        FileOutputStream fileOut = null;
        try {
            System.out.println(fileName);
            File outputFile = new File(subfolder, fileName);
            fileOut = new FileOutputStream(outputFile); //SETS UP THE FILE OUTPUT STREAM FROM THE CREATED STREAM EARLIER, AND THE FILENAME ESTABLISHED EARLIER
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        int dataPacketSize = 516;
        byte[] dataPacketData = new byte[dataPacketSize];
        byte[] actualDataPacketData = null;
        int actualDataPacketSize;
        boolean done = false;
        DatagramPacket mainpacket = new DatagramPacket(dataPacketData, dataPacketSize); //STARTS A NEW PACKET OF THE LENGTH OF MAX DATA TRANSFER
        int writtenBlockNumber = 0;
        int count = 0;
        while(!done) { //THE WHILE LOOP THAT KEEP ACCEPTING DATA UNTIL IT REACHES A PACKET OF LESS THAN 516
            try {
                if(count > 5){ //BREAKOUT ON THE CASE CONNECTION IS LOST
                    System.out.println("Timeout more than 5 times");
                    done = true;
                }
                try {
                    DedSoc.receive(mainpacket); //RECEIVES A DATA PACKET OF THE LENGTH DEFINED ABOVE
                    count = 0;
                }catch (SocketTimeoutException e){
                    System.out.println("Timeout on receive");
                    count+=1;
                    DedSoc.send(ackPacket); //RESENDS THE ACK IF NO RESPONSE IS GIVEN
                    continue;
                }
                actualDataPacketData = mainpacket.getData();
                actualDataPacketSize = mainpacket.getLength(); //GETS THE LENGTH AND DATA OUT OF THE DATA PACKET
                InetAddress address = mainpacket.getAddress();
                int port = mainpacket.getPort(); //GETS THE PORT OF THE DATA PACKET JUST IN CASE THERE IS A CHANGE
                ackPacketByte = new byte[ackLength];
                int recievedBlockNumber = ((actualDataPacketData[2] & 255) << 8) | (actualDataPacketData[3] & 255); //GETS THE BLOCK NUMBER FROM THE PACKET FOR RESUBMISSION
                ackPacketByte[0] = 0;
                ackPacketByte[1] = 4;
                ackPacketByte[2] = (byte) ((recievedBlockNumber >> 8) &255);
                ackPacketByte[3] = (byte) (recievedBlockNumber&255);
                if(writtenBlockNumber >= recievedBlockNumber){
                    continue; //BREAKS OUT IF THE BLOCK NUMBER IS WRONG SO IT DOESN'T REWRITE DATA
                }
                writtenBlockNumber = ((ackPacketByte[2] & 255) << 8) | (ackPacketByte[3] & 255);
                fileOut.write(actualDataPacketData, 4, (actualDataPacketSize - 4)); //WRITES THE DATA AS LONG AS ALL THE ABOVE CONDITIONS ARE MET
                ackPacket = new DatagramPacket(ackPacketByte,ackLength);
                ackPacket.setPort(port);
                ackPacket.setAddress(address); //RESETS PORT AND ADDRESS JUST IN CASE
                try {
                    DedSoc.send(ackPacket); //SENDS THE NEW ACK PACKET WITH THE UPDATED BLOCK NUMBER
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(actualDataPacketSize < 516){
                    if(actualDataPacketSize == 4){
                        done = true; //WAITS FOR ONE LAST PACKET IN CASE THE EDGE CASE IS MET
                    }
                }
            } catch (IOException e) { //HANDLES THE ERROR IF SOMETHING GOES WRONG WITH IO FOR FILE NAME OR OTHER
                e.printStackTrace();
            }
        }
        DedSoc.close(); //CLOSES EVERYTHING ONCE DONE
    }
}