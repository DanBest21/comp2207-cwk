import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Coordinator
{
    private final int portNumber;
    private final int loggerPort;
    private final int numberOfParticipants;
    private final int timeout;
    private final List<String> options;

    private final ServerSocket serverSocket;
    private final Map<Integer, Socket> participants = Collections.synchronizedMap(new HashMap<>());
    private final List<ParticipantThread> threads = Collections.synchronizedList(new ArrayList<>());

    private final CoordinatorLogger logger;

    public Coordinator(int portNumber, int loggerPort, int numberOfParticipants, int timeout, List<String> options)
    {
        this.portNumber = portNumber;
        this.loggerPort = loggerPort;
        this.numberOfParticipants = numberOfParticipants;
        this.timeout = timeout;
        this.options = options;

        CoordinatorLogger tempLogger;

        try
        {
            CoordinatorLogger.initLogger(this.loggerPort, this.portNumber, this.timeout);
            tempLogger = CoordinatorLogger.getLogger();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            tempLogger = null;
        }

        this.logger = tempLogger;
        this.serverSocket = initialise(portNumber);
    }

    public static void main(String[] args)
    {
        List<String> options = Collections.synchronizedList(new ArrayList<>());
        for (int i = 4; i < args.length; i++) { options.add(args[i]); }

        Coordinator coordinator = new Coordinator(Integer.parseInt(args[0]),
                Integer.parseInt(args[1]),
                Integer.parseInt(args[2]),
                Integer.parseInt(args[3]),
                options);

        coordinator.run();
    }

    public void run()
    {
        for (int i = 0; i < numberOfParticipants; i++)
        {
            try
            {
                Socket socket = serverSocket.accept();
                logger.connectionAccepted(portNumber);

                ParticipantThread thread = new ParticipantThread(socket);
                threads.add(thread);
                thread.start();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }

        // Wait the timeout to ensure that all threads are at the point where they can receive the DETAILS and VOTE_OPTIONS.
        ScheduledExecutorService senderService = Executors.newSingleThreadScheduledExecutor();

        Runnable sendDetailsAndOptions = () -> {
            threads.forEach(e -> e.sendMessage(MessageType.DETAILS));
            threads.forEach(e -> e.sendMessage(MessageType.VOTE_OPTIONS));
        };

        senderService.schedule(sendDetailsAndOptions, timeout, TimeUnit.MILLISECONDS);
        senderService.shutdown();
    }

    private ServerSocket initialise(int portNumber)
    {
        try
        {
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

                ExecutorService joinService = Executors.newSingleThreadExecutor();

                Callable<Integer> retrieveJoinRequest = () -> {
                    String joinMessage;

                    joinMessage = in.readLine();
                    logger.messageReceived(socket.getPort(), joinMessage);
                    return parser.parseJoinRequest(joinMessage);
                };

                Future<Integer> futureRequest = joinService.submit(retrieveJoinRequest);

                try
                {
                    portNumber = futureRequest.get(timeout, TimeUnit.MILLISECONDS);

                    synchronized (participants)
                    {
                        participants.put(portNumber, socket);
                    }
                }
                // Handle timeout by closing the socket and interrupting this thread.
                catch (TimeoutException ex)
                {
                    logger.participantCrashed(portNumber);
                    in.close();
                    out.close();
                    socket.close();
                    this.interrupt();
                }
                catch (InterruptedException | ExecutionException ex)
                {
                    ex.printStackTrace();
                }

                joinService.shutdown();

                message = in.readLine();
                logger.messageReceived(portNumber, message);
                Outcome outcome = parser.parseOutcome(message);
                logger.outcomeReceived(portNumber, outcome.getVote());
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

            synchronized (participants)
            {
                for (int portNumber : participants.keySet()) {
                    if (portNumber != this.portNumber) {
                        message.append(portNumber).append(" ");
                    }
                }

                out.println(message.toString().trim());
                logger.detailsSent(this.portNumber,
                        participants.keySet().stream()
                                .filter(e -> e != this.portNumber)
                                .collect(Collectors.toList()));
            }

            return message.toString().trim();
        }

        private String sendVoteOptions()
        {
            StringBuilder message = new StringBuilder("VOTE_OPTIONS ");

            synchronized (options)
            {
                for (String option : options) {
                    message.append(option).append(" ");
                }

                out.println(message.toString().trim());
                logger.voteOptionsSent(this.portNumber, options);
            }

            return message.toString().trim();
        }
    }
}