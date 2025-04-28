package problem;

/**
 * Class for implementing a trip (input for the MD-VSP) *
 */
public class Trip implements Comparable<Trip>
{
	private final int id;
	private int startTime;
	private Location startLocation;
	private int endTime;
	private Location endLocation;
	
	
	public Trip(int id, Location startLocation, int startTime, Location endLocation, int endTime)
	{
		this.id = id;
		this.startLocation = startLocation;
		this.startTime = startTime;
		this.endLocation = endLocation;
		this.endTime = endTime;

	}
		
	public int getStartTime()
	{
		return startTime;
	}
	
	public Location getStartLocation()
	{
		return startLocation;
	}

	public int getEndTime()
	{
		return endTime;
	}
	
	public Location getEndLocation()
	{
		return endLocation;
	}
	
	public int getTripTime() {
		return endTime - startTime;
	}
	
	@Override
	public String toString()
	{
		return "Trip "+id;
	}
	
	@Override
	public int compareTo(Trip t)
	{
		if(this.getStartTime()==t.getStartTime()) {
			return this.getID()-t.getID();
		}
		return this.getStartTime() - t.getStartTime();
	}

	public int getID() {
		return id;
	}
}
