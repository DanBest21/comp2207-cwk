import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Election
{
    private final int numberOfRounds;

    private final int participant;
    private final int[] otherParticipants;

    private final Vote vote;
    private final List<Vote> collectedVotes = new ArrayList<>();
    private final List<Vote> newVotes = new ArrayList<>();

    private final Map<Integer, PrintStream> outputConnections = new HashMap<>();
    private final Map<Integer, BufferedReader> inputConnections = new HashMap<>();

    private final ServerSocket serverSocket;

    private final ParticipantLogger logger;

    private final ExecutorService pollService;

    public Election(int participant, int[] otherParticipants, String[] voteOptions, ParticipantLogger logger, int timeout)
    {
        this.numberOfRounds = otherParticipants.length;

        this.participant = participant;
        this.otherParticipants = otherParticipants;

        this.vote = decideVote(participant, voteOptions);
        collectedVotes.add(vote);
        newVotes.add(vote);

        this.serverSocket = initialise(participant, timeout);

        this.logger = logger;

        this.pollService = Executors.newFixedThreadPool(otherParticipants.length);

        establishConnections(otherParticipants, timeout);
    }

    public Outcome holdElection()
    {
        for (int i = 1; i <= numberOfRounds; i++)
        {
            logger.beginRound(i);

            Runnable sendVotes = () -> {
                for (int portNumber : outputConnections.keySet())
                {
                    StringBuilder message = new StringBuilder("VOTE ");

                    for (Vote vote : newVotes)
                    {
                        message.append(vote.getParticipantPort()).append(" ").append(vote.getVote());
                    }

                    outputConnections.get(portNumber).println(message);
                    logger.votesSent(portNumber, newVotes);
                    logger.messageSent(portNumber, message.toString());
                }
            };

            pollService.execute(sendVotes);

            Callable<List<Vote>> retrieveVotes = () -> {
                List<Vote> retrievedVotes = new ArrayList<>();

                MessageParser parser = new MessageParser();
                String message;

                for (int portNumber : inputConnections.keySet())
                {
                    message = inputConnections.get(portNumber).readLine();
                    logger.messageReceived(portNumber, message);

                    Vote[] newVotes = parser.parseVotes(message);
                    logger.votesReceived(portNumber, Arrays.asList(newVotes));

                    retrievedVotes.addAll(Arrays.asList(newVotes));
                }

                return retrievedVotes;
            };

            Future<List<Vote>> future = pollService.submit(retrieveVotes);
            List<Vote> retrievedVotes = new ArrayList<>();

            try
            {
                retrievedVotes = future.get();
            }
            catch (InterruptedException | ExecutionException ex)
            {
                ex.printStackTrace();
            }

            for (Vote newVote : retrievedVotes)
            {
                boolean voteAlreadyCollected = false;

                for (Vote vote : collectedVotes)
                {
                    if (newVote.getParticipantPort() == vote.getParticipantPort())
                        voteAlreadyCollected = true;
                }

                if (!voteAlreadyCollected)
                    collectedVotes.add(newVote);
            }

            logger.endRound(i);
        }

        return new Outcome(participant, decideOutcome(collectedVotes), otherParticipants);
    }

    private Vote decideVote(int participant, String[] voteOptions)
    {
        Random random = new Random();
        int optionNumber = random.nextInt(voteOptions.length);

        return new Vote(participant, voteOptions[optionNumber]);
    }

    private String decideOutcome(List<Vote> votes)
    {
        Map<String, Long> tally = votes
                .stream()
                .map(Vote::getVote)
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()));

        String winningVote = "";
        long winningVoteCount = 0;

        for (String vote : tally.keySet())
        {
            if (tally.get(vote) > winningVoteCount ||
                    (tally.get(vote) == winningVoteCount && vote.compareTo(winningVote) < 0))
            {
                winningVote = vote;
                winningVoteCount = tally.get(vote);
            }
        }

        List<Integer> participants = Arrays.stream(otherParticipants).boxed().collect(Collectors.toList());
        participants.add(participant);

        logger.outcomeDecided(winningVote, participants);

        return winningVote;
    }

    private ServerSocket initialise(int portNumber, int timeout)
    {
        try
        {
            ServerSocket socket = new ServerSocket(portNumber);
            socket.setSoTimeout(timeout);

            return socket;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

    private void establishConnections(int[] otherParticipants, int timeout)
    {
        Runnable acceptConnections = () -> {
            for (int i = 0; i < otherParticipants.length; i++)
            {
                try
                {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(timeout);

                    logger.connectionAccepted(socket.getPort());

                    outputConnections.put(socket.getPort(), new PrintStream(socket.getOutputStream()));
                }
                catch (IOException ex)
                {
                    ex.printStackTrace();
                }
            }
        };

        pollService.execute(acceptConnections);

        for (int i = 0; i < otherParticipants.length; i++)
        {
            try
            {
                Socket socket = new Socket("localhost", otherParticipants[i]);
                socket.setSoTimeout(timeout);

                logger.connectionEstablished(otherParticipants[i]);

                inputConnections.put(otherParticipants[i], new BufferedReader(new InputStreamReader(socket.getInputStream())));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}
