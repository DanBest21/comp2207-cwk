import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Outcome extends Vote
{
    private final int[] otherParticipants;

	public Outcome(int participantPort, String vote, int[] otherParticipants)
    {
        super(participantPort, vote);

        this.otherParticipants = otherParticipants;
        Arrays.sort(otherParticipants);
    }

    public int[] getOtherParticipants() { return otherParticipants; }
}
