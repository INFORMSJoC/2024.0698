package ddd;
import java.util.Objects;

import problem.Location;

/**
 * Class for implementing a node in the timespace network. 
 */
public class TimedNode  implements Comparable<TimedNode>
{
	private final Location station;
	private final int time;
	
	public TimedNode(Location station, int time)
	{
		this.station = station;
		this.time = time;
	}
	
	public boolean isDepot()
	{
		return station.isDepot();
	}
	
	public Location getStation()
	{
		return station;
	}
	
	public int getTime()
	{
		return time;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(station, time);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TimedNode other = (TimedNode) obj;
		return Objects.equals(station, other.station) && time == other.time;
	}

	@Override
	public String toString()
	{
		return station.getIndex() + " (" + time + ")";
	}

	@Override
	public int compareTo(TimedNode o) {
		// TODO Auto-generated method stub
		return this.time - o.time;
	}
}
