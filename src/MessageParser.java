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

    public int[] parseDetails(String message) throws IllegalArgumentException
    {
        if (parseMessage(message, MessageType.DETAILS))
        {
            int[] participants = new int[tokenizer.countTokens()];
            int i = 0;

            while (tokenizer.hasMoreTokens())
            {
                participants[i] = Integer.parseInt(tokenizer.nextToken());
                i++;
            }

            return participants;
        }
        else
            return null;
    }

    public String[] parseVoteOptions(String message) throws IllegalArgumentException
    {
        if (parseMessage(message, MessageType.VOTE_OPTIONS))
        {
            String[] options = new String[tokenizer.countTokens()];
            int i = 0;

            while (tokenizer.hasMoreTokens())
            {
                options[i] = tokenizer.nextToken();
                i++;
            }

            return options;
        }
        else
            return null;
    }

    public Vote[] parseVote(String message) throws IllegalArgumentException
    {
        if (parseMessage(message, MessageType.VOTE))
        {
            Vote[] votes = new Vote[tokenizer.countTokens()];
            int i = 0;

            while (tokenizer.hasMoreTokens())
            {
                votes[i] = new Vote(Integer.parseInt(tokenizer.nextToken()), tokenizer.nextToken());
                i++;
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
            int participant = Integer.parseInt(tokenizer.nextToken());
            String option = tokenizer.nextToken();

            int[] otherParticipants = new int[tokenizer.countTokens()];
            int i = 0;

            while (tokenizer.hasMoreTokens())
            {
                otherParticipants[i] = Integer.parseInt(tokenizer.nextToken());
                i++;
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