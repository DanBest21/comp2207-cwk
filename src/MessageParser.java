import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class MessageParser
{
    private StringTokenizer tokenizer;

    public int parseJoinRequest(String message) throws IllegalArgumentException
    {
        if (parseMessage(message, MessageType.JOIN))
            return Integer.parseInt(tokenizer.nextToken());
        else
            return -1;
    }

    public List<Integer> parseDetails(String message) throws IllegalArgumentException
    {
        if (parseMessage(message, MessageType.DETAILS))
        {
            List<Integer> participants = new ArrayList<>();

            while (tokenizer.hasMoreTokens())
            {
                participants.add(Integer.parseInt(tokenizer.nextToken()));
            }

            return participants;
        }
        else
            return null;
    }

    public List<String> parseVoteOptions(String message) throws IllegalArgumentException
    {
        if (parseMessage(message, MessageType.VOTE_OPTIONS))
        {
            List<String> options = new ArrayList<>();

            while (tokenizer.hasMoreTokens())
            {
                options.add(tokenizer.nextToken());
            }

            return options;
        }
        else
            return null;
    }

    public List<Vote> parseVotes(String message) throws IllegalArgumentException
    {
        if (parseMessage(message, MessageType.VOTE))
        {
            List<Vote> votes = new ArrayList<>();

            while (tokenizer.hasMoreTokens())
            {
                votes.add(new Vote(Integer.parseInt(tokenizer.nextToken()), tokenizer.nextToken()));
            }

            return votes;
        }
        else
            return null;
    }

    public Outcome parseOutcome(String message) throws IllegalArgumentException
    {
        if (parseMessage(message, MessageType.OUTCOME))
        {
            String option = tokenizer.nextToken();

            int participant = Integer.parseInt(tokenizer.nextToken());

            List<Integer> otherParticipants = new ArrayList<>();

            while (tokenizer.hasMoreTokens())
            {
                otherParticipants.add(Integer.parseInt(tokenizer.nextToken()));
            }

            return new Outcome(participant, option, otherParticipants);
        }
        else
            return null;
    }

    private boolean parseMessage(String message, MessageType type)
    {
        tokenizer = new StringTokenizer(message);
        return verifyMessageType(type);
    }

    private boolean verifyMessageType(MessageType type) throws IllegalArgumentException
    {
        return MessageType.valueOf(tokenizer.nextToken()) == type;
    }
}