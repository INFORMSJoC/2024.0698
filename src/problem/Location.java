package problem;
import java.util.HashMap;
import java.util.Map;

public class Location 
{
	private int type; // Depot = 0, station = 1
	private int capacity;
	private int index;

	private Map<Location, Integer> timeTo;
	private Map<Location, Integer> timeFrom;
	
	public Location (int type, int capacity, int index)
	{
		this.type = type;
		this.capacity = capacity;
		this.index = index;

		timeTo = new HashMap<>();
		timeFrom = new HashMap<>();
	}
	
	
	
	@Override
	public String toString() {
		return "Location [index=" + index + "]";
	}



	public boolean isDepot()
	{
		if (type == 0)
		{
			return true;
		}
		
		return false;
	}
	
	public int getCapacity()
	{
		return capacity;
	}
	
	public int getIndex()
	{
		return index;
	}
	
	public void addTimeTo(Location to, int time)
	{
		timeTo.put(to, time);
	}
	
	public void addTimeFrom(Location from, int time)
	{
		timeFrom.put(from, time);
	}
	
	public int getTimeTo(Location to) 
	{
		return timeTo.get(to);
	}	
	
	public int getTimeFrom(Location from) 
	{
		return timeFrom.get(from);
	}
}
