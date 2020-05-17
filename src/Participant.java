import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.*;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Participant
{
    private final int coordinatorPort;
    private final int loggerPort;
    private final int portNumber;
    private final int timeout;

    private final Socket socket;
    private BufferedReader in;
    private PrintStream out;

    private final ParticipantLogger logger = ParticipantLogger.getLogger();

    public Participant(int coordinatorPort, int loggerPort, int portNumber, int timeout)
    {
        this.coordinatorPort = coordinatorPort;
        this.loggerPort = loggerPort;
        this.portNumber = portNumber;
        this.timeout = timeout;

        this.socket = initialise();

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

    public static void main(String[] args)
    {
        Participant participant = null;

        try
        {
            participant = new Participant(Integer.parseInt(args[0]),
                    Integer.parseInt(args[1]),
                    Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]));

            MessageParser parser = new MessageParser();

            participant.sendMessage(MessageType.JOIN);
            participant.getLogger().connectionEstablished(participant.coordinatorPort);

            String message;
            int destinationPort = participant.getCoordinatorPort();

            message = participant.getInputStream().readLine();
            participant.getLogger().messageReceived(destinationPort, message);
            int[] otherParticipants = parser.parseDetails(message);
            participant.getLogger().detailsReceived(Arrays.stream(otherParticipants).boxed().collect(Collectors.toList()));

            message = participant.getInputStream().readLine();
            participant.getLogger().messageReceived(destinationPort, message);
            String[] voteOptions = parser.parseVoteOptions(message);
            participant.getLogger().voteOptionsReceived(Arrays.asList(voteOptions));

            participant.startElection(participant.getPortNumber(), otherParticipants, voteOptions);
        }
        catch (SocketTimeoutException ex)
        {
            participant.getLogger().participantCrashed(participant.getCoordinatorPort());
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    public Socket initialise()
    {
        try
        {
            ParticipantLogger.initLogger(this.loggerPort, this.portNumber, this.timeout);

            Socket socket = new Socket("localhost", this.portNumber);
            logger.startedListening();

            socket.setSoTimeout(this.timeout);

            return socket;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return null;
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
                message = "";
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

        out.println(message);
        logger.joinSent(this.coordinatorPort);

        return message.toString();
    }

    public Outcome startElection(int participant, int[] otherParticipants, String[] voteOptions)
    {
        Election election = new Election(participant, otherParticipants, voteOptions, logger, timeout);
        return election.holdElection();
    }

    public int getPortNumber() { return portNumber; }

    public int getCoordinatorPort() { return coordinatorPort; }

    public ParticipantLogger getLogger() { return logger; }

    public BufferedReader getInputStream() { return in; }
}