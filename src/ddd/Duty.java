package ddd;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ilog.concert.IloException;
import problem.Instance;
import problem.Location;
import problem.Trip;


/**
 * Class used for modeling a duty, which is characterized both by a path in the TS-network, and a sequence of trips. 
 */
public class Duty {
	private Instance inst;
	private boolean isCycle; 
	private List<TimedArc> schedule;
	private Location depot;
	private final List<Trip> trips;
	
	
	private Map<TimedArc, Integer> actualDeparture;
	private List<TimedTrip> timedTrips;
	private int modelCosts;
	private int actualCosts;
	private boolean feasible;
	private List<TimedNode> suggestedTimePoints;
	private int totalDeviation;
	private int idleTime;
	private int drivingTime;

	private static int printDetail = 1;
	
	public Duty(List<TimedArc> arcs, Instance inst, boolean isCycle) {
		this.schedule = arcs;
		this.depot = arcs.get(0).getFrom().getStation();
		this.inst = inst;
		this.isCycle = isCycle;
		trips = new ArrayList<>();
		for (TimedArc a : schedule) {
			if (a.getType() == TimedArc.TRIP_ARC) {
				trips.add(a.getTrip());
			}
		}
	}
	
	/**
	 * Method that checks whether a sequence of trips is feasible, and returns a list of timednodes if not
	 */
	public List<TimedNode> feasibilityCheck(int maxDeviation, boolean print)
	{
		if(isCycle) {
			return newTimePointCycle();
		}
		feasible = true;
		modelCosts = 0;
		actualDeparture = new HashMap<>();
		int time = 0;
		List<TimedNode> toAdd = new ArrayList<>();

		Location curLoc = depot;
		if(print) {
			System.out.println("Depot; "+depot.getIndex() + " nr in schedule: "+schedule.size());
		}
		for (TimedArc a : schedule)
		{	
			modelCosts += a.getCost();
			if(a.getFrom().getStation()!=curLoc) {
				throw new Error("Not feasible");
			}
			curLoc = a.getTo().getStation();
			
			
			actualDeparture.put(a, time);
			if(printDetail>1||print) {
				System.out.println("Time = " + time + ". Scheduled departure = " + a.getFrom().getTime() + ". Arc = " + a);
			}
			
			toAdd.add(new TimedNode(a.getFrom().getStation(),time));

			if (a.getType() == TimedArc.TRIP_ARC)
			{
				if(print) {
					System.out.println("Trip: "+a.getTrip());
				}
				if (time > a.getTrip().getStartTime() + maxDeviation)
				{
					// Infeasible!
					if(printDetail>2||print) {
						System.out.println("Route is infeasible!");
					}
					feasible = false;
					suggestedTimePoints = toAdd;
					return toAdd;
				} else if (time < a.getTrip().getStartTime() - maxDeviation) {
					//as early as possible, no part before is always feasible
					toAdd = new ArrayList<>();
				}

				time = Math.max(time,  a.getTrip().getStartTime() - maxDeviation) + a.getTravelTime();
			}
			else
			{
				time += a.getTravelTime();
			}
		}
		toAdd = new ArrayList<>();
		if(curLoc!=depot) {
			for (TimedArc a : schedule) {
				System.out.println(a);
			}

			throw new Error("Not feasible");
		}
		return toAdd;
	}
	
	/**
	 * Method that finds time points that break a cycle
	 */
	private List<TimedNode> newTimePointCycle() {

		List<TimedNode> newPoints = new ArrayList<>();
		for(TimedArc a: schedule) {
			int under = a.getTravelTime()-(a.getTo().getTime()-a.getFrom().getTime());
			if(under>0) {
				newPoints.add(new TimedNode(a.getTo().getStation(),a.getFrom().getTime()+a.getTravelTime()));
			}
		}
		return newPoints;
	}

	/**
	 * Method that post-processes a duty
	 */
	public void postProcess(boolean print) throws IloException {
		ConversionModel conMod = new ConversionModel(inst, this,true);
		conMod.solve();
		timedTrips = conMod.getDepartureTimeSolution();
		conMod.cleanup();
		
		int time = 0;
		Location curLocation = depot;
		totalDeviation = 0;
		idleTime = 0;
		for(TimedTrip t: timedTrips) {
			totalDeviation += Math.abs(t.getDepTime()-t.getT().getStartTime());
			int deadhead = curLocation.getTimeTo(t.getT().getStartLocation());
			if(deadhead==10000) {
				System.out.println("No dh in data set");
			}
			if(print) {
				System.out.println("To Trip: "+t.getT()+" costs= "+(Instance.VARIABLE_COST*deadhead));
				System.out.println("Dh: "+deadhead +" dep time: "+t.getDepTime());
			}
			if(time+deadhead>t.getDepTime()) {
				//not feasible
				throw new Error();
			} else if(curLocation!=depot) {
				idleTime += t.getDepTime()-time-deadhead;
			}
			curLocation = t.getT().getEndLocation();
			time = t.getDepTime()+t.getT().getTripTime();
		}
		int pullin = depot.getTimeFrom(curLocation);
		if(print) {
			System.out.println("To depot costs= "+(Instance.VARIABLE_COST*pullin));
		}

	}
	
	public int size() {
		return schedule.size();
	}

	public Map<TimedArc, Integer> getActualDeparture() {
		return actualDeparture;
	}

	public List<TimedArc> getArcs() {
		return schedule;
	}

	public Location getDepot() {
		return depot;
	}

	public int getModelCosts() {
		if(!feasible) {
			throw new Error("Duty not feasible, so no costs");
		}
		return modelCosts;
	}

	public List<TimedTrip> getTimedTrips() {
		return timedTrips;
	}
	
	public List<Trip> getTrips() {
		return trips;
	}

	public void printDuty() {
		System.out.println("Start duty");
		for(TimedTrip t: timedTrips) {
			System.out.println("Trip: "+t.getT());
			System.out.println("Scheduled at: "+t.getDepTime());
		}
		System.out.println("End duty");
	}

	public boolean isCycle() {
		return isCycle;
	}

	public boolean isFeasible() {
		return feasible;
	}

	public List<TimedNode> getSuggestedTimePoints() {
		return suggestedTimePoints;
	}

	public int getTotalDeviation() {
		// TODO Auto-generated method stub
		return totalDeviation;
	}

	public int getActualCosts() {
		return actualCosts; 
	}

	public void computeActualCosts() {
		actualCosts = Instance.FIXED_COST*2;
		Location curLocation = depot;
		for(Trip t: trips) {
			int deadhead = curLocation.getTimeTo(t.getStartLocation());
			actualCosts+= Instance.VARIABLE_COST*deadhead;
			curLocation = t.getEndLocation();
		}
		int pullin = depot.getTimeFrom(curLocation);
		actualCosts += Instance.VARIABLE_COST*pullin;
		
		curLocation = depot;
		if(actualCosts>modelCosts) {
			for(TimedArc a: schedule) {
				System.out.println(a);
			}
			for(Trip t: trips) {
				curLocation = t.getEndLocation();
			}

			throw new Error();
		}
		
	}

	public int getDrivingTime() {
		drivingTime = 0;
		Location curLocation = depot;
		for(Trip t: trips) {
			int deadhead = curLocation.getTimeTo(t.getStartLocation());
			drivingTime+= deadhead;
			curLocation = t.getEndLocation();
		}
		drivingTime += depot.getTimeFrom(curLocation);
		return drivingTime;
	}	
	
}
