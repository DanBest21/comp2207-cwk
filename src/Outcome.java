public class Outcome extends Vote
{
    private final int[] otherParticipants;

	public Outcome(int participantPort, String vote, int[] otherParticipants)
    {
        super(participantPort, vote);
        this.otherParticipants = otherParticipants;
    }

    public int[] getOtherParticipants() { return otherParticipants; }
}
