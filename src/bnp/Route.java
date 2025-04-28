package bnp;

import java.util.List;
import java.util.Objects;

import ddd.Duty;
import problem.Instance;
import problem.Location;
import problem.Trip;

/**
 * Class for modeling a route in the branch-and-price algorithm *
 */
public class Route {
	private final Location depot;
	private final List<Trip> trips;
	private final int cost;
	private List<CN_Arc> arcs;
	
	public Route(Location depot, List<Trip> trips, List<CN_Arc> arcs) {
		super();
		this.depot = depot;
		this.trips = trips;
		this.cost = computeCosts();
		this.arcs = arcs;
	}
	
	public Route(Duty d) {
		this.depot = d.getDepot();
		this.trips = d.getTrips();
		this.cost = computeCosts();
	}

	private int computeCosts() {
		int curTime = 0;
		int costTemp = Instance.FIXED_COST*2;
		Location curLocation = depot;
		for(Trip t: trips) {
			int deadhead = curLocation.getTimeTo(t.getStartLocation());
			costTemp+= Instance.VARIABLE_COST*deadhead;
			curLocation = t.getEndLocation();
			int arrTime = curTime+deadhead;
			if(arrTime>t.getStartTime()+5) {
				throw new Error("Infeasible");
			} else { 
				curTime = Math.min(arrTime,  t.getStartTime()-5);
				curTime += t.getTripTime();
			}
		}
		int pullin = depot.getTimeFrom(curLocation);
		costTemp += Instance.VARIABLE_COST*pullin;
		return costTemp;
	}

	public int getCost() {
		return cost;
	}

	public List<Trip> getTrips() {
		return trips;
	}

	@Override
	public String toString() {
		return "Route [depot=" + depot.getIndex() + ", trips=" + trips + ", cost=" + cost + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(cost, depot, trips);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Route other = (Route) obj;
		return cost == other.cost && Objects.equals(depot, other.depot) && Objects.equals(trips, other.trips);
	}

	public Location getDepot() {
		return depot;
	}

	public boolean containsArc(CN_Arc a) {
		return arcs.contains(a);
	}

	public List<CN_Arc> getArcs() {
		return arcs;
	}
	
	
	
	
}
