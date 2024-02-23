import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;

/**
 * The main method of the server
 */

public class TFTP_UDP_SERVER extends Thread {
    protected DatagramSocket socket = null;
    protected DatagramSocket sendSocket = null;
    int portNum = 69; //Listening port number
    InetAddress inetAddress = InetAddress.getLocalHost();
    public TFTP_UDP_SERVER() throws SocketException, IOException {
        this("UDPSocketServer");
    }

    /**
     * This constructs the server
     * @param name
     * @throws IOException
     */
    public TFTP_UDP_SERVER(String name) throws IOException {
        super(name);
        System.out.println("Local IP address: " + inetAddress.getHostAddress());
        socket = new DatagramSocket(portNum, inetAddress);
    }

    /**
     * The main run method
     */
    @Override
    public void run() {
        int min = 500;
        int offset = 9001;
        Thread[] activePorts = new Thread[min];
        byte[] recvBuf = new byte[1024];
        try {
            while (true) {
                int portNum = new Random().nextInt(min) + offset; //CREATES A RANDOM PORT NUMBER ABOVE 9000
                if (activePorts[portNum - offset] != null && activePorts[portNum - offset].isAlive()){
                    continue;
                }
                DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length); //CREATES THE NEW DATAGRAM PACKET THAT TAKES THE REQUEST DATA
                try {
                    socket.receive(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                byte[] ackData = packet.getData();
                if(ackData[1] == 1){    // IF THE REQUEST CODE IS 1 IT OPENS A WRITING THREAD
                    System.out.println("Writing");
                    Thread thread = new Thread(new TFTP_UDP_SERVER_WRITING(portNum, packet));
                    activePorts[portNum - offset] = thread;
                    thread.start(); //STARTS THE THREAD ON THE PORT NUMBER GENERATED
                } else if(ackData[1] == 2) { //IF THE REQUEST CODE IS 2 IT OPENS A READING THREAD
                    System.out.println("Reading");
                    Thread thread = new Thread(new TFTP_UDP_SERVER_READING(portNum, packet));
                    activePorts[portNum - offset] = thread;
                    thread.start(); //STARTS THE THREAD ON THE PORT NUMBER GENERATED
                } else{
                    System.out.println("Invalid request");
                    if(ackData[1] == 3){
                        System.out.println("Data");
                    }else if(ackData[1] == 4){
                        System.out.println("Ack");
                    }else if(ackData[1] == 5){
                        System.out.println("Error");
                    } else{
                        System.out.println("Unknown"); //IF THE CODE IS ALLOWED THEN IT DISPLAYS THIS AND BREAKS OUT ON THIS SPECIFIC REQUEST
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            socket.close(); //CLOSES THE SOCKET WHEN ITS DONE, THOUGH THIS WON'T LIKELY HAPPEN AS THE CONDITIONAL CAN'T CHANGE
        }
    }
    public static void main(String[] args) throws IOException {
        new TFTP_UDP_SERVER().start();
        System.out.println("Time Server Started");
    }
}
