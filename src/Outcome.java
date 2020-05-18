import java.util.Collections;
import java.util.List;

public class Outcome extends Vote
{
    private final List<Integer> otherParticipants;

	public Outcome(int participantPort, String vote, List<Integer> otherParticipants)
    {
        super(participantPort, vote);

        Collections.sort(otherParticipants);
        this.otherParticipants = otherParticipants;
    }

    public List<Integer> getOtherParticipants() { return otherParticipants; }
}
