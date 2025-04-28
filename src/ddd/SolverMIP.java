package ddd;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ilog.concert.IloException;
import problem.Instance;
import problem.Location;
import problem.Solution;

public class SolverMIP {
	
	private final int timeLimit;
	private Instance inst;
	private boolean aggregate;
	private Solution solution;
	private int nodes; 
	private int arcs;
	private int lb;
	private double cpu;
	private int objVal;
	
	private int maxVehicles;
	
	public SolverMIP(Instance inst, boolean aggregate, int timeLimit) {
		this.inst = inst;
		this.aggregate = aggregate;
		this.timeLimit = timeLimit;
	}
	
	public void solve() throws IloException {
		double startTimeDDD = System.currentTimeMillis();
		Map<Location,TimeSpaceGraph> fullNetwork = new HashMap<>();
		
		Location d0 = inst.getDepots().get(0);
		TimeSpaceGraph n0 =  new TimeSpaceGraph(inst,true,d0,2,aggregate,false);
		fullNetwork.put(d0, n0);
		for(Location d: inst.getDepots()) {
			if(d!=d0) {
				fullNetwork.put(d, new TimeSpaceGraph(n0,d));
			}
		}
		
		arcs = 0;
		nodes = 0;
		for(Location d: inst.getDepots()) { 
			arcs+= fullNetwork.get(d).getNumArcs();
			nodes+= fullNetwork.get(d).getNumNodes();
		}
		
		
		/*for(Location depot: inst.getDepots()) {
			fullNetwork.put(depot, new TimeSpaceGraph(inst,true,depot));
		}*/
		MDVSP fullModel = new MDVSP(inst,fullNetwork, false,false); 
		
		if(this.maxVehicles!=0) {
			fullModel.addDepotOutflowConstraints(maxVehicles);
		}
	
		fullModel.setAbsGap(0.99);
		cpu = 10e-4*(System.currentTimeMillis() - startTimeDDD);
		fullModel.setTimeLimit(Math.max(timeLimit-cpu,0));
		fullModel.solve();
		lb = fullModel.getLB();
		objVal = (int) Math.round(fullModel.getObjectiveValue());
		System.out.println("Full model has obj: "+fullModel.getObjectiveValue());

		if(fullModel.foundSolution()) {
			Map<Location, List<Duty>> schedules = fullModel.retrievePathDecomposition();
			solution = new Solution(inst,schedules);
		} 
	
		fullModel.cleanup();
		cpu = 10e-4*(System.currentTimeMillis() - startTimeDDD);
	}
	
	
	public int getNodes() {
		return nodes;
	}

	public int getArcs() {
		return arcs;
	}

	public int getLB() {
		return lb;
	}
	
	public int getUB() throws IloException {
		if(solution!=null) {
			solution.computeCosts();
			return solution.getCosts();
		}
		return Integer.MAX_VALUE;
	}

	public double getCpu() {
		return cpu;
	}

	public Solution getSolution() {
		return solution;
	}

	public int getMaxVehicles() {
		return maxVehicles;
	}

	public void setMaxVehicles(int maxVehicles) {
		this.maxVehicles = maxVehicles;
	}

	public int getObjective() {
		// TODO Auto-generated method stub
		return objVal;
	}
	
}
