import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UDPLoggerServer
{
    private final int portNumber;
    private final DatagramSocket socket;
    private final byte[] buffer = new byte[256];

    private final File logFile = new File("logger_server_" + System.currentTimeMillis() + ".log");

    public UDPLoggerServer(int portNumber)
    {
        this.portNumber = portNumber;
        this.socket = initialise();
    }

    public static void main(String[] args)
    {
        UDPLoggerServer server = new UDPLoggerServer(Integer.parseInt(args[0]));

        server.run();
    }

    public DatagramSocket initialise()
    {
        try
        {
            this.logFile.createNewFile();
            return new DatagramSocket(portNumber);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

    public void run()
    {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        PrintWriter writer;

        try
        {
             writer = new PrintWriter(logFile);
        }
        catch (FileNotFoundException ex)
        {
            ex.printStackTrace();
            return;
        }

        while (true)
        {
            try
            {
                socket.receive(packet);
                List<String> tokens = new ArrayList<>(Arrays.asList(new String(packet.getData(), 0, packet.getLength()).trim().split(" ")));

                String id = tokens.remove(0);
                StringBuilder message = new StringBuilder();

                while (!tokens.isEmpty())
                {
                    message.append(tokens.remove(0)).append(" ");
                }

                writer.println(id + " " + System.currentTimeMillis() + " " + message.toString().trim());
                writer.flush();

                // Send back "ACK" to acknowledge the message has been received.
                InetAddress address = packet.getAddress();
                int senderPort = packet.getPort();
                String ack = "ACK";

                DatagramPacket ackPacket = new DatagramPacket(ack.getBytes(), ack.getBytes().length, address, senderPort);
                socket.send(ackPacket);
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }
}