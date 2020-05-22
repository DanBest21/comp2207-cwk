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

    private final Map<Integer, PrintStream> outputConnections = Collections.synchronizedMap(new HashMap<>());
    private final Map<Integer, BufferedReader> inputConnections = Collections.synchronizedMap(new HashMap<>());

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

        this.pollService = Executors.newWorkStealingPool();

        establishConnections(otherParticipants);
    }

    public Outcome holdElection()
    {
        ScheduledExecutorService roundService = Executors.newSingleThreadScheduledExecutor();

        for (int i = 1; i <= numberOfRounds; i++)
        {
            int roundNumber = i;

            Callable<Boolean> round = () -> {
                startRound(roundNumber);
                return true;
            };

            Future<Boolean> futureBlock = roundService.schedule(round, timeout, TimeUnit.MILLISECONDS);

            try
            {
                Boolean waitForRound = futureBlock.get();
            }
            catch (InterruptedException | ExecutionException ex)
            {
                ex.printStackTrace();
            }
        }

        roundService.shutdown();
        pollService.shutdown();

        List<Integer> voters = collectedVotes.stream().map(Vote::getParticipantPort).sorted().collect(Collectors.toList());

        return new Outcome(participant, decideOutcome(collectedVotes, voters), voters);
    }

    public void startRound(int roundNumber)
    {
        logger.beginRound(roundNumber);

        Callable<Boolean> sendVotes = () -> {
            for (int portNumber : outputConnections.keySet())
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

            return true;
        };

        List<Callable<VoteResponse>> incomingVotes = new ArrayList<>();

        for (int portNumber : inputConnections.keySet())
        {
            Callable<VoteResponse> retrieveVotes = () -> {
                MessageParser parser = new MessageParser();

                String message = inputConnections.get(portNumber).readLine();

                if (roundNumber != 1)
                    logger.messageReceived(portNumber, message);

                List<Vote> retrievedVotes = parser.parseVotes(message);
                VoteResponse voteResponse;

                if (roundNumber == 1 && retrievedVotes.size() == 1)
                {
                    BufferedReader reader = inputConnections.remove(portNumber);

                    int participant = retrievedVotes.get(0).getParticipantPort();

                    inputConnections.put(participant, reader);

                    logger.messageReceived(participant, message);
                    logger.votesReceived(participant, retrievedVotes);

                    voteResponse = new VoteResponse(participant);
                    voteResponse.setVotes(retrievedVotes);
                }
                else if (!retrievedVotes.isEmpty())
                {
                    logger.votesReceived(portNumber, retrievedVotes);

                    voteResponse = new VoteResponse(portNumber);
                    voteResponse.setVotes(retrievedVotes);
                }
                else
                {
                    voteResponse = new VoteResponse(portNumber);
                }

                return voteResponse;
            };

            incomingVotes.add(retrieveVotes);
        }

        Future<Boolean> futureSent = pollService.submit(sendVotes);

        try
        {
            List<Future<VoteResponse>> futureVotes =
                    pollService.invokeAll(incomingVotes, timeout, TimeUnit.MILLISECONDS);

            List<Integer> participantsResponded = new ArrayList<>();

            boolean messagesSent = futureSent.get();

            newVotes.clear();

            List<VoteResponse> voteResponses = new ArrayList<>();

            for (Future<VoteResponse> futureVote : futureVotes)
            {
                try
                {
                    VoteResponse voteResponse = futureVote.get();
                    voteResponses.add(voteResponse);
                }
                catch (CancellationException ignored) { }
            }

            for (VoteResponse voteResponse : voteResponses)
            {
                List<Vote> retrievedVotes = voteResponse.getVotes();

                participantsResponded.add(voteResponse.getParticipant());

                newVotes.addAll(retrievedVotes.stream()
                        .filter(vote -> !collectedVotes
                                .stream()
                                .map(Vote::getParticipantPort)
                                .collect(Collectors.toList())
                                .contains(vote.getParticipantPort()))
                        .filter(vote -> !newVotes
                                .stream()
                                .map(Vote::getParticipantPort)
                                .collect(Collectors.toList())
                                .contains(vote.getParticipantPort()))
                        .collect(Collectors.toList()));
            }

            List<Integer> crashedParticipants = new ArrayList<>();

            for (int participant : inputConnections.keySet())
            {
                if (!participantsResponded.contains(participant))
                {
                    logger.participantCrashed(participant);
                    crashedParticipants.add(participant);
                }
            }

            for (int participant : crashedParticipants)
            {
                inputConnections.remove(participant);
                outputConnections.remove(participant);
            }

            collectedVotes.addAll(newVotes);
        }
        catch (InterruptedException | ExecutionException ex)
        {
            ex.printStackTrace();
        }

        logger.endRound(roundNumber);
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

    private void establishConnections(List<Integer> otherParticipants)
    {
        List<Callable<Integer>> callablePorts = new ArrayList<>();

        for (Integer participant : otherParticipants)
        {
            Callable<Integer> outgoingSocket = () -> {
                try
                {
                    Socket socket = new Socket("localhost", participant);

                    logger.connectionEstablished(participant);

                    outputConnections.put(participant,
                            new PrintStream(socket.getOutputStream()));

                    return participant;
                }
                catch (CancellationException | ConnectException ex)
                {
                    return null;
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    return null;
                }
            };

            callablePorts.add(outgoingSocket);
        }

        for (int i = 0; i < otherParticipants.size(); i++)
        {
            Callable<Integer> incomingSocket = () -> {
                try
                {
                    Socket socket = serverSocket.accept();

                    logger.connectionAccepted(socket.getPort());

                    inputConnections.put(socket.getPort(),
                            new BufferedReader(new InputStreamReader(socket.getInputStream())));

                    return socket.getPort();
                }
                catch (IOException ex)
                {
                    ex.printStackTrace();
                    return null;
                }
            };

            callablePorts.add(incomingSocket);
        }

        try
        {
            List<Future<Integer>> futurePortNumbers = pollService.invokeAll(callablePorts, timeout, TimeUnit.MILLISECONDS);
            List<Integer> portNumbers = new ArrayList<>();

            for (Future<Integer> futureParticipant : futurePortNumbers)
            {
                try
                {
                    int portNumber = futureParticipant.get();
                    portNumbers.add(portNumber);
                }
                catch (CancellationException ignored) { }
                catch (InterruptedException | ExecutionException ex)
                {
                    ex.printStackTrace();
                }
            }

            for (int participant : otherParticipants)
            {
                if (!portNumbers.contains(participant))
                    logger.participantCrashed(participant);
            }
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
        }
    }
}
