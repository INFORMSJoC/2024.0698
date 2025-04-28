package ddd;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import problem.Instance;
import problem.Location;
import problem.Trip;

/**
 * Class that represents a MDVSP model in CPLEX.  *
 */
public class MDVSP 
{
	private IloCplex cplex;
	private Instance in;
	private Map<Location,TimeSpaceGraph> graph;

	private boolean oneDepot;
	private boolean relaxation;

	// Decision variables
	private Map<Location, Map<TimedArc, IloNumVar>> X;
	
	//to store the solution
	Map<Location,Map<TimedArc,Integer>> flows;
	private boolean hasCycle;
	
	private static int SEED;
	private static int printDetail = 2; 


	public MDVSP(Instance in, Map<Location,TimeSpaceGraph> graph, boolean oneDepot, boolean relaxation) throws IloException
	{	
		this.cplex = new IloCplex();
		this.in = in;
		this.relaxation = relaxation;

		this.graph = graph;
		this.oneDepot = oneDepot;

		this.X = new LinkedHashMap<>();

		addVariables();
		if(printDetail>1) {
			System.out.println("Variables added!");
		}
		addObjective();
		if(printDetail>1) {
			System.out.println("Objective added!");
		}
		addFlowConservationConstraints();
		if(printDetail>1) {
			System.out.println("Flow conservation constraints added!");
		}
		addCoverConstraints();
		if(printDetail>2) {
			System.out.println("Cover constraints added!");
		}

		if(printDetail>5) {
			cplex.exportModel("mdvsp.lp");
		} 
		if(printDetail==0) {
			cplex.setOut(null);
		}
		cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 1.0e-9);
		cplex.setParam(IloCplex.Param.MIP.Tolerances.AbsMIPGap, 0.99);
		cplex.setParam(IloCplex.Param.RandomSeed, MDVSP.SEED);
	}
	
	public static void setSeed(int seed) throws IloException {
		SEED = seed;
	}
	
	public void setTimeLimit(double timeLimit) throws IloException {
		cplex.setParam(IloCplex.DoubleParam.TimeLimit, timeLimit);
	}
	
	public int getNumVariables() throws IloException
	{
		int numVars = 0;
		
		for (Location l : X.keySet())
		{
			numVars+= X.get(l).size();
		}
		
		return numVars;
	}

	public void addVariables() throws IloException
	{
		int numVars = 0;

		for (Location depot : in.getDepots())
		{
			X.put(depot, new LinkedHashMap<>());

			for (TimedArc a : graph.get(depot).getArcs())
			{
				if (a.getType() == TimedArc.TRIP_ARC)
				{
					if(relaxation) {
						X.get(depot).put(a, cplex.numVar(0, 1));
					} else {
						X.get(depot).put(a, cplex.intVar(0, 1));
					}
				}
				else
				{
					if(relaxation) {
						X.get(depot).put(a, cplex.numVar(0, Double.MAX_VALUE));
					} else {
						X.get(depot).put(a, cplex.intVar(0, Integer.MAX_VALUE));	
					}
				}
				
				numVars++;
			}
			
			if (oneDepot) break;
		}

		if(printDetail>1) {
			System.out.println("Number of variables: " + numVars);
		}
	}

	public void addDepotOutflowConstraints(int max) throws IloException
	{
		IloNumExpr lhs = cplex.constant(0);
		for (Location depot : in.getDepots())
		{
			for (TimedArc a : X.get(depot).keySet())
			{
				if (a.getFrom().isDepot() && a.getFrom().getTime() == 0)
				{
					lhs = cplex.sum(lhs, X.get(depot).get(a));
				}
			}

			if (oneDepot) break;
		}
		cplex.addEq(lhs, max);
	}

	public void addFlowConservationConstraints() throws IloException
	{
		for (Location depot : in.getDepots())
		{		
			for (Location l : in.getLocations())
			{
				if(graph.get(depot).getAllNodes().containsKey(l)) {
					for (TimedNode n : graph.get(depot).getAllNodes().get(l))
					{
						IloNumExpr lhs = cplex.constant(0);
						IloNumExpr rhs = cplex.constant(0);

						if (!n.isDepot())
						{
							for (TimedArc inArc : graph.get(depot).getInArcs(n))
							{
								lhs = cplex.sum(lhs, X.get(depot).get(inArc));
							}

							for (TimedArc outArc : graph.get(depot).getOutArcs(n))
							{
								rhs = cplex.sum(rhs, X.get(depot).get(outArc));
							}
						}

						cplex.addEq(lhs, rhs, "flow"+depot.getIndex()+"-"+n.toString());
					}
				}
			}
			if (oneDepot) break;
		}
	}

	public void addCoverConstraints() throws IloException
	{
		Map<Trip, List<IloNumVar>> tripVars = new HashMap<>();

		for (Trip t : in.getTrips())
		{
			tripVars.put(t, new ArrayList<>());
		}

		for (Location depot : in.getDepots())
		{
			for (TimedArc a : X.get(depot).keySet())
			{
				if (a.getType() == TimedArc.TRIP_ARC)
				{
					tripVars.get(a.getTrip()).add(X.get(depot).get(a));
				}
			}
			if (oneDepot) break;
		}

		for (Trip t : in.getTrips())
		{
			IloNumExpr lhs = cplex.constant(0);

			for (IloNumVar var : tripVars.get(t))
			{
				lhs = cplex.sum(lhs, var);
			}
			cplex.addEq(lhs, 1,"cover"+t.getID());
		}
	}

	public void addObjective() throws IloException
	{
		IloNumExpr obj = cplex.constant(0);
		for (Location depot : in.getDepots())
		{
			for (TimedArc a : X.get(depot).keySet())
				
			{
				obj = cplex.sum(obj, cplex.prod(a.getCost(), X.get(depot).get(a)));
			}
			if (oneDepot) break;
		}

		cplex.addMinimize(obj);
	}

	public boolean isFeasible() throws IloException
	{
		return cplex.isPrimalFeasible();
	}
	
	public void setGap(double d) throws IloException {
		cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, d);
	}
	
	public void setAbsGap(double d) throws IloException {
		cplex.setParam(IloCplex.Param.MIP.Tolerances.AbsMIPGap, d);
	}

	public void solve() throws IloException 
	{
		cplex.solve();
	}

	public void cleanup() throws IloException 
	{
		cplex.clearModel();
		cplex.end();
	}

	public double getObjectiveValue() throws IloException
	{
		return cplex.getObjValue();
	}
	
	public double getMIPgap() throws IloException {
		return cplex.getMIPRelativeGap();
	}

	public void printSolution() throws IloException
	{
		for (Location depot : in.getDepots())
		{
			int numVehicles = 0;

			for (TimedArc a : X.get(depot).keySet())
			{
				if (a.getFrom().isDepot())
				{
					numVehicles += cplex.getValue(X.get(depot).get(a));
				}
			}
			
			System.out.println("Depot " + depot.getIndex() + " delivers " + numVehicles + " vehicles.");
			if (oneDepot) break;
		}
	}

	public Map<Location, Map<TimedArc, Double>> printArcSolution() throws IloException
	{
		Map<Location, Map<TimedArc, Double>> sol = new HashMap<>();

		for (Location depot : in.getDepots())
		{
			sol.put(depot, new HashMap<>());

			System.out.println();
			System.out.println("Depot " + depot.getIndex() + ":");

			for (TimedArc a : X.get(depot).keySet())
			{				
				if (cplex.getValue(X.get(depot).get(a)) > 0.5)
				{
					System.out.println(a + " has value " + cplex.getValue(X.get(depot).get(a)));
					sol.get(depot).put(a, cplex.getValue(X.get(depot).get(a)));
				}
			}
			if (oneDepot) break;
		}

		return sol;
	}
	
	public void setStartSolution(Set<Duty> feasibleDuties) throws IloException {
		Map<Location,Map<TimedArc,Integer>> arcToValue = new HashMap<>();
		Set<Trip> unserved = new HashSet<>(in.getTrips());
		int nVars = 0;
		for(Location depot: this.in.getDepots()) {
			Map<TimedArc,Integer> inner = new HashMap<>();
			for(TimedArc a: graph.get(depot).getArcs()) {
				inner.put(a, 0);
				nVars++;
				
			}
			arcToValue.put(depot, inner);
		}
		for(Duty d: feasibleDuties) {
			Location depot = d.getDepot();
			Map<TimedArc,Integer> inner = arcToValue.get(depot);
			for(TimedArc a: graph.get(depot).convertPathNew(d.getTrips())) {
				inner.put(a, inner.get(a)+1);
				if(a.getType()==TimedArc.TRIP_ARC) {
					unserved.remove(a.getTrip());
				}
			}
		}
		
		//System.out.println("Number of variables in set solution: "+nVars);
		
		if(!unserved.isEmpty()) {
			throw new Error("There are still "+unserved.size()+ " unserved trips.");
		}
		
		IloNumVar[] vars = new IloNumVar[nVars];
		double[] vals = new double[nVars];
		
		int i = 0;
		for(Location depot: this.in.getDepots()) {
			//System.out.println("init solution for depot: "+depot.getIndex());
			Map<TimedArc,Integer> inner = arcToValue.get(depot);
			for(Entry<TimedArc,Integer> ent: inner.entrySet()) {
				TimedArc a = ent.getKey();
				vars[i] = X.get(depot).get(a);
				vals[i] = ent.getValue();
				if(vars[i]==null) {
					throw new Error("Don't have variable for: "+a);
				}
				//System.out.println(vars[i]+" and "+vals[i] + " "+a);
				i++;
			}
		}
		//System.out.println("Combined arcs give cost of "+cost);
		cplex.addMIPStart(vars,vals,IloCplex.MIPStartEffort.CheckFeas);
	}
	
	private void storeFlows() throws IloException {
		flows = new LinkedHashMap<>();
		for(Location depot: in.getDepots()) {
			flows.put(depot, getSupport(depot));
		}
	}
	
	private Map<TimedArc,Integer> getSupport(Location depot) throws IloException {
		Map<TimedArc, Integer> solution = new LinkedHashMap<>();
		for (TimedArc a : X.get(depot).keySet()) 
		{
			if (cplex.getValue(X.get(depot).get(a)) > 0.5)
			{
				double flow = cplex.getValue(X.get(depot).get(a));
				int rounded = (int) Math.round(flow);
				if(Math.abs(flow-rounded)>0.01) {
					System.out.println("flow: "+flow);
				}
				solution.put(a, rounded);
				//System.out.println("flow : "+ flow+ " Total flow: " + totalFlow + ". Depot " + depot.getIndex() + ":" + " rounded flow: "+roundedFlow);
			}
		}
		return solution;
	}
	
	/**
	 * Method that gets all connected components in the subgraph with positive flow
	 * @param depot
	 * @return
	 */
	private Set<Map<TimedArc,Integer>> getConnectedComponents(Location depot) {
		Set<Map<TimedArc,Integer>> conComps = new LinkedHashSet<>();
		Map<TimedArc,Integer> support = new LinkedHashMap<>(flows.get(depot)); //copying 
		while(!support.isEmpty()) {
			//there is another component
			Map<TimedArc,Integer> component = new LinkedHashMap<>();
			TimedArc start = support.keySet().iterator().next();
			Set<TimedNode> explore = new LinkedHashSet<>();
			if(!start.getFrom().getStation().isDepot()) {
				explore.add(start.getFrom());
			}
			if(!start.getTo().getStation().isDepot()) {
				explore.add(start.getTo());
			}
			component.put(start, support.get(start));
			support.remove(start);
			while(!explore.isEmpty()&&!support.isEmpty()) {
				Set<TimedArc> toAdd = new LinkedHashSet<>();
				for(TimedArc a: support.keySet()) {
					for(TimedNode n: explore) {
						if(a.getFrom().equals(n)||a.getTo().equals(n)) {
							toAdd.add(a);
						}
					}
				}
				explore = new LinkedHashSet<>();
				for(TimedArc a: toAdd) {
					if(!a.getFrom().getStation().isDepot()) {
						explore.add(a.getFrom());
					}
					if(!a.getTo().getStation().isDepot()) {
						explore.add(a.getTo());
					}
					component.put(a, support.get(a));
					support.remove(a);
				}
			}
			
			conComps.add(component);
		}
		return conComps;
	}
	
	/**
	 * Method that retrieves all paths starting from some node
	 */
	private Set<List<TimedArc>> getAllPathsFrom(Map<TimedArc,Integer> component, TimedNode current, TimedNode endNode, List<TimedNode> curPath) {
		Set<List<TimedArc>> allPaths = new LinkedHashSet<>();
		
		if(current.equals(endNode)) {
			allPaths.add(new ArrayList<>());
		} else {
			for(TimedArc a: component.keySet()) {
				if(a.getFrom().equals(current)) {
					//start new path
					TimedNode next = a.getTo();
					List<TimedNode> extPath = new ArrayList<>(curPath);
					if(curPath.contains(next)) {
						return null;
					} else {
						extPath.add(next);
					}
					Set<List<TimedArc>> allPathsFrom = getAllPathsFrom(component,next,endNode,extPath);
					if(allPathsFrom==null) {
						//cycle
						return null;
					}
					for(List<TimedArc> pathFromNext: allPathsFrom) {
						pathFromNext.add(0,a);
						allPaths.add(pathFromNext);
					}
				}
			}
		}
		
		
		return allPaths;
	} 
	
	/**
	 * Method that returns the super-duty composition
	 * We assume this method is only called if the solution does not contain any cycles
	 * @return
	 */
	public Map<Location,List<SuperDuty>> retrieveSuperDutyDecomposition() {
		Map<Location, List<SuperDuty>> superDuties = new LinkedHashMap<>();
		for (Location depot : in.getDepots())
		{
			superDuties.put(depot, new ArrayList<>());
			Set<Map<TimedArc,Integer>> components = getConnectedComponents(depot);
			System.out.println("components: "+components.size());
			for(Map<TimedArc,Integer> compo : components) {
				
				List<TimedNode> curPath = new ArrayList<>();
				curPath.add(graph.get(depot).getStartDepot());
				Set<List<TimedArc>> allPaths =  getAllPathsFrom(compo,  graph.get(depot).getStartDepot(), graph.get(depot).getEndDepot(),curPath);
				if(allPaths==null) {
					return null;
				}
				superDuties.get(depot).add(new SuperDuty(allPaths, in,compo));
			}
		} 		
		return superDuties;
	}
	

	public Map<Location, List<Duty>> retrievePathDecomposition() throws IloException
	{
		storeFlows();
		boolean print = (printDetail>2);
		Map<Location, List<Duty>> schedules = new HashMap<>();
		for (Location depot : in.getDepots())
		{
			Map<TimedArc, Integer> solution = new HashMap<>(flows.get(depot));
			int numVehicles = 0;
			int totalFlow = 0;

			for (TimedArc a : solution.keySet())
			{
				if (a.getFrom().isDepot())
				{
					numVehicles += solution.get(a);
				}
				totalFlow += solution.get(a);
			}

			if (print) System.out.println();

			schedules.put(depot, new ArrayList<>());
			for (int i = 0; i < numVehicles; i++)
			{
				List<TimedArc> schedule = new ArrayList<>();
				if (print) System.out.println();
				if (print) System.out.println("Vehicle #" + (i+1) + ":");

				TimedNode current = graph.get(depot).getStartDepot();

				while (true)
				{
					for (TimedArc a : solution.keySet())
					{
						if (a.getFrom().equals(current))
						{
							if (print) System.out.println(a);
							schedule.add(a);
							totalFlow--;
							solution.put(a, solution.get(a) - 1);

							if (solution.get(a) == 0)
							{
								solution.remove(a);
							}

							current = a.getTo();
							break;
						}
					}

					if (current.isDepot())
					{
						Duty d = new Duty(schedule,in,false);
						if(d.getTrips().size()>0) {
							schedules.get(depot).add(new Duty(schedule, in,false));
						}
						break;
					}
				}
			}

			if (totalFlow > 0)
			{
				//print = true;
				hasCycle = true;
				if (print) System.out.println("");
				if (print) System.out.println("Cycles left!");
				if (print) System.out.println("");
				System.out.println("Solution has a cycle");
				List<TimedArc> cycles = new ArrayList<>();

				for (TimedArc a : solution.keySet())
				{
					if (print) System.out.println(a);
					cycles.add(a);
				}

				schedules.get(depot).add(new Duty(cycles, in,true));
			}
			if (oneDepot) break;
		}
		
		return schedules;
	}

	public int getLB() throws IloException {
		if(cplex.getCplexStatus()==IloCplex.CplexStatus.Optimal) {
			return (int) Math.round(getObjectiveValue());
		}
		return (int) Math.ceil(cplex.getBestObjValue()-0.0001);
	}

	public boolean solutionHasCycle() {
		return hasCycle;
	}

	public boolean foundSolution() throws IloException {
		return cplex.getStatus().equals(IloCplex.Status.Feasible)||cplex.getStatus().equals(IloCplex.Status.Optimal);
	}

	


}
