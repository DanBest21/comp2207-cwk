import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.*;
import java.util.List;

// TODO: Make the participant be able to start before the coordinator.
public class Participant extends Thread
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
            // socket.setSoTimeout(timeout);

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

        participant.start();
    }

    public void run()
    {
        try
        {
            MessageParser parser = new MessageParser();

            sendMessage(MessageType.JOIN);

            String message;
            int destinationPort = coordinatorPort;

            message = in.readLine();
            logger.messageReceived(destinationPort, message);
            List<Integer> otherParticipants = parser.parseDetails(message);
            logger.detailsReceived(otherParticipants);

            message = in.readLine();
            logger.messageReceived(destinationPort, message);
            List<String> voteOptions = parser.parseVoteOptions(message);
            logger.voteOptionsReceived(voteOptions);

            startElection(portNumber, otherParticipants, voteOptions);

            sendMessage(MessageType.OUTCOME);
        }
        catch (SocketTimeoutException ex)
        {
            logger.participantCrashed(coordinatorPort);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    public Socket initialise()
    {
        while (true)
        {
            try
            {
                Socket socket = new Socket("localhost", this.coordinatorPort);

                // socket.setSoTimeout(this.timeout);

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

        message.append(this.outcome.getParticipantPort()).append(" ");

        for (int otherParticipant : this.outcome.getOtherParticipants())
        {
            message.append(otherParticipant).append(" ");
        }

        out.println(message.toString().trim());

        List<Integer> participants = this.outcome.getOtherParticipants();
        participants.add(this.outcome.getParticipantPort());

        logger.outcomeNotified(this.outcome.getVote(), participants);

        return message.toString().trim();
    }

    public void startElection(int participant, List<Integer> otherParticipants, List<String> voteOptions)
    {
        Election election = new Election(participant, otherParticipants, voteOptions, logger, timeout);
        this.outcome = election.holdElection();
    }
}