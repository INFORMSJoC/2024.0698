package bnp;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ilog.concert.IloColumn;
import ilog.concert.IloConstraint;
import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import ilog.concert.IloObjective;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;
import problem.Instance;
import problem.Location;
import problem.Trip;
import util.Pair;
import util.Triple;

/**
 * Class for representing the master problem in branch-and-price
 */
public class MasterProblem {
	private Instance inst;
	private IloCplex cplex;
	private IloObjective obj; 
	
	private Set<Route> routeSet;
	private Map<Route,IloNumVar> varMap;
	private Map<Trip,IloRange> coveringConstraints;
	private IloConstraint[] rngArray;
	
	private BNP_Node bnpNode;
	private Set<Route> routeSetPositive;
	
	public MasterProblem(Instance inst) throws IloException {
		this.inst = inst;
		inititalize();
		routeSet = new LinkedHashSet<>();
		varMap = new LinkedHashMap<>();
	}
	
	public void inititalize() throws IloException {
		cplex = new IloCplex();
		obj = cplex.addMinimize();
		coveringConstraints = new LinkedHashMap<>();
		for(Trip t: inst.getTrips()) {
			IloRange rng = cplex.addEq(cplex.constant(0),1);
			coveringConstraints.put(t,rng);
		}
		
		rngArray = new IloConstraint[inst.getNumTrips()];
		int i = 0;
		for(Trip t: inst.getTrips()) {
			rngArray[i] = coveringConstraints.get(t);
			i++;
		}
		//turn off presolve
		cplex.setParam(IloCplex.Param.Preprocessing.Presolve, true);
		cplex.setOut(null);
	}
	
	public void removeCoveringConstraints() throws IloException {
		for(Trip t: inst.getTrips()) {
			cplex.remove(coveringConstraints.get(t));
		}
	}
	
	public void setBounds(BNP_Node bNode) throws IloException {
		this.bnpNode = bNode;
		this.cleanup();
		inititalize();
		varMap = new LinkedHashMap<>();
		System.out.println("Route set contains "+routeSet.size()+" routes");
		for(Route r: routeSet) {
			//check if it contains a forbidden arc
			boolean forbidden = false;
			for(CN_Arc a: r.getArcs()) {
				if(bNode.getAtLowerBound().get(r.getDepot()).contains(a)) {
					forbidden = true;
					break;
				} 
			}
			if(!forbidden) {
				//add variable to model
				addColumn(r);
			} 
		}
	}
	
	public void addColumn(Route r) throws IloException {
		IloColumn col = cplex.column(obj, r.getCost());
		for(Trip t: r.getTrips()) {
			col = col.and(cplex.column(coveringConstraints.get(t), 1));
		}
		varMap.put(r, cplex.numVar(col, 0, Double.MAX_VALUE));
		routeSet.add(r);
		for(CN_Arc a: r.getArcs()) {
			if(bnpNode!=null&&bnpNode.getAtLowerBound().get(r.getDepot()).contains(a)) {
				throw new Error("Route is forbidden!");
			}
		}
	}
	
	public void removeColumn(Route r) throws IloException { 
		cplex.remove(varMap.get(r));
		routeSet.remove(r);
	}
	
	public Map<Trip,Double> getDuals() throws IloException {
		Map<Trip,Double> duals = new LinkedHashMap<>();
		
		for(Trip t: inst.getTrips()) {
			duals.put(t,cplex.getDual(coveringConstraints.get(t)));
		}
		return duals;
	}
	
	public Map<Trip,Double> getFarkasDuals() throws IloException {
		cplex.setParam(IloCplex.Param.Preprocessing.Presolve, false);
		cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Dual);
		cplex.solve();
		Map<Trip,Double> duals = new LinkedHashMap<>();
		
		double[] dualArray = new double[inst.getNumTrips()];
		cplex.dualFarkas(rngArray, dualArray);
		
		int i = 0;
		for(Trip t: inst.getTrips()) {
			duals.put(t,dualArray[i]);
			i++;
		}
		cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Primal);
		return duals;
	}
	
	public void cleanup() throws IloException 
	{
		cplex.clearModel();
		cplex.end();
	}


	public boolean isFeasible() throws IloException {
		//System.out.println("Status: "+cplex.getStatus());
		return !(cplex.getStatus().equals(IloCplex.Status.Infeasible));
	}

	public void solve() throws IloException {
		cplex.solve();
	}
	public double getObj() throws IloException {
		return cplex.getObjValue();
	}

	public Triple<Location,Location,Trip> getColorBranching() throws IloException {
		Triple<Location,Location,Trip> mostFrac = null;
		double mostFracVal = 0;
		for(Location d1: inst.getDepots()) {
			for(Location d2: inst.getDepots()) {
				if(d1.getIndex()>=d2.getIndex()) {
					continue;
				}
				//d1<d2
				Pair<Trip,Double> mostFracPairD1D2 = getColorBranching(d1,d2);
				if(mostFracPairD1D2.getB()>mostFracVal) {
					mostFracVal = mostFracPairD1D2.getB();
					mostFrac = new Triple<>(d1,d2,mostFracPairD1D2.getA());
				}
			}
		}
		if(mostFracVal<1e-3) {
			//return null in case integer
			System.out.println("No colour branching possible!");
			return null;
		}
		return mostFrac;
	}
	
	
	private Pair<Trip, Double> getColorBranching(Location d1, Location d2) throws UnknownObjectException, IloException {
		Map<Trip,Double> tripVals = new LinkedHashMap<>();
		Map<Trip,Set<Location>> posCounter = new LinkedHashMap<>();
		for(Entry<Route,IloNumVar> ent: varMap.entrySet()) {
			if(!ent.getKey().getDepot().equals(d1)&&!ent.getKey().getDepot().equals(d2)) {
				continue;
			}
			double val = cplex.getValue(ent.getValue());
			if(val<1e-3) {
				continue;
			}
			Route r = ent.getKey();
			for(Trip a: r.getTrips()) {
				if(tripVals.containsKey(a)) {
					tripVals.put(a, tripVals.get(a)+val);
					posCounter.get(a).add(r.getDepot());
				} else {
					tripVals.put(a, val);
					posCounter.put(a, new HashSet<>());
					posCounter.get(a).add(r.getDepot());
				}
			}
		}
		double mostFracVal = 0;
		Trip mostFrac = null;
		for(Entry<Trip,Double> ent: tripVals.entrySet()) {
			//check if positive values for both depots
			if(posCounter.get(ent.getKey()).size()<2) {
				continue;
			}
			
			double flow = ent.getValue();
			double frac = Math.abs(flow-Math.round(flow));
			if(frac>mostFracVal&&frac<0.5+1e-3) {
				mostFracVal = frac;
				mostFrac = ent.getKey();
			}			
		}
		return new Pair<>(mostFrac,mostFracVal);
	}
	
	public Triple<Location,Location,CN_Arc> getInterTaskBranching() throws IloException {
		Triple<Location,Location,CN_Arc> mostFrac = null;
		double mostFracVal = 0;
		for(Location d1: inst.getDepots()) {
			for(Location d2: inst.getDepots()) {
				if(d1.getIndex()>=d2.getIndex()) {
					continue;
				}
				//d1<d2
				Pair<CN_Arc,Double> mostFracPairD1D2 = getInterTaskBranching(d1,d2);
				if(mostFracPairD1D2.getB()>mostFracVal) {
					mostFracVal = mostFracPairD1D2.getB();
					mostFrac = new Triple<>(d1,d2,mostFracPairD1D2.getA());
				}
			}
		}
		if(mostFracVal<1e-3) {
			//return null in case integer
			System.out.println("No intertask branching possible!");
			return null;
		}
		return mostFrac;
	}
	
	private Pair<CN_Arc, Double> getInterTaskBranching(Location d1, Location d2) throws UnknownObjectException, IloException {
		Map<CN_Arc,Double> arcVals = new LinkedHashMap<>();
		Map<CN_Arc,Set<Location>> posCounter = new LinkedHashMap<>();
		for(Entry<Route,IloNumVar> ent: varMap.entrySet()) {
			if(!ent.getKey().getDepot().equals(d1)&&!ent.getKey().getDepot().equals(d2)) {
				continue;
			}
			double val = cplex.getValue(ent.getValue());
			if(val<1e-3) {
				continue;
			}
			Route r = ent.getKey();
			for(CN_Arc a: r.getArcs()) {
				if(a.getType()==0) {
					//only check trip arcs
					if(arcVals.containsKey(a)) {
						arcVals.put(a, arcVals.get(a)+val);
						posCounter.get(a).add(r.getDepot());
					} else {
						arcVals.put(a, val);
						posCounter.put(a, new HashSet<>());
						posCounter.get(a).add(r.getDepot());
					}
					
				}
				
			}
		}
		double mostFracVal = 0;
		CN_Arc mostFrac = null;
		for(Entry<CN_Arc,Double> ent: arcVals.entrySet()) {
			//check if positive values for both depots
			if(posCounter.get(ent.getKey()).size()<2) {
				continue;
			}
			
			double flow = ent.getValue();
			double frac = Math.abs(flow-Math.round(flow));
			if(frac>mostFracVal&&frac<0.5+1e-3) {
				mostFracVal = frac;
				mostFrac = ent.getKey();
			}			
		}
		return new Pair<>(mostFrac,mostFracVal);
	}

	public Pair<Location,CN_Arc> getMostFractionalArc() throws IloException {
		Pair<Location,CN_Arc> mostFrac = null;
		double mostFracVal = 0;
		for(Location d: inst.getDepots()) {
			Pair<CN_Arc,Double> mostFracPairD = getMostFractionalArc(d);
			if(mostFracPairD.getB()>mostFracVal) {
				mostFracVal = mostFracPairD.getB();
				mostFrac = new Pair<>(d,mostFracPairD.getA());
			}
		}
		if(mostFracVal<1e-3) {
			//return null in case integer
			System.out.println("Solution integer!");
			return null;
		}
		return mostFrac;
	}
	
	public void computePositiveRouteSet() throws UnknownObjectException, IloException {
		routeSetPositive = new LinkedHashSet<>();
		for(Entry<Route,IloNumVar> ent: varMap.entrySet()) {
			double val = cplex.getValue(ent.getValue());
			Route r = ent.getKey();
			if(val>1e-3) {
				routeSetPositive.add(r);
			}
		}
	}

	/**
	 * Method that finds the most fractional variable belonging to some depot layer
	 * @throws IloException 
	 * @throws  
	 */
	private Pair<CN_Arc, Double> getMostFractionalArc(Location d) throws IloException {
		Map<CN_Arc,Double> arcVals = new LinkedHashMap<>();
		for(Entry<Route,IloNumVar> ent: varMap.entrySet()) {
			if(!ent.getKey().getDepot().equals(d)) {
				continue;
			}
			double val = cplex.getValue(ent.getValue());
			Route r = ent.getKey();
			for(CN_Arc a: r.getArcs()) {
				if(bnpNode.getAtLowerBound().get(r.getDepot()).contains(a)) {
					throw new Error("Route is forbidden!");
				}
				if(a.getType()==0) {
					//only check trip arcs
					if(arcVals.containsKey(a)) {
						arcVals.put(a, arcVals.get(a)+val);
					} else {
						arcVals.put(a, val);
					}
					
				}
				
			}
		}
		double mostFracVal = 0;
		CN_Arc mostFrac = null;
		for(Entry<CN_Arc,Double> ent: arcVals.entrySet()) {
			double flow = ent.getValue();
			double frac = Math.abs(flow-Math.round(flow));
			if(frac>mostFracVal&&frac<0.5+1e-3) {
				mostFracVal = frac;
				mostFrac = ent.getKey();
			}			
		}
		return new Pair<>(mostFrac,mostFracVal);
	}

	public boolean fixLargestFractional() throws IloException {
		Route largestFrac = null;
		double largestFracValue = 0;
		for(Route r: varMap.keySet()) {
			double val = cplex.getValue(varMap.get(r));
			if(val>largestFracValue&&val<1-1e-4) {
				largestFrac = r;
				largestFracValue = val;
			}
		}
		if(largestFrac == null) {
			System.out.println("Solution integer");
			return true;
		} else {
			cplex.addGe(varMap.get(largestFrac), 1);
			return false;
		}
		
	}

	public Set<Route> getPositiveRoutes() {
		return this.routeSetPositive;
	}






}
