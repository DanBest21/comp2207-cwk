import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class Coordinator
{
    private final int portNumber;
    private final int loggerPort;
    private final int numberOfParticipants;
    private final int timeout;
    private final String[] options;

    private final ServerSocket socket;
    private final Map<Integer, Socket> participants = new HashMap<>();

    private final CoordinatorLogger logger = CoordinatorLogger.getLogger();

    public Coordinator(int portNumber, int loggerPort, int numberOfParticipants, int timeout, String[] options)
    {
        this.portNumber = portNumber;
        this.loggerPort = loggerPort;
        this.numberOfParticipants = numberOfParticipants;
        this.timeout = timeout;
        this.options = options;

        this.socket = initialise(portNumber);
    }

    public static void main(String[] args)
    {
        try
        {
            String[] options = new String[args.length - 4];
            for (int i = 4; i < args.length; i++) { options[i - 4] = args[i]; }

            Coordinator coordinator = new Coordinator(Integer.parseInt(args[0]),
                    Integer.parseInt(args[1]),
                    Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]),
                    options);

            for (int i = 0; i < coordinator.numberOfParticipants; i++)
            {
                Socket socket = coordinator.accept();

                ListenerThread thread = coordinator.new ListenerThread(socket);
                thread.start();
            }
        }
        catch (RuntimeException ex)
        {
            ex.printStackTrace();
        }
    }

    private ServerSocket initialise(int portNumber)
    {
        try
        {
            CoordinatorLogger.initLogger(this.loggerPort, this.portNumber, this.timeout);

            ServerSocket socket = new ServerSocket(portNumber);
            logger.startedListening(portNumber);

            return socket;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

    private Socket accept()
    {
        try
        {
            return this.socket.accept();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

    private class ListenerThread extends Thread
    {
        private final Socket client;

        public ListenerThread(Socket client)
        {
            this.client = client;

            try
            {
                client.setSoTimeout(timeout);
            }
            catch (SocketException ex)
            {
                ex.printStackTrace();
            }
        }

        @Override
        public void run()
        {
            try
            {
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintStream out = new PrintStream(client.getOutputStream());

                MessageParser parser = new MessageParser();

                int portNumber = parser.parseJoinRequest(in.readLine());
                logger.joinReceived(portNumber);
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }

            super.run();
        }
    }
}