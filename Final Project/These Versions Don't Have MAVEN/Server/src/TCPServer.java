import java.io.*;
import java.net.*;

/**
 * THIS IS THE MAIN TCP CLASS WHICH RUNS THE SERVER, UNLIKE THE TFTP VERSION I HAVE KEPT THE HANDLERS INSIDE FOR EASE OF MAVEN
 */

public class TCPServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(69); //ESTABLISHES THE SOCKET ON PORT 69
        System.out.println("Server started");
        while (true) { //A LOOP THAT WAITS FOR A NEW CONNECTION, AND WAITS FOR A NEW CONNECTION WHEN ONE IS BROKEN
            Socket socket = serverSocket.accept();
            System.out.println("Client connected");
            Thread clientThread = new Thread(new ClientHandler(socket)); //OPENS THE NEW THREAD WITH THE CLIENTHANDLER
            clientThread.start();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        /**
         * THE MAIN RUN METHOD OF THE THREAD
         */

        @Override
        public void run() {
            try {
                InputStream inFromClient = socket.getInputStream(); //STARTS THE SOCKET INPUT AND OUTPUT STREAM
                OutputStream outToClient = socket.getOutputStream();
                BufferedReader requirement = new BufferedReader(new InputStreamReader(inFromClient));
                String TCPOPCODE = requirement.readLine(); //READS THE OPCODE OF THE CLIENT TO DECIDE WHETHER IT IS READING OR WRITING
                BufferedReader reader = new BufferedReader(new InputStreamReader(inFromClient));
                String fileName = reader.readLine(); //READS THE FILE NAME OF THE CLIENT AS IT IS USED IN BOTH CASES
                System.out.println("Received file name from client: " + fileName);
                String fullFileName = "Serverside/" + fileName; //DIVERTS THE FILE TO BE IN THE FOLDER SERVER SIDE
                if(TCPOPCODE == null){
                    TCPOPCODE = "3"; //CHANGES THE OP CODE IN CASE IT IS STILL NULL AFTER READING TO STOP NULL POINTER EXCEPTION
                }
                if(TCPOPCODE.equals("1")){ //CHECKS THE OPCODE AND STARTS A READING THREAD IF THE CODE WAS 1
                if(fileName != null) { //ENSURE THE FILENAME IS NOT NULL
                    receiveFile(inFromClient, fullFileName); //RUNS THE METHOD RECEIVE PASSING IN THE INPUT STREAM FROM THE CLIENT AND THE FILENAME
                    socket.shutdownInput();
                    socket.shutdownOutput();
                    socket.close(); //CLOSES THE STREAMS AND THE SOCKET WHEN DONE
                    System.out.println("Connection closed");
                }
                else{
                    System.err.println("Filename was null"); //BREAKS OUT IF THE FILENAME WAS NULL
                }
                } else if (TCPOPCODE.equals("2")) { //CHECKS THE OPCODE AND STARTS A WRITING THREAD IF THE CODE WAS 2
                    if(fileName != null) {//ENSURE THE FILENAME IS NOT NULL
                        sendFile(outToClient, inFromClient, fullFileName); //RUNS THE SEND METHOD AND PASSES IN THE INPUT AND OUTPUT STREAMS AS WELL AS THE FILENAME. IT NEEDS THE OUT TO CLIENT STREAM TO TELL IT WHEN A FILE DOESN'T EXIST
                        socket.shutdownInput();
                        socket.shutdownOutput();
                        socket.close(); //CLOSES THE STREAMS AND THE SOCKET WHEN DONE
                        System.out.println("Connection closed");
                    }
                    else{
                        System.err.println("Filename was null"); //BREAKS OUT IF THE FILENAME WAS NULL
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to connect to client: " + e.getMessage());
                return;
            }
        }

        /**
         * THIS IS THE RECEIVER METHOD, THIS TAKES THE PARAMETERS GIVEN AND WRITES OUT A FILE TO THE SERVERSIDE FOLDER FROM THE CLIENT
         * @param inFromClient THIS IS THE INPUT STREAM FROM THE CLIENT TO GET THE CLIENTS INFORMATION
         * @param fileName THIS IS THE FILENAME FROM THE CLIENT
         * @throws IOException IN CASE THERE IS AN ERROR
         */
        private void receiveFile(InputStream inFromClient, String fileName) throws IOException {
            FileOutputStream fileOut = new FileOutputStream(fileName); //ESTABLISHES THE OUTPUT FILE FROM THE FILENAME PROVIDED
            byte[] buffer = new byte[516];
            int bytesRead;
            while ((bytesRead = inFromClient.read(buffer)) != -1) { //GETS THE DATA UNTIL THE IN FROM THE CLIENT IS -1 AND THEN BREAKS
                fileOut.write(buffer, 0, bytesRead); //WRITES OUT
            }
            System.out.println("File received successfully");
            fileOut.close();
            inFromClient.close(); //CLOSES EVERYTHING ONCE IT IS DONE
        }

        /**
         * THIS IS THE SEND METHOD, THIS TAKES THE PARAMS GIVEN AND WRITES A FILE TO THE CLIENT FROM THE SERVERSIDE FOLDER
         * @param outToClient THIS IS THE SOCKETS OUTPUT STREAM TO THE CLIENT INCASE IT NEEDS TO SAY THE FILE DOESN'T EXIST
         * @param inFromClient THIS IS THE SOCKETS INPUT STREAM FROM THE CLIENT
         * @param fileName THIS IS THE FILE NAME GIVEN FROM THE CLIENT
         * @throws IOException
         */
        private void sendFile(OutputStream outToClient, InputStream inFromClient, String fileName) throws IOException {
            try {
                FileInputStream fileOut = new FileInputStream(fileName); //THE FILE INPUT STREAM FROM THE SERVERSIDE FOLDER, IF THIS FAILS ITS CAUGHT AND DOESN'T SEND "3" AS CONFIRMATION
                PrintWriter confirm = new PrintWriter(outToClient, true); //ESTABLISHES A CONNECTION OUT TO CLIENT
                confirm.println("3");   //SENDS THE CONFIRMATION "3" BECAUSE THE FILE WAS FOUND
                byte[] buffer = new byte[516];
                int bytesRead;
                while ((bytesRead = fileOut.read(buffer)) != -1) {
                    outToClient.write(buffer, 0, bytesRead); //WRITES OUT TO THE CLIENT UNTIL THE BYTESREAD IS NOT -1
                }
                System.out.println("File sent successfully"); //ONCE OUT OF THE LOOP AND THE ENTIRE FILE IS SENT IT BREAKS OUT AND CONFIRMS OF THIS
                fileOut.close();
            }catch (IOException f){ //CATCHES THE FACT THAT THE INPUT STREAM CANNOT BE MADE AND SENDS CONFIRMATION THAT THE FILE DOESNT EXIST
                System.err.println("Invalid file request: " + f);
                PrintWriter confirm = new PrintWriter(outToClient, true);
                confirm.println("4");
            }
            inFromClient.close();
        }
    }
}