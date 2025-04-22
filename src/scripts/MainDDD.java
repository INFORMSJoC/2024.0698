package scripts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import ddd.MDVSP;
import ddd.Solver_DDD;
import ilog.concert.IloException;
import problem.Instance;
import problem.Solution;

public class MainDDD {

	public static void main(String[] args) throws IloException, FileNotFoundException {
		
		//settings and instance
		int nrTrips = 250;
		int maxDev = 1;
		int deadhead_type = 3; 
		int refiningStrat = 3; 
		boolean optimize_postprocessing = true;
		int timeLimit = 600;		
		
		boolean dyn_gap = true;
		boolean aggregate = true;
		int iterLimit = Integer.MAX_VALUE;

		String suffix = "DDD-"+nrTrips+"-"+maxDev+"-"+dyn_gap+"-"+deadhead_type+"-"+refiningStrat+"-"+optimize_postprocessing;
		PrintWriter pw = new PrintWriter("results/"+suffix+".txt");
		pw.println("instance,status,lb,ub,cpu,cpuNetwork,cpuIPs,cpuRefining,cpuUB,iterations,nodes,arcs,deviationMin,deviationRandom,deviationMax,firstLB,vehicles,distance,vehiclesFirst");
		for (int i = 0; i < 10; i++)
		{
			// Initialize file and instance
			String instanceName = "GD-4-"+nrTrips+"-"+i;
			File fileDDD = new File("dataEUC/"+instanceName+".txt");
			Instance inst = new Instance(fileDDD, maxDev, i);
			Solver_DDD solver = new Solver_DDD(inst,dyn_gap,deadhead_type,refiningStrat,optimize_postprocessing,timeLimit,aggregate,iterLimit);
			
			MDVSP.setSeed(1);
			solver.solve();
			if(solver.solved()) {
				pw.println(instanceName+",solved,"+solver.getLB()+","+solver.getUB()+","+solver.getCPU()
				+","+solver.getCpuNetworkCreation()+","+solver.getCpuIPs()+","+solver.getCpuRefining()+","+solver.getCpuUB()
				+","+solver.getIterations()+","+solver.getNodes()+","
				+solver.getArcs()+","+solver.getTotalDevMinimized()+","+solver.getTotalDev()+","+solver.getTotalDevMaximized()+","+solver.getFirstLB()
				+","+solver.getVehicles()+","+solver.getDrivingTime()+","+solver.getVehiclesFirst());
			} else if(solver.foundSolution()) {
				pw.println(instanceName+",notOptimal,"+solver.getLB()+","+solver.getUB()+","+solver.getCPU()
				+","+solver.getCpuNetworkCreation()+","+solver.getCpuIPs()+","+solver.getCpuRefining()+","+solver.getCpuUB()
				+","+solver.getIterations()+","+solver.getNodes()+","
				+solver.getArcs()+","+solver.getTotalDevMinimized()+","+solver.getTotalDev()+","+solver.getTotalDevMaximized()+","+solver.getFirstLB()
				+","+solver.getVehicles()+","+solver.getDrivingTime()+","+solver.getVehiclesFirst());
			} else {
				pw.println(instanceName+",noSolution,"+solver.getLB()+","+solver.getUB()+","+solver.getCPU()
				+","+solver.getCpuNetworkCreation()+","+solver.getCpuIPs()+","+solver.getCpuRefining()+","+solver.getCpuUB()
				+","+solver.getIterations()+","+solver.getNodes()+","+solver.getArcs()+","+solver.getFirstLB()+","+solver.getVehiclesFirst());
			}
			
			pw.flush();
			String identifier = instanceName+"-"+suffix;
			if(solver.foundSolution()) {
				Solution sol = solver.getSolution();
				sol.printSol(new PrintWriter("solutions/"+identifier+".txt"));	
			}
			solver.printDDDstats(new PrintWriter("DDD_stats/"+identifier+".txt"));
					
			
		}
		pw.close();
	}

}
