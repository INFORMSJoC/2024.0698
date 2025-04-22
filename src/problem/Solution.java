package problem;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ddd.Duty;
import ddd.TimedTrip;
import ilog.concert.IloException;

/**
 * Class for representing a solution to the MDVSP-TS
 */
public class Solution {
	
	private Instance inst;
	private Set<Duty> duties;
	private int cost;
	
	public Solution(Instance inst, Map<Location, List<Duty>> schedules) {
		this.inst = inst; 
		duties = new LinkedHashSet<>();
		
		for(Location depot : schedules.keySet()) {
			for(Duty d: schedules.get(depot)) {
				duties.add(d);
			}
		}
	}
	
	public Solution(Instance inst, Set<Duty> schedules) {
		this.inst = inst; 
		duties = schedules;
	}
	
	public void computeCosts() throws IloException {
		cost = 0;
		for(Duty d: duties) {
			boolean dutyFeasible = (d.feasibilityCheck(inst.getMaxDeviation(), false)).isEmpty();
			if(!dutyFeasible) {
				d.feasibilityCheck(inst.getMaxDeviation(), true);
				throw new Error("Infeasible duty in solution");
			}
			//d.postProcess(false);
			d.computeActualCosts();
			cost += d.getActualCosts();
			//System.out.println(d.getTrips()+" has costs: "+d.getActualCosts());
		}
	}
	
	public boolean isFeasible() throws IloException {
		List<Trip> uncovered = new ArrayList<>(inst.getTrips());
		for(Duty d: duties) {
			d.feasibilityCheck(inst.getMaxDeviation(), false);
			//d.postProcess(false);
			duties.add(d);
			for(Trip t: d.getTrips()) {
				uncovered.remove(t);
			}
		}
		if(!uncovered.isEmpty()) {
			System.out.println("Uncovered has "+uncovered.size() + " trips");
			for(Trip t: uncovered) {
				System.out.println(t);
			}
			return false;
		}
		return true;
	}

	public Set<Duty> getDuties() {
		return duties;
	}

	public int getCosts() throws IloException {
		if(cost==0) {
			computeCosts();
		}
		return cost;
	}
	
	public void printSol(PrintWriter pw) {
		for(Duty d: duties) {
			for(TimedTrip t: d.getTimedTrips()) {
				pw.print("("+t.getT().getID()+", t="+t.getDepTime()+")," );
			}
			pw.println();
			pw.flush();
		}
		pw.close();
	}

	public int getTotalDeviation() throws IloException {
		int dev = 0;
		for(Duty d: duties) {
			d.postProcess(false);
			dev += d.getTotalDeviation();
		}
		return dev;
	}

	public int getDrivingTime() throws IloException {
		computeCosts();
		return cost-Instance.FIXED_COST*2*duties.size();
	}
	
	public int getDrivingTime2() {
		int driving = 0;
		for(Duty d: duties) {
			driving+= d.getDrivingTime();
		}
		return driving;
	}

	public List<TimedTrip> getTimedTrips() {
		List<TimedTrip> ttList = new ArrayList<>();
		for(Duty du: duties) {
			ttList.addAll(du.getTimedTrips());
		}
		return ttList;
	}
	
}
