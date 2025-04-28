package scripts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import ddd.SolverMIP;
import ilog.concert.IloException;
import problem.Instance;

public class MainMIP {

	public static void main(String[] args) throws FileNotFoundException, IloException {
		int nrTrips = 500;
		int maxDev = 3;
		int timeLimit = 30;
		boolean aggregate = true;
		
		String prefix = "mip-"+nrTrips+"-"+maxDev;
		PrintWriter pw = new PrintWriter("results/"+prefix+".txt");
		pw.println("instance,status,lb,ub,cpu,nodes,arcs");
		//iterate over 10 instances of this type and solve them
		for(int i = 0; i<10; i++) {
			String instanceName = "GD-4-"+nrTrips+"-"+i;
			File file = new File("dataEUC/"+instanceName+".txt");
			Instance inst = new Instance(file, maxDev, i);
			
			System.out.println(" Start full network");
			double startTimeFull = System.currentTimeMillis();
			SolverMIP solver = new SolverMIP(inst,aggregate, timeLimit);
			solver.solve();

			double cpuFull = 10e-4*(System.currentTimeMillis() - startTimeFull);

			System.out.println("Full model solved in " + cpuFull + "s. Objective = "+solver.getUB());
			pw.println(instanceName+","+solver.getLB()+","+solver.getUB()+","+solver.getCpu()
			+","+solver.getNodes()+","+solver.getArcs());
			pw.flush();
		}
		pw.close();
	}

}
