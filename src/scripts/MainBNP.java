package scripts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import bnp.BranchAndPrice;
import ilog.concert.IloException;
import problem.Instance;

public class MainBNP {
	public static void main(String[] args) throws IloException, FileNotFoundException {
		int nrTrips = 250;
		int maxDev = 1;
		int timeLimit = 300;
		
		String prefix = "bnp-"+nrTrips+"-"+maxDev;
		PrintWriter pw = new PrintWriter("results/"+prefix+".txt");
		pw.println("instance,root,lb,ub,cpuTotal,cpuRoot,cpuMaster,cpuPricing,nodes");
		//iterate over 10 instances of this type and solve them
		for(int i = 0; i<10; i++) {
			String instanceName = "GD-4-"+nrTrips+"-"+i;
			File file = new File("dataEUC/"+instanceName+".txt");
			Instance inst = new Instance(file, maxDev, i);
			
			System.out.println(" Start BNP on instance "+i);
			BranchAndPrice cG = new BranchAndPrice(inst, timeLimit);
			cG.branchAndPrice();			
			pw.println(instanceName+","+cG.getRootBound()+","+cG.getLB()+","+cG.getUB()+","+cG.getTimeTotal()
			+","+cG.getTimeRoot()+","+cG.getTimeMaster()+","+cG.getTimePricing()+","+cG.getNodes());
			pw.flush();
		}
		pw.close();
	}

}
