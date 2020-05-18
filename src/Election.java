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
    private final int timeout;

    private final int participant;

    private final List<Vote> collectedVotes = Collections.synchronizedList(new ArrayList<>());
    private final List<Vote> newVotes = Collections.synchronizedList(new ArrayList<>());

    private final Map<Integer, PrintStream> outputConnections = new HashMap<>();
    private final Map<Integer, BufferedReader> inputConnections = new HashMap<>();

    private final ServerSocket serverSocket;

    private final ParticipantLogger logger;

    private final ExecutorService pollService;

    public Election(int participant, List<Integer> otherParticipants, List<String> voteOptions, ParticipantLogger logger, int timeout)
    {
        this.numberOfRounds = otherParticipants.size();
        this.timeout = timeout;

        this.participant = participant;

        Vote vote = decideVote(voteOptions);
        collectedVotes.add(vote);
        newVotes.add(vote);

        this.logger = logger;

        this.serverSocket = initialise();

        this.pollService = Executors.newFixedThreadPool(otherParticipants.size() * 2);

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
                    if (!newVotes.isEmpty())
                    {
                        StringBuilder message = new StringBuilder("VOTE ");

                        for (Vote vote : newVotes)
                        {
                            message.append(vote.getParticipantPort()).append(" ")
                                    .append(vote.getVote()).append(" ");
                        }

                        outputConnections.get(portNumber).println(message.toString().trim());
                        logger.votesSent(portNumber, newVotes);
                        logger.messageSent(portNumber, message.toString().trim());
                    }
                }
            };

            List<Callable<List<Vote>>> incomingVotes = new ArrayList<>();

            for (int portNumber : inputConnections.keySet())
            {
                Callable<List<Vote>> retrieveVotes = () -> {
                    MessageParser parser = new MessageParser();
                    String message = inputConnections.get(portNumber).readLine();
                    logger.messageReceived(portNumber, message);

                    List<Vote> retrievedVotes = parser.parseVotes(message);

                    if (!retrievedVotes.isEmpty())
                        logger.votesReceived(portNumber, retrievedVotes);

                    return retrievedVotes;
                };

                incomingVotes.add(retrieveVotes);
            }

            pollService.execute(sendVotes);

            try
            {
                List<Future<List<Vote>>> futureVotes =
                        pollService.invokeAll(incomingVotes, timeout, TimeUnit.MILLISECONDS);

                for (Future<List<Vote>> futureVote : futureVotes)
                {
                    List<Vote> retrievedVotes;

                    try
                    {
                        retrievedVotes = futureVote.get();
                    }
                    catch (CancellationException ex)
                    {
                        // TODO: Handle failures (everywhere but especially here)
                        continue;
                    }

                    newVotes.clear();

                    newVotes.addAll(retrievedVotes.stream()
                            .filter(vote -> !collectedVotes
                                    .stream()
                                    .map(Vote::getParticipantPort)
                                    .collect(Collectors.toList())
                                    .contains(vote.getParticipantPort()))
                            .collect(Collectors.toList()));

                    collectedVotes.addAll(newVotes);
                }
            }
            catch (InterruptedException | ExecutionException ex)
            {
                ex.printStackTrace();
            }

            logger.endRound(i);
        }

        List<Integer> voters = collectedVotes.stream().map(Vote::getParticipantPort).collect(Collectors.toList());

        return new Outcome(participant, decideOutcome(collectedVotes, voters), voters);
    }

    private Vote decideVote(List<String> voteOptions)
    {
        Random random = new Random();
        int optionNumber = random.nextInt(voteOptions.size());

        return new Vote(participant, voteOptions.get(optionNumber));
    }

    private String decideOutcome(List<Vote> votes, List<Integer> voters)
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

        logger.outcomeDecided(winningVote, voters);

        return winningVote;
    }

    private ServerSocket initialise()
    {
        try
        {
            ServerSocket socket = new ServerSocket(participant);
            logger.startedListening();

            return socket;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

    private void establishConnections(List<Integer> otherParticipants, int timeout)
    {
        List<Callable<Socket>> outgoingSockets = new ArrayList<>();
        List<Callable<Socket>> incomingSockets = new ArrayList<>();

        for (Integer participant : otherParticipants)
        {
            Callable<Socket> outgoingSocket = () -> {
                try
                {
                    Socket socket = new Socket("localhost", participant);

                    logger.connectionEstablished(participant);

                    return socket;
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    return null;
                }
            };

            outgoingSockets.add(outgoingSocket);
        }

        for (int i = 0; i < otherParticipants.size(); i++)
        {
            Callable<Socket> incomingSocket = () -> {
                try
                {
                    Socket socket = serverSocket.accept();

                    logger.connectionAccepted(socket.getPort());

                    return socket;
                }
                catch (IOException ex)
                {
                    ex.printStackTrace();
                    return null;
                }
            };

            incomingSockets.add(incomingSocket);
        }

        try
        {
            List<Future<Socket>> futureOutgoing = pollService.invokeAll(outgoingSockets, timeout, TimeUnit.MILLISECONDS);
            List<Future<Socket>> futureIncoming = pollService.invokeAll(incomingSockets, timeout, TimeUnit.MILLISECONDS);

            for (Future<Socket> futureSocket : futureOutgoing)
            {
                outputConnections.put(futureSocket.get().getPort(),
                        new PrintStream(futureSocket.get().getOutputStream()));
            }

            for (Future<Socket> futureSocket : futureIncoming)
            {
                inputConnections.put(futureSocket.get().getPort(),
                        new BufferedReader(new InputStreamReader(futureSocket.get().getInputStream())));
            }
        }
        catch (CancellationException ex)
        {
            // TODO: Handle timeouts.
        }
        catch (InterruptedException | ExecutionException | IOException ex)
        {
            ex.printStackTrace();
        }
    }
}
