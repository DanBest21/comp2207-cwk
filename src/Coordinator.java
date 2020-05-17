import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

public class Coordinator
{
    private final int portNumber;
    private final int loggerPort;
    private final int numberOfParticipants;
    private final int timeout;
    private final String[] options;

    private final ServerSocket serverSocket;
    private final Map<Integer, Socket> participants = Collections.synchronizedMap(new HashMap<>());
    private final List<ParticipantThread> threads = Collections.synchronizedList(new ArrayList<>());

    private final CoordinatorLogger logger = CoordinatorLogger.getLogger();

    public Coordinator(int portNumber, int loggerPort, int numberOfParticipants, int timeout, String[] options)
    {
        this.portNumber = portNumber;
        this.loggerPort = loggerPort;
        this.numberOfParticipants = numberOfParticipants;
        this.timeout = timeout;
        this.options = options;

        this.serverSocket = initialise(portNumber);
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

            for (int i = 0; i < coordinator.getNumberOfParticipants(); i++)
            {
                Socket socket = coordinator.accept();
                coordinator.getLogger().connectionAccepted(socket.getPort());

                ParticipantThread thread = coordinator.new ParticipantThread(socket);
                thread.start();
                coordinator.getThreads().add(thread);
            }

            coordinator.getThreads().forEach(e -> e.sendMessage(MessageType.DETAILS));
            coordinator.getThreads().forEach(e -> e.sendMessage(MessageType.VOTE_OPTIONS));
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

            socket.setSoTimeout(this.timeout);

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
            return this.serverSocket.accept();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

    public int getNumberOfParticipants() { return numberOfParticipants; }

    public CoordinatorLogger getLogger() { return this.logger; }

    public List<ParticipantThread> getThreads() { return this.threads; }

    private class ParticipantThread extends Thread
    {
        private final Socket socket;
        private BufferedReader in;
        private PrintStream out;

        private int portNumber;

        public ParticipantThread(Socket socket)
        {
            this.socket = socket;

            try
            {
                socket.setSoTimeout(timeout);

                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintStream(socket.getOutputStream());
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }

        @Override
        public void run()
        {
            try
            {
                MessageParser parser = new MessageParser();
                String message;

                message = in.readLine();
                logger.messageReceived(socket.getPort(), message);
                portNumber = parser.parseJoinRequest(message);
                logger.joinReceived(portNumber);

                participants.put(portNumber, socket);

                message = in.readLine();
                logger.messageReceived(portNumber, message);
                Outcome outcome = parser.parseOutcome(message);
                logger.outcomeReceived(portNumber, outcome.getVote());
            }
            catch (SocketTimeoutException ex)
            {
                logger.participantCrashed(socket.getPort());
                ex.printStackTrace();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }

        public void sendMessage(MessageType type) throws IllegalArgumentException
        {
            String message;

            switch (type)
            {
                case DETAILS:
                    message = sendDetails();
                    break;
                case VOTE_OPTIONS:
                    message = sendVoteOptions();
                    break;
                default:
                    throw new IllegalArgumentException(type + " is an invalid message type for the coordinator.");
            }

            logger.messageSent(portNumber, message);
        }

        private String sendDetails()
        {
            StringBuilder message = new StringBuilder("DETAILS ");

            for (int portNumber : participants.keySet())
            {
                if (portNumber != this.portNumber)
                {
                    message.append(portNumber).append(" ");
                }
            }

            out.println(message);
            logger.detailsSent(this.portNumber,
                    participants.keySet().stream()
                            .filter(e -> e != this.portNumber)
                            .collect(Collectors.toList()));

            return message.toString();
        }

        private String sendVoteOptions()
        {
            StringBuilder message = new StringBuilder("VOTE_OPTIONS ");

            for (String option : options)
            {
                message.append(option).append(" ");
            }

            out.println(message);
            logger.voteOptionsSent(this.portNumber, Arrays.asList(options));

            return message.toString();
        }
    }
}