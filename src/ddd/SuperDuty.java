package ddd;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import problem.Instance;
import problem.Trip;

/**
 * Class for modeling a connected component of duties
 */
public class SuperDuty {
	
	private Set<Duty> duties;
	private List<Trip> trips;
	private Set<Duty> optimalDecomposition;
	private Set<Duty> selectedFeasibleDuties;
	private Set<Duty> selectedInfeasibleDuties;
	private Set<Trip> served;
	private Set<Trip> unserved;

	public SuperDuty(Set<List<TimedArc>> allPaths, Instance inst, Map<TimedArc, Integer> compo) {
		duties = new LinkedHashSet<>();
		trips = new ArrayList<>();
		Set<Trip> tripSet = new HashSet<>();
		for(List<TimedArc> path: allPaths) {
			Duty d = new Duty(path,inst,false);
			duties.add(d);
			tripSet.addAll(d.getTrips());
		}
		trips = new ArrayList<>(tripSet);
		Collections.sort(trips);
		for(TimedArc a: compo.keySet()) {
			if(a.getFrom().getStation().isDepot()) {
				compo.get(a);
			}
		}
	}
	
	public Set<List<TimedNode>> feasibilityCheck(int maxDeviation, boolean aggresive, boolean print) throws IloException {
		Set<List<TimedNode>> toAdd = new HashSet<>();
		for(Duty d: duties) {
			d.feasibilityCheck(maxDeviation, print);
			if(!d.isFeasible()) {
			}
		}
		optimizeDecomposition();
		selectedFeasibleDuties = new HashSet<>();
		selectedInfeasibleDuties = new HashSet<>();
		for(Duty d: optimalDecomposition) {
			if(!d.isFeasible()) {
				toAdd.add(d.getSuggestedTimePoints());
				selectedInfeasibleDuties.add(d);
			} else {
				selectedFeasibleDuties.add(d);
			}
		}
	
		//determine the served and unserved trips
		served = new HashSet<>();
		unserved = new HashSet<>(trips);
		for(Duty d: selectedFeasibleDuties) {
			served.addAll(d.getTrips());
		}
		unserved.removeAll(served);
		
		if(unserved.isEmpty()||!aggresive) {
			return toAdd;
		}
		toAdd = new HashSet<>();
		for(Duty d: duties) {
			if(!d.isFeasible()) {
				toAdd.add(d.getSuggestedTimePoints());
			}
		}
		return toAdd;
	}

	/**
	 * Method that optimizes the path decomposition with respect to the number of time points 
	 * @throws IloException
	 */
	private void optimizeDecomposition() throws IloException {
		IloCplex cplex = new IloCplex();
		Map<Duty,IloNumVar> dutyToVar = new LinkedHashMap<>();
		IloNumExpr obj = cplex.constant(0);
		cplex.setOut(null);
		for(Duty d: duties) {
			dutyToVar.put(d, cplex.boolVar());
			if(!d.isFeasible()) {
				obj = cplex.sum(obj,cplex.prod(d.getSuggestedTimePoints().size(),dutyToVar.get(d)));
			}
		}
		cplex.setParam(IloCplex.Param.RandomSeed, 1);
		cplex.addMinimize(obj); //minimize nr added points
		for(Trip t: trips) {
			IloNumExpr lhs = cplex.constant(0);
			for(Duty d: duties) {
				if(d.getTrips().contains(t)) {
					lhs = cplex.sum(lhs,dutyToVar.get(d));
				}
			}
			cplex.addEq(lhs,1,"t"+t.getID());
		}
		//cplex.exportModel("my.lp");
		cplex.solve();
		
		//read solution
		optimalDecomposition = new LinkedHashSet<>();
		for(Duty d: duties) {
			double val = cplex.getValue(dutyToVar.get(d));
			if(val>0.999) {
				optimalDecomposition.add(d);
			}
		}
		
		cplex.clearModel();
		cplex.end();
	}

	public Set<Trip> getServedTrips() {
		return served;
	}
	public Set<Trip> getUnservedTrips() {
		return unserved;
	}

	public Set<Duty> getSelectedFeasibleDuties() {
		return selectedFeasibleDuties;
	}

	public Set<Duty> getSelectedInfeasibleDuties() {
		return selectedInfeasibleDuties;
	}

	public int getNrPaths() {
		return duties.size();
	}

	public List<Trip> getTrips() {
		// TODO Auto-generated method stub
		return trips;
	}

	/**
	 * Optimize the path decomposition in a post-processing step wrt the total deviation
	 * @param minimize
	 * @return
	 * @throws IloException
	 */
	public Set<Duty> optimalPostProcessing(boolean minimize) throws IloException {
		IloCplex cplex = new IloCplex();
		Map<Duty,IloNumVar> dutyToVar = new HashMap<>();
		IloNumExpr obj = cplex.constant(0);
		cplex.setOut(null);
		for(Duty d: duties) {
			if(d.isFeasible()) {
				dutyToVar.put(d, cplex.boolVar());
				d.postProcess(false);
				obj = cplex.sum(obj,cplex.prod(d.getTotalDeviation(),dutyToVar.get(d)));
			}
		}
		if(minimize) {
			cplex.addMinimize(obj);
		} else {
			cplex.addMaximize(obj);
		}
		for(Trip t: trips) {
			IloNumExpr lhs = cplex.constant(0);
			for(Duty d: duties) {
				if(d.isFeasible()&&d.getTrips().contains(t)) {
					lhs = cplex.sum(lhs,dutyToVar.get(d));
				}
			}
			cplex.addEq(lhs,1);
		}
		cplex.setParam(IloCplex.Param.RandomSeed, 1);

		cplex.solve();
		
		//read solution
		Set<Duty> optDecomp = new HashSet<>();
		for(Duty d: duties) {
			if(d.isFeasible()) {
				double val = cplex.getValue(dutyToVar.get(d));
				if(val>0.999) {
					optDecomp.add(d);
				}
			}
		}
		
		cplex.clearModel();
		cplex.end();
		return optDecomp;
	}
	

}
