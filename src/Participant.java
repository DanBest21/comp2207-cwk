import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;

public class Participant
{
    private final int coordinatorPort;
    private final int loggerPort;
    private final int portNumber;
    private final int timeout;

    private final Socket socket;
    private BufferedReader in;
    private PrintStream out;

    private final ParticipantLogger logger;

    private Outcome outcome;

    public Participant(int coordinatorPort, int loggerPort, int portNumber, int timeout)
    {
        this.coordinatorPort = coordinatorPort;
        this.loggerPort = loggerPort;
        this.portNumber = portNumber;
        this.timeout = timeout;

        ParticipantLogger tempLogger;

        try
        {
            ParticipantLogger.initLogger(this.loggerPort, this.portNumber, this.timeout);
            tempLogger = ParticipantLogger.getLogger();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            tempLogger = null;
        }

        this.logger = tempLogger;
        this.socket = initialise();

        try
        {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintStream(socket.getOutputStream());

            logger.connectionEstablished(this.coordinatorPort);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        Participant participant = new Participant(Integer.parseInt(args[0]),
                Integer.parseInt(args[1]),
                Integer.parseInt(args[2]),
                Integer.parseInt(args[3]));

        participant.run();
    }

    public void run()
    {
        MessageParser parser = new MessageParser();

        sendMessage(MessageType.JOIN);

        int destinationPort = coordinatorPort;

        ExecutorService messageService = Executors.newSingleThreadExecutor();

        Callable<List<Integer>> retrieveParticipants = () -> {
            String message = in.readLine();
            logger.messageReceived(destinationPort, message);
            return parser.parseDetails(message);
        };

        Future<List<Integer>> futureParticipants = messageService.submit(retrieveParticipants);

        List<Integer> otherParticipants = null;

        try
        {
            otherParticipants = futureParticipants.get();
            logger.detailsReceived(otherParticipants);
        }
        catch (InterruptedException | ExecutionException ex)
        {
            ex.printStackTrace();
            return;
        }

        Callable<List<String>> retrieveOptions = () -> {
            String message = in.readLine();
            logger.messageReceived(destinationPort, message);
            return parser.parseVoteOptions(message);
        };

        Future<List<String>> futureOptions = messageService.submit(retrieveOptions);

        List<String> voteOptions = null;

        try
        {
            voteOptions = futureOptions.get(timeout, TimeUnit.MILLISECONDS);
            logger.voteOptionsReceived(voteOptions);
        }
        catch (TimeoutException ex)
        {
            logger.participantCrashed(coordinatorPort);
            return;
        }
        catch (InterruptedException | ExecutionException ex)
        {
            ex.printStackTrace();
            return;
        }

        messageService.shutdown();

        startElection(portNumber, otherParticipants, voteOptions);

        sendMessage(MessageType.OUTCOME);
    }

    public Socket initialise()
    {
        while (true)
        {
            try
            {
                Socket socket = new Socket("localhost", this.coordinatorPort);

                return socket;
            }
            catch (ConnectException e)
            {
                System.out.println("Coordinator at port number '" + this.coordinatorPort + "' not found. Attempting to reconnect...");

                try
                {
                    Thread.sleep(timeout / 5);
                }
                catch (InterruptedException ex)
                {
                    ex.printStackTrace();
                }
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
                return null;
            }
        }
    }

    public void sendMessage(MessageType type) throws IllegalArgumentException
    {
        String message;

        switch (type)
        {
            case JOIN:
                message = sendJoinRequest();
                break;
            case OUTCOME:
                message = sendOutcome();
                break;
            default:
                throw new IllegalArgumentException(type + " is an invalid message type for a participant.");
        }

        logger.messageSent(this.coordinatorPort, message);
    }

    private String sendJoinRequest()
    {
        StringBuilder message = new StringBuilder("JOIN ");

        message.append(this.portNumber);

        out.println(message.toString().trim());
        logger.joinSent(this.coordinatorPort);

        return message.toString().trim();
    }

    private String sendOutcome()
    {
        StringBuilder message = new StringBuilder("OUTCOME ");

        message.append(this.outcome.getVote()).append(" ");

        List<Integer> participants = this.outcome.getOtherParticipants();

        for (int otherParticipant : participants)
        {
            message.append(otherParticipant).append(" ");
        }

        out.println(message.toString().trim());

        logger.outcomeNotified(this.outcome.getVote(), participants);

        return message.toString().trim();
    }

    public void startElection(int participant, List<Integer> otherParticipants, List<String> voteOptions)
    {
        Election election = new Election(participant, otherParticipants, voteOptions, logger, timeout);
        this.outcome = election.holdElection();
    }
}