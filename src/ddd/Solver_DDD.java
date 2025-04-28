package ddd;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ilog.concert.IloException;
import problem.Instance;
import problem.Location;
import problem.Solution;
import problem.Trip;

/**
 * Class that implements the DDD algorithm *
 */
public class Solver_DDD 
{
	private final static double DDD_ABS_TOL = 0.999;
	private final static boolean POSTPROCESSING = true;
	private final static int maxTripsUB = 100; //nr of trips for when the UB heuristic is called
	
	private boolean dyn_gap; //if false, always solve LB-IP to optimality. If true, the optimality tolerance is set dynamically
	private int deadhead_type; //1 is short, 2 is medium, 3 is long	
	private int refinementStrat; //1 is simple, 2 is FewerTimePoints, 3 is FewerIterations
	private boolean optimize_postprocessing; //optimize the path decomposition in the postprocessing step to minimize deviations
	private final int TIME_LIMIT;
	private final boolean aggregate;
	private final int iterLimit;
	
	private boolean initMore;
	
	private static int printDetail = 1; 

	private Instance instance;
	private Map<Location,TimeSpaceGraph> tsNetwork;
	
	private Solution sol; //the final solution
	private Solution sol_postMinimized; //final solution with deviation minimized
	private Solution sol_postMaximized; //final solution with deviation maximized


	private Solution bestSolution; //the best solution so far
	private int objective;
	private boolean solved;
	private int lb;
	private int ub;
	private double cpu; 
	private double cpuNetworkCreation;
	private double cpuIPs;
	private double cpuRefining;
	private double cpuUB;
	
	private int iterations; 
	private int nodes;
	private int arcs;
	
	private Set<Duty> infeasibleDuties; //set with infeasible duties;
	private Set<Duty> feasibleDuties; //set with feasible duties;
	
	private List<Integer> lbs;
	private List<Integer> ubs;
	private List<Integer> vehiclesLB;
	private List<Integer> vehiclesUB;
	private List<Integer> numVars;
	private List<Integer> nodesPerIteration;
	private List<Integer> arcsPerIteration;
	private List<Double> cpus;
	
	public Solver_DDD(Instance in, boolean dyn_gap,int deadhead_type,int refiningStrat,boolean optimize_postprocessing, int timeLimit, boolean aggregate, int iterLimit) {
		this.instance = in;
		this.dyn_gap = dyn_gap;
		this.deadhead_type = deadhead_type;
		this.refinementStrat = refiningStrat;
		this.optimize_postprocessing = optimize_postprocessing;
		this.TIME_LIMIT = timeLimit;
		this.aggregate = aggregate;
		this.iterLimit = iterLimit;
		
		lbs = new ArrayList<>();
		numVars = new ArrayList<>();
		ubs = new ArrayList<>();
		vehiclesLB = new ArrayList<>();
		vehiclesUB = new ArrayList<>();
		nodesPerIteration = new ArrayList<>();
		arcsPerIteration = new ArrayList<>();
		cpus = new ArrayList<>();
		cpu = 0;
		cpuNetworkCreation = 0;
		cpuIPs = 0;
		cpuRefining = 0;
		cpuUB = 0;
	}
	
	/**
	 * Main method of DDD
	 */
	public void solve() throws IloException {
		
		//initialization
		double startTimeDDD = System.currentTimeMillis();
		lb = 0;
		ub = Integer.MAX_VALUE;
		solved = false;
		int numIterations = 0;
		initNetwork();

		//main loop
		while (!solved && cpu < TIME_LIMIT && numIterations<iterLimit) 
		{
			numIterations++;
			printProgress1(numIterations);
			
			//construct network
			double clock = System.nanoTime();
			copyNetworks(numIterations);
			cpuNetworkCreation+= 1e-9*(System.nanoTime()-clock);
			determineNrNodes();
			determineNrArcs();
			printProgress2();
			
			if(iterLimit==1) {
				break;
			}
			
			// Solve problem on partial network
			clock = System.nanoTime();
			MDVSP modelDDD = new MDVSP(instance, tsNetwork, false, false);
			setStartSolutionAndGapAndTimeLimit(modelDDD);
			modelDDD.solve();
			cpuIPs += 1e-9*(System.nanoTime()-clock);
			System.out.println("Cpu IPs: "+cpuIPs);
			
			// Save results mathematical model and store the solution
			int objDDD = (int) modelDDD.getObjectiveValue();
			lb = Math.max(lb, modelDDD.getLB());
			printProgress3(objDDD,modelDDD.getLB());
			lbs.add(modelDDD.getLB());
			numVars.add(modelDDD.getNumVariables());
			
			//retrieve duties or superduties (connected components of duties)
			clock = System.nanoTime();
			Map<Location, List<Duty>> schedules = modelDDD.retrievePathDecomposition();
			Map<Location, List<SuperDuty>> supDutyMap = null;
			modelDDD.cleanup();
			determineNrVehicles(schedules);

			//check optimality and add timepoints if not
			boolean continuousTimeFeasible;
			if(refinementStrat!=1&&!modelDDD.solutionHasCycle()) {
				supDutyMap = modelDDD.retrieveSuperDutyDecomposition();
			}
			if(supDutyMap!=null) {
				continuousTimeFeasible = checkFeasibilitySuper(supDutyMap);
			} else {
				continuousTimeFeasible = checkFeasibility(schedules);
			}
			System.out.println("continu feas: "+continuousTimeFeasible);
			
			cpuRefining += 1e-9*(System.nanoTime()-clock);
			
			if (!continuousTimeFeasible)
			{
				clock = System.nanoTime();
				determineUpperBound();
				cpuUB += 1e-9*(System.nanoTime()-clock);
			}
			else 
			{
				Solution newSolution = new Solution(instance,feasibleDuties); 
				int trueObj = newSolution.getCosts(); //recompute the true objective given the schedule, necessary if not solved to optimality
				if(printDetail>2) {
					System.out.println("True costs: "+newSolution.getCosts());
				}
				ubs.add(trueObj);
				if(trueObj<ub) {
					//found a better solution
					ub = trueObj;
					bestSolution = newSolution;
					sol = bestSolution;
				}
				printProgress4();
				double dddAbsGap = (ub-lb);
				if(dddAbsGap<DDD_ABS_TOL) {
					solved = true;
					sol = bestSolution;
					this.objective = trueObj;
					System.out.println("Solved! Objective = "+this.objective + " and lb = "+lb);
					if(Solver_DDD.POSTPROCESSING) {
						if(this.optimize_postprocessing) {
							supDutyMap = modelDDD.retrieveSuperDutyDecomposition();
							postProcessing(supDutyMap);
						}
						System.out.println("Total costs: "+bestSolution.getCosts()+" Total deviation = "+bestSolution.getTotalDeviation());
					}
				} 
			}
			cpu = 10e-4*(System.currentTimeMillis() - startTimeDDD);
			cpus.add(cpu);
		}
		
		iterations = numIterations;
		
		if(!solved) {
			//time run out
			sol = bestSolution;
		}
		
		printResults(); 
	}
	
	private void determineNrVehicles(Map<Location, List<Duty>> schedules) {
		int veh = 0;
		for(Location l: schedules.keySet()) {
			veh += schedules.get(l).size();
		}
		vehiclesLB.add(veh);
	}

	private void setStartSolutionAndGapAndTimeLimit(MDVSP modelDDD) throws IloException {
		if(bestSolution!=null) {
			modelDDD.setStartSolution(bestSolution.getDuties());
		}
		if(dyn_gap) {
			if(bestSolution==null) { 
				modelDDD.setGap(0.01);
			} else {
				double absGapTol = Math.max(0.99, (ub-lb)/10.0);
				modelDDD.setAbsGap(absGapTol);
			}
		}
		modelDDD.setTimeLimit(TIME_LIMIT-cpu); //added 24-02
	}
	
	private void printProgress4() {
		if(printDetail>0) {
			System.out.println("Found new upper bound: "+ub);
		}
	}
	
	private void printProgress3(int objDDD, int lbI) {
		if(printDetail>0) {
			System.out.println("Solved LB-IP with objective "+objDDD + " lb in iteration is "+lbI + " abs gap is "+(ub-lb));
		}
	}

	private void printProgress2() {
		if(printDetail>0) {
			System.out.println("TI network has "+nodes+ " nodes and "+arcs+" arcs");
		}
	}

	private void printProgress1(int numIterations) {
		if(printDetail>0) {
			System.out.println("***********************************************************");
			System.out.println("Dataset " + instance.getInd() + ". Iteration " + numIterations);
			System.out.println("***********************************************************");
		}
	}

	private void printResults() {
		// Print stuff
		if(printDetail>0) { 
			System.out.println("Finished after " + iterations + " iterations!");
			System.out.println("DDD solved in " + cpu + "s.");
			System.out.print("[");
			
			for (int a = 0; a < lbs.size(); a++)
			{
				System.out.print(lbs.get(a));

				if (a == lbs.size() - 1)
				{
					System.out.print("]");
				}
				else
				{
					System.out.print(", ");
				}
			}
			
			System.out.println();
		}
		
	}

	/**
	 * Method that post-processes the solution, with the objective to minimize total deviation.
	 */
	private void postProcessing(Map<Location, List<SuperDuty>> supDutyMap) throws IloException {
		for(Location depot: instance.getDepots()) {
			for(SuperDuty sD: supDutyMap.get(depot)) {
				sD.feasibilityCheck(this.instance.getMaxDeviation(), false, false);
			}
		}
		
		Set<Duty> duties = new HashSet<>();
		for(Location depot: instance.getDepots()) {
			for(SuperDuty sD: supDutyMap.get(depot)) {
				duties.addAll(sD.optimalPostProcessing(true));
			}
		}
		sol_postMinimized = new Solution(instance,duties);
		
		Set<Duty> duties2 = new HashSet<>();
		for(Location depot: instance.getDepots()) {
			for(SuperDuty sD: supDutyMap.get(depot)) {
				duties2.addAll(sD.getSelectedFeasibleDuties());
			}
		}
		sol = new Solution(instance,duties2);
		
		
		Set<Duty> duties3 = new HashSet<>();
		for(Location depot: instance.getDepots()) {
			for(SuperDuty sD: supDutyMap.get(depot)) {
				duties3.addAll(sD.optimalPostProcessing(false));
			}
		}
		sol_postMaximized = new Solution(instance,duties3);
	}

	private void copyNetworks(int numIterations) {
		Location d0 = instance.getDepots().get(0);
		TimeSpaceGraph n0 = tsNetwork.get(d0);
		if(numIterations>1) {
			n0.constructArcs();
		}
		for(Location d: instance.getDepots()) {
			if(d!=d0) {
				tsNetwork.put(d, new TimeSpaceGraph(n0,d));
			}
		}
		
	}

	private void determineNrArcs() {
		arcs = 0;
		for(Location d: instance.getDepots()) { 
			arcs+= tsNetwork.get(d).getNumArcs();
		}
		arcsPerIteration.add(arcs);
	}

	private void determineNrNodes() {
		int before = nodes;
		nodes = 0;
		for(Location d: instance.getDepots()) { 
			nodes+= tsNetwork.get(d).getNumNodes();
		}
		if(before==nodes&&!dyn_gap) {
			throw new Error("Nodes not increased");
		}
		nodesPerIteration.add(nodes);
	}

	private void determineUpperBound() throws IloException {
		Solution feasibilized = makeFeasible();
		if(feasibilized==null) {
			ubs.add(Integer.MAX_VALUE);
			vehiclesUB.add(Integer.MAX_VALUE);
			return;
		}
		if(!feasibilized.isFeasible()) {
			throw new Error("The solution is not feasible");
		}
		feasibilized.computeCosts();
		if(printDetail>0) {
			System.out.println("Found feasible solution with costs "+feasibilized.getCosts());
		}
		ubs.add(feasibilized.getCosts());
		vehiclesUB.add(feasibilized.getDuties().size());
		if(feasibilized.getCosts()<ub) {
			ub = feasibilized.getCosts();
			bestSolution = feasibilized;
			if(printDetail>0) {
				System.out.println("Found new upper bound: "+ub);
			}
			if(ub<lb) {
				throw new Error("Cannot be");
			}
		}
		
	}

	private Solution makeFeasible() throws IloException {
		List<Trip> unserved = new ArrayList<>();
		for(Duty d: infeasibleDuties) {
			unserved.addAll(d.getTrips());
		}
		List<Trip> served = new ArrayList<>();
		for(Duty d: feasibleDuties) {
			served.addAll(d.getTrips());
		}
		if(printDetail>0) {
			System.out.println("After removal of the infeasible duties, the number of unserved trips is "+unserved.size());
			System.out.println("The number of served trips is : "+served.size());
		}
		
		if(unserved.size()<Solver_DDD.maxTripsUB) {
			Instance subInst = new Instance(instance,unserved);
			SolverMIP fullSolver = new SolverMIP(subInst,aggregate,TIME_LIMIT);
			fullSolver.solve();
			feasibleDuties.addAll(fullSolver.getSolution().getDuties());;
			return new Solution(instance,feasibleDuties);
		} else {
			return null;
		}

	}
	
	/**
	 * Method that checks feasibility and adds time points
	 * @param schedules
	 * @return
	 * @throws IloException
	 */
	private boolean checkFeasibility(Map<Location, List<Duty>> schedules) throws IloException {
		// Step 3 - Convert solution
		if(printDetail>1) {
			System.out.println("Step 3");
		}
		boolean optimal = true;
		feasibleDuties = new HashSet<>();
		infeasibleDuties = new LinkedHashSet<>();
		Set<Trip> unserved = new HashSet<>(instance.getTrips());
		for (Location depot : schedules.keySet())
		{
			for (Duty route: schedules.get(depot))
			{
				// Feasibility check
				List<TimedNode> newTimePoints = route.feasibilityCheck(instance.getMaxDeviation(),false);
				unserved.removeAll(route.getTrips());
				if (!newTimePoints.isEmpty())
				{
					optimal = false;
					addTimePoints(newTimePoints);
					if(printDetail>1) {
						route.feasibilityCheck(instance.getMaxDeviation(),true);
					}
					infeasibleDuties.add(route);
				} else { 
					feasibleDuties.add(route);
				}
			}
		}
		if(unserved.size()>0) {
			for(Trip un: unserved) {
				System.out.println(un);
			}
			throw new Error("SOmethings really wrong. Serving not: "+unserved.size() + " trips.");
		}
		return optimal;
	}
	
	private boolean checkFeasibilitySuper(Map<Location, List<SuperDuty>> schedules) throws IloException {
		// Step 3 - Convert solution
		if(printDetail>1) {
			System.out.println("Step 3");
		}

		boolean optimal = true;
		feasibleDuties = new HashSet<>();
		infeasibleDuties = new LinkedHashSet<>();
		for (Location depot : schedules.keySet())
		{
			//System.out.println("nr of superduties: "+schedules.get(depot).size());
			for (SuperDuty route: schedules.get(depot))
			{
				//System.out.println("Checking superduty with : "+route.getNrPaths() + " paths");
				// Feasibility check
				Set<List<TimedNode>> newTimePointsSet = route.feasibilityCheck(instance.getMaxDeviation(),(this.refinementStrat==3),false);
				feasibleDuties.addAll(route.getSelectedFeasibleDuties());
				if (!newTimePointsSet.isEmpty())
				{
					optimal = false;
					for(List<TimedNode> newTimePoints: newTimePointsSet) {
						addTimePoints(newTimePoints);
					}
					infeasibleDuties.addAll(route.getSelectedInfeasibleDuties());
				} 
			}
		}
		
		return optimal;
	}

	private void initNetwork() {
		// Step 1 - Create partial network
		long startTimeStep1 = System.currentTimeMillis();
		tsNetwork = new HashMap<>();
		tsNetwork.put(instance.getDepots().get(0), new TimeSpaceGraph(instance,false,instance.getDepots().get(0),deadhead_type,aggregate,initMore));

		/*for(Location d: instance.getDepots()) {
			tsNetwork.put(d, new TimeSpaceGraph(instance,false,d,deadhead_type));
			//System.out.println("Should copy construct!");
		}*/
		if(printDetail>1) {
			System.out.println("Step 1");
			System.out.println("Network creation time: " + (System.currentTimeMillis() - startTimeStep1) + "ms");
		}

	}


	public void addTimePoints(List<TimedNode> newTimePoints)
	{	
		if(printDetail>1) {
			System.out.println("\n Infeasible subroute: ");
		}
		for (TimedNode n : newTimePoints) 
		{
			if(printDetail>1) {
				System.out.println("Add "+n);
			}
			for(Location d: instance.getDepots()) {
				tsNetwork.get(d).addNode(n.getStation(), n.getTime());
			}
		}
	}
	
	public int getFirstLB() {
		return lbs.get(0);
	}

	public int getObjective() {
		return objective;
	}

	public Solution getSolution() {
		return sol;
	}

	public int getCPU() {
		return (int) Math.round(cpu);
	}

	public int getIterations() {
		return iterations;
	}

	public int getNodes() {
		return nodes;
	}
	
	public int getArcs() {
		return arcs;
	}

	public int getTotalDev() throws IloException {
		return sol.getTotalDeviation();
	}
	
	public int getTotalDevMinimized() throws IloException {
		if(this.sol_postMinimized!=null) {
			return sol_postMinimized.getTotalDeviation();
		} else {
			return Integer.MAX_VALUE;
		}
	}
	
	public int getTotalDevMaximized() throws IloException {
		if(this.sol_postMaximized!=null) {
			return sol_postMaximized.getTotalDeviation();
		} else {
			return Integer.MAX_VALUE;
		}
	}

	public int getLB() {
		return lb;
	}
	public int getUB() {
		return ub;
	}

	public boolean solved() {
		return solved;
	}

	public boolean foundSolution() {
		return (sol!=null);
	}

	public List<Integer> getLbs() {
		return lbs;
	}

	public List<Integer> getUbs() {
		return ubs;
	}
	
	public List<Integer> getVehiclesLB() {
		return vehiclesLB;
	}
	
	public List<Integer> getVehiclesUB() {
		return vehiclesUB;
	}
	
	public int getNodesFirst() {
		return nodesPerIteration.get(0);
	}
	
	public int getArcsFirst() {
		return arcsPerIteration.get(0);
	}

	public double getCpuNetworkCreation() {
		return cpuNetworkCreation;
	}

	public double getCpuIPs() {
		return cpuIPs;
	}

	public double getCpuRefining() {
		return cpuRefining;
	}

	public double getCpuUB() {
		return cpuUB;
	}

	public void printDDDstats(PrintWriter pw) {
		System.out.println("Printing DDD stats");
		pw.println("iteration,nodes,arcs,lb,ub,cpu");
		for(int i = 0; i<this.iterations; i++) {
			pw.println((i+1)+","+nodesPerIteration.get(i)+","+arcsPerIteration.get(i)+","+lbs.get(i)+","+
					ubs.get(i)+","+cpus.get(i));
		}
		pw.flush();
		pw.close();
	}

	public int getVehicles() {
		// TODO Auto-generated method stub
		return sol.getDuties().size();
	}

	public int getDrivingTime() throws IloException {
		// TODO Auto-generated method stub
		return sol.getDrivingTime();
	}

	public int getVehiclesFirst() {
		// TODO Auto-generated method stub
		return vehiclesLB.get(0);
	}

	public boolean isInitMore() {
		return initMore;
	}

	public void setInitMore(boolean initMore) {
		this.initMore = initMore;
	}
	
	
	
}
