package bnp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ddd.Duty;
import ilog.concert.IloException;
import problem.Instance;
import problem.Location;
import problem.Trip;
import util.Pair;
import util.Triple;

/**
 * Class that implements the branch-and-price algorithm *
 */
public class BranchAndPrice {
	private final static int routesPerIteration = 4000; //used to be 50
	private final static int colManPeriod = 50;//20;
	private final static double maxRedCost = 10;
	private final double timeLimit;
	private final Instance inst;
	private final MasterProblem master;
	private final Map<Location,ConnectionNetwork> pricingGraphs;
	private Map<Location,ShortestPathWithResources> pricingProblems;
	
	private Set<Route> addedRoutes;
	private double rootBound;
	private double globalUB;
	private double globalLB;
	private double timeTotal;
	private double timeRoot;
	private double timePricing;
	private double timeMaster;
	private int nrNodes;
	
	public BranchAndPrice(Instance inst,double timeLimit) throws IloException {
		super();
		this.inst = inst;
		this.timeLimit = timeLimit;
		addedRoutes = new HashSet<>();
		this.master = new MasterProblem(inst);
		Map<Location,ConnectionNetwork> thePricingGraphs = new LinkedHashMap<>();
		pricingProblems = new LinkedHashMap<>();
		for(Location depot: inst.getDepots()) {
			ConnectionNetwork cN = new ConnectionNetwork(inst,depot);
			thePricingGraphs.put(depot, cN);
			pricingProblems.put(depot, new ShortestPathWithResources(cN,inst.getMaxDeviation()));
		}
		this.pricingGraphs = thePricingGraphs;
	}
	
	public void addInitialColumns() throws IloException {
		Location depot = inst.getDepots().get(0);
		ConnectionNetwork cN = pricingGraphs.get(depot);
		for(Trip t: inst.getTrips()) {
			List<Trip> tList = new ArrayList<>();
			tList.add(t);
			List<CN_Arc> arcList = new ArrayList<>();
			arcList.add(cN.getPullOut(t));
			arcList.add(cN.getPullIn(t));
			Route r = new Route(depot,tList,arcList);
			master.addColumn(r);
			addedRoutes.add(r);
		}
	}
	
	/**
	 * Method that runs the branch-and-price algorithm
	 * @throws IloException
	 */
	public void branchAndPrice() throws IloException {
		System.out.println("Start Branch-and-Price");
		double startTime = System.currentTimeMillis();
		double runTime = 0;
		addInitialColumns();
		globalLB = 0;
		globalUB = Double.MAX_VALUE;
		List<BNP_Node> nodeList = new ArrayList<>();
		BNP_Node root = new BNP_Node(this.pricingGraphs);
		nodeList.add(root);
		nrNodes = 0;
		
		while(!nodeList.isEmpty() && runTime<this.timeLimit) {
			//we always select node with lowest bound
			BNP_Node toSolve = nodeList.get(0);	
			globalLB = toSolve.getLB();
			
			System.out.println("\n Global LB = "+globalLB+" globalUB="+globalUB+" nr unexplored nodes: "+nodeList.size() +" depth curnode: "+toSolve.getDepth());
			System.out.println("Solving: "+toSolve);
			if(globalUB-globalLB<0.99) {
				break;
			}
			solve(toSolve,this.timeLimit-runTime);
			if(toSolve.getOptValue()==Double.MAX_VALUE) {
				// it was a time out
				break;
			}
			nrNodes++;
			if(nrNodes==1) {
				timeRoot = 1e-3*(System.currentTimeMillis()-startTime);
				rootBound = toSolve.getOptValue();
			}
			System.out.println("Finished solving node");
			nodeList.remove(0);
			
			//try intertask branching second
			Triple<Location,Location,Trip> color = master.getColorBranching();
			//try intertask branching second
			Triple<Location,Location,CN_Arc> interTask = master.getInterTaskBranching();
			//get the most fractional variable
			Pair<Location,CN_Arc> fracPair = master.getMostFractionalArc();
			
			//check if the solution is integer
			if(fracPair == null) {
				//solution integer, found new upper bound
				double newUB = toSolve.getOptValue();
				if(newUB<globalUB) {
					globalUB = newUB;
					System.out.println("Found new global best upper bound: "+newUB);
				}
				if(nodeList.isEmpty()) {
					System.out.println("Finished exploring tree");
					globalLB = globalUB;
				}
			} else {
				//should branch
				if(color!=null) {
					System.out.println("Branch on color ("+color.getA().getIndex()+","+color.getB().getIndex()+","+color.getC());
					nodeList.add(toSolve.getDownBranchColor(color));
					nodeList.add(toSolve.getUpBranchColor(color));
				} else if(interTask!=null) {
					System.out.println("Branch on intertask ("+interTask.getA().getIndex()+","+interTask.getB().getIndex()+","+interTask.getC());
					nodeList.add(toSolve.getDownBranch(interTask));
					nodeList.add(toSolve.getUpBranch(interTask));
				} else {
					System.out.println("Branch on depot "+fracPair.getA().getIndex()+" and arc "+fracPair.getB());
					nodeList.add(toSolve.getDownBranch(fracPair));
					nodeList.add(toSolve.getUpBranch(fracPair));
				}
			}
			Collections.sort(nodeList, (o1, o2) -> Double.compare(o1.getLB()-1e-3*o1.getDepth(),o2.getLB()-1e-3*o2.getDepth()));
			
			runTime = 1e-3*(System.currentTimeMillis()-startTime);
		}
		//System.out.println("Final LB: "+globalLB + " final ")
		timeTotal = 1e-3*(System.currentTimeMillis()-startTime);
	}

	/**
	 * Method that solves a node in the branching tree using column generations
	 */
	public double solve(BNP_Node toSolve, double localTimeLimit) throws IloException {
		boolean optimal = false;
		double masterCost = Double.MAX_VALUE;
		int iteration = 1;
		double startTime = System.currentTimeMillis();
		double runTime = 0;
		//branch in the master and pricing problem
		master.setBounds(toSolve);
		for(Location depot: inst.getDepots()) {
			pricingProblems.get(depot).setForbiddenArcs(toSolve.getAtLowerBound().get(depot));
		}
		while(!optimal&&runTime<localTimeLimit) {
			optimal = true;
			double startTimeMaster = System.currentTimeMillis();
			master.solve();
			timeMaster += 1e-3*(System.currentTimeMillis()-startTimeMaster);
			
			//update the pricing problem
			if(master.isFeasible()) {
				masterCost = master.getObj();
				//System.out.println("Solved master with objective "+master.getObj());
				Map<Trip,Double> duals = master.getDuals();
				for(ConnectionNetwork cN: pricingGraphs.values()) {
					cN.updateCosts(duals, true);
				}
				
				//check column management
				if(iteration%colManPeriod==0) {
					Set<Route> toRemove = new HashSet<>();
					for(Route r: addedRoutes) {
						if(getRedCost(r,duals)>maxRedCost) {
							toRemove.add(r);
							master.removeColumn(r);
						}
					}
					addedRoutes.removeAll(toRemove);
				}
				
			} else {
				System.out.println("Master infeasible");
				masterCost = Double.MAX_VALUE;
				Map<Trip,Double> duals = master.getFarkasDuals();
				System.out.println("Retrieved duals");
				for(ConnectionNetwork cN: pricingGraphs.values()) {
					cN.updateCosts(duals, false);
				}
			}
			
			
			//solve the pricing problem
			for(Location depot: inst.getDepots()) {
				ShortestPathWithResources dK = pricingProblems.get(depot); 
				
				double startTimePricing = System.currentTimeMillis();
				dK.computeDistances();
				timePricing += 1e-3*(System.currentTimeMillis()-startTimePricing);
				double redCost = dK.getDistance();
				//System.out.println("Found negative reduced cost: "+redCost);
				if(redCost<-1e-4) {
					//found negative reduced column
					optimal = false;
					
					for(Route r: dK.getBestRoutes(routesPerIteration)) {
						boolean newRoute = addedRoutes.add(r);
						if(!newRoute) {
							throw new Error("already in route set");
						} 
						master.addColumn(r);
					}
				} 
			}
			iteration++;
			runTime = 1e-3*(System.currentTimeMillis()-startTime);
		}
		if(!optimal) {
			//timeout
			masterCost = Double.MAX_VALUE;
		}
		toSolve.setOptValue(masterCost);
		return masterCost;
	}
	
	

	public static int getRoutesperiteration() {
		return routesPerIteration;
	}

	public Instance getInst() {
		return inst;
	}

	public MasterProblem getMaster() {
		return master;
	}

	public Map<Location, ConnectionNetwork> getPricingGraphs() {
		return pricingGraphs;
	}

	public Map<Location, ShortestPathWithResources> getPricingProblems() {
		return pricingProblems;
	}

	public Set<Route> getAddedRoutes() {
		return addedRoutes;
	}

	public double getTimePricing() {
		return timePricing;
	}

	public double getTimeMaster() {
		return timeMaster;
	}
	
	public void addColumn(Duty d) throws IloException {
		master.addColumn(new Route(d));
	}

	/**
	 * Method that computes the reduced cost of a rout given a set of duals
	 */
	public double getRedCost(Route r, Map<Trip, Double> duals) throws IloException {
		double redCost = r.getCost();
		for(Trip t: r.getTrips()) {
			redCost-= duals.get(t);
		}
		return redCost;
	}

	public double getUB() {
		return globalUB;
	}
	public double getRootBound() {
		return rootBound;
	}

	public double getTimeTotal() {
		return timeTotal;
	}

	public double getTimeRoot() {
		return timeRoot;
	}

	public double getLB() {
		// TODO Auto-generated method stub
		return globalLB;
	}

	public int getNodes() {
		// TODO Auto-generated method stub
		return nrNodes;
	}
	
	
}
