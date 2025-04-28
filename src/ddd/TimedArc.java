package ddd;
import java.util.Objects;

import problem.Instance;
import problem.Trip;

/**
 * Class representing a TimedArc in the timespace network
 * @author 20215535
 *
 */
public class TimedArc implements Comparable<TimedArc>
{
	
	private final TimedNode from;
	private final TimedNode to;
	private final int type; // 1: Trip. 2: Deadheading. 3: Waiting at station. 4: Waiting at depot. 5: Pull-in. 6: Pull-out.
	private final int capacity;
	private final int travelTime;
		
	private int cost;
	private Trip trip;
	
	public final static int TRIP_ARC = 1;
	public final static int DEADHEADING_ARC = 2;
	public final static int WAITING_STATION_ARC = 3;
	public final static int WAITING_DEPOT_ARC = 4;
	public final static int PULLIN_ARC = 5;
	public final static int PULLOUT_ARC = 6;
	
	public TimedArc(TimedNode from, TimedNode to, int type, int capacity, int travelTime)
	{
		this.from = from;
		this.to = to;
		this.type = type;
		this.capacity = capacity;
		this.travelTime = travelTime;
		cost = computeCost(type);
	}
	
	public TimedArc(TimedNode from, TimedNode to, int type, Trip trip, int capacity, int travelTime)
	{
		this.from = from;
		this.to = to;
		this.type = type;
		this.trip = trip;
		this.capacity = capacity;
		this.travelTime = travelTime;
		cost = computeCost(type);
	}
	
	public TimedNode getFrom()
	{
		return from;
	}
	
	public TimedNode getTo()
	{
		return to;
	}
	
	public int getType()
	{
		return type;
	}
	
	public Trip getTrip()
	{
		if (type == 1)
		{
			return trip;
		}
		else
		{
			System.out.println("This is not a trip arc: " + this + ". Actual type = " + type);
			return null;
		}
	}
	
	public int getCapacity()
	{
		return capacity;
	}
	
	public int getTravelTime()
	{
		return travelTime;
	}
	
	/**
	 * Cost is computed according to the Readme file of the Kulkarni et al. (2019) benchmark dataset.
	 * 
	 * @param type
	 */
	public int computeCost(int type)
	{
		int cost = 0;
		
		if (type == 1)
		{
			cost = 0;  //2 * (to.getTime() - from.getTime() - travelTime);
		}
		else if (type == 2)
		{
			//cost = Math.max(8 * travelTime + 2 * (to.getTime() - from.getTime()), 8 * travelTime);
			cost = Instance.VARIABLE_COST * travelTime;// + 2 * (to.getTime() - from.getTime());
		}
		else if (type == 3)
		{
			cost = 0;
		}
		else if (type == 4)
		{
			cost = 0;
		}
		else
		{
			cost = Instance.FIXED_COST + Instance.VARIABLE_COST * travelTime;
		}
		
		return cost;
	}
	
	
	public int getCost()
	{
		return cost;
	}

	@Override
	public int hashCode() {
		return Objects.hash(from, to, trip, type);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TimedArc other = (TimedArc) obj;
		return Objects.equals(from, other.from) && Objects.equals(to, other.to) && Objects.equals(trip, other.trip)
				&& type == other.type;
	}

	@Override
	public String toString()
	{
		String tripInfo = "";
		
		if (type == 1)
		{
			tripInfo = " trip "+ trip.getID() + " travel time " + Integer.toString(travelTime);
		}
		else if (type == 2)
		{
			tripInfo = "type 2 (tt: " + Integer.toString(travelTime) + ")";
		}
		else
		{		
			tripInfo = "type " + Integer.toString(type);
		}
		
		return from.toString() + " --" + tripInfo + "--> " + to.toString() + ". Cost = " + cost;//. Depot = " + from.getDepot().getIndex();
	}

	@Override
	public int compareTo(TimedArc o) {
		return from.compareTo(o.from);
	}
}
