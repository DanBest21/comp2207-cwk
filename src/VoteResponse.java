import java.util.ArrayList;
import java.util.List;

public class VoteResponse
{
    private final int participant;
    private final List<Vote> votes = new ArrayList<>();

    public VoteResponse(int participant)
    {
        this.participant = participant;
    }

    public void setVotes(List<Vote> votes)
    {
        this.votes.addAll(votes);
    }

    public int getParticipant()
    {
        return participant;
    }

    public List<Vote> getVotes()
    {
        return votes;
    }
}
