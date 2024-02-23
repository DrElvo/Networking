import java.io.*;
import java.net.Socket;

public class TCPClient {
    /**
     *STARTS THE NEW CLASS CLIENT READY TO COMMUNICATE WITH THE SERVER
     * @param args THIS IS THE HOSTNAME HOWEVER IN THIS IMPLEMENTATION THE SAME AS UDP IS IT NOT USED
     * @throws IOException THROUGH AN ERROR IF THERE IS ONE, BUT ISNT ACTUALLY USED BECAUSE OF ANOTHER TRY BLOCK
     */
    public static void main(String[] args) throws IOException {

        String hostName = "localhost";
        int portNumber = 69;
        try (Socket socket = new Socket(hostName, portNumber)) { //ESTABLISHES THE NEW SOCKET ON THE SAME PORT AS THE LISTENING SERVER
            System.out.println("Connected to server");
            OutputStream outToServer = socket.getOutputStream();
            InputStream inFromServer = socket.getInputStream(); //STARTS THE INPUT AND OUTPUT STREAM OF THE SOCKET
            boolean valid = false;
            String TCPOPCODE = null; //CREATES THE OPCODE AND MAKES A LOOP UNTIL A VALID OPCODE IS PUT IN
            while (!valid) {
                BufferedReader TCPReader = new BufferedReader(new InputStreamReader(System.in)); //CREATES A READER FOR THE USER TO WRITE THE OPCODE
                System.out.println("Enter 1 for writing to server : Enter 2 for reading from server");
                TCPOPCODE = TCPReader.readLine();
                if (TCPOPCODE.equals("1") | TCPOPCODE.equals("2")) { //CHECKS THE OPCODE TO MAKE SURE ITS VALID BEFORE MOVING ON
                    valid = true;
                } else {
                    System.out.println("Invalid"); //KEEPS ASKING OTHERWISE
                }
            }
            BufferedReader fileNameReader = new BufferedReader(new InputStreamReader(System.in)); //ASKS FOR A FILENAME FROM THE USER THAT THEY ARE EITHER SENDING OR RECEIVING
            System.out.println("Enter file name: ");
            String fileName = fileNameReader.readLine(); //READS THE USERS FILENAME
            if (TCPOPCODE.equals("1")) { //CHECKS WHICH OPCODE IT IS AND OPENS THE CORRESPONDING METHOD
                sendFile(outToServer, fileName, TCPOPCODE);
            } else if (TCPOPCODE.equals("2")) {
                receiveFile(inFromServer, outToServer, fileName, TCPOPCODE);
            } else {
                System.out.println("Invalid TCP OP Code");
            }
        } catch (IOException e) { //OTHERWISE AN IO EXCEPTION IS THROWN IN WHICH THE SERVER CONNECTION FAILS
            System.err.println("Failed to connect to server: " + e.getMessage());
            return;
        }
        System.out.println("Connection closed");
    }

    /**
     *THIS IS THE METHOD FOR THE CLIENT TO SEND A FILE TO THE SERVER
     * @param outToServer THIS IS THE SOCKETS OUTPUT STREAM TO SEND TO THE SERVER
     * @param fileName THIS IS THE FILE NAME THAT THE USER JUST PUT IN BEFORE CALL
     * @param TCPOPCODE THE TCP OP CODE TO PASS TO THE SERVER
     * @throws IOException IO EXCEPTION WHEN A CONNECTION IS BROKEN OR A FILE NAME DOESN'T EXIST
     */
    private static void sendFile(OutputStream outToServer, String fileName, String TCPOPCODE) throws IOException {
        try {
            String fullFileName = "Clientside/" + fileName; //TRIES TO GET THE FILE FROM THE NAME GIVEN AND CATCHES IF IT DOESN'T EXIST
            FileInputStream fileIn = new FileInputStream(fullFileName);
            PrintWriter requirement = new PrintWriter(outToServer, true); //TELLS THE SERVER ITS OPCODE AND WHAT IT WANTS FROM THE SERVER, IN THIS CASE ITS TO WRITE TO IT
            requirement.println(TCPOPCODE);
            PrintWriter writer = new PrintWriter(outToServer, true); //TELLS THE SERVER THE FILE NAME OF THE FILE ITS ABOUT TO SEND
            writer.println(fileName);
            byte[] buffer = new byte[516];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) { //STARTS TO SEND THE FILE BLOCK BY BLOCK UNTIL NOTHING REMAINS AND GIVE A FINAL -1 TO THE SERVER TO SAY ITS DONE
                outToServer.write(buffer, 0, bytesRead);
            }
            System.out.println("File sent successfully");
            fileIn.close(); //CLOSES ONCE THE FILE HAS BEEN SENT
        } catch (IOException g) {
            System.err.println("This file doesn't exist: " + g.getMessage());
        }
        outToServer.close(); //CLOSES THE SOCKET ONCE ITS DONE, OR THE FILE DID EXIST
    }

    /**
     * THIS IS THE METHOD TO RECEIVE A FILE FROM THE SERVER
     * @param inFromServer THIS IS THE INPUT STREAM FROM THE SERVER TO RECEIVE DATA
     * @param outToServer THIS IS THE OUTPUT STREAM TO THE SERVER TO SEND IT THE FILENAME AND OPCODE
     * @param fileName THIS IS THE FILE NAME THE USER PUT IN
     * @param TCPOPCODE THIS IS THE TCP OP CODE, WHICH TELLS THE SERVER WHAT IT WANTS
     * @throws IOException THIS IS THE EXCEPTION THROWN IF SOMETHING GOES WRONG
     */

    private static void receiveFile(InputStream inFromServer, OutputStream outToServer, String fileName, String TCPOPCODE) throws IOException {
        try {
            String fullFileName = "Clientside/" + fileName;
            PrintWriter requirement = new PrintWriter(outToServer, true);
            requirement.println(TCPOPCODE); //THIS SENDS THE OPCODE WHICH ALLOWS THE SERVER TO GET READY TO SEND A FILE
            PrintWriter writer = new PrintWriter(outToServer, true);
            writer.println(fileName); //THIS SENDS OUT THE FILENAME THAT THE CLIENT WANTS FROM THE SERVER
            byte[] buffer = new byte[516];
            int bytesRead;
            BufferedReader confirmReader = new BufferedReader(new InputStreamReader(inFromServer));
            String confirm = confirmReader.readLine(); //GETS THE CONFIRMATION CODE FROM THE SERVER IN ORDER TO TELL WHETHER THE FILE THE CLIENT WANTS EXISTS OR NO
            if (confirm.equals("3")) {
                FileOutputStream fileOut = new FileOutputStream(fullFileName); //IN THE CASE IT DOES, IT STARTS TO RECEIVE DATA UNTIL IT GETS THE -1 FROM THE SERVER SAYING ITS DONE
                while ((bytesRead = inFromServer.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, bytesRead); //WRITES TO THE FILE OUTPUT STREAM UNTIL ITS DONE
                }
                System.out.println("File received successfully"); //CONFIRMATION IT WROTE THE FILE
            } else if (confirm.equals("4")) {
                System.err.println("Invalid file name on request"); //IN THE CASE WHERE THE FILE DOESN'T EXIST, IT TELLS THE USER AND BREAKS OUT
            }
        } catch(IOException g){
                System.err.println(g.getMessage()); //THE EXCEPTION ERROR MESSAGE WHEN SOMETHING GOES WRONG
            }
            outToServer.close(); //CLOSES THE SOCKET
        }


    }


