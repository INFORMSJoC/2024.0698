package problem;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

/**
 * Class used to generate instances for the MDVSP, using the Carpaneto method
 * @author 20215535
 *
 */
public class InstanceGenerator {
	
	private final static int NUM_DEPOTS = 4;
	private final static int MIN_LOCATIONS = 75;
	private final static int MAX_LOCATIONS = 75; 
	private final static int GRID_SIZE = 60;
	
	private final int num_trips;
	private final double short_trips;

	
	public InstanceGenerator(int num_trips, double short_trips) {
		this.num_trips = num_trips;
		this.short_trips = short_trips;
	}
	
	
	public File generateDataFile(int ind) throws IOException
	{
		int propHelp = (int) Math.round(10*short_trips);
		String instName = "GD-" + NUM_DEPOTS + "-" + num_trips + "-0."+propHelp+"-" + ind;
		if(propHelp==10) {
			instName = "GD-" + NUM_DEPOTS + "-"+num_trips+"-1.0"+"-" + ind;
		}
		// Create data file
		File data = new File("dataVaryShort/" +instName+".txt");
		BufferedWriter bw = new BufferedWriter(new FileWriter(data));

		// Write first line
		bw.write(NUM_DEPOTS + " ");
		bw.write(num_trips + " ");
		
		Random rand = new Random((int) Math.round(ind*num_trips*short_trips));
		if(short_trips==0) {
			rand = new Random((int) Math.round(ind*num_trips*(short_trips+1)));
		}
		int numLocations = NUM_DEPOTS + MIN_LOCATIONS + rand.nextInt(MAX_LOCATIONS - MIN_LOCATIONS + 1);
		System.out.println(numLocations);
		bw.write(numLocations + " ");
		bw.newLine();

		// Generate depot capacities
		for (int i = 0; i < NUM_DEPOTS; i++)
		{
			bw.write(num_trips + " ");
		}

		bw.newLine();

		// Generate coordinates (0 = no location, 1 = trip location, 2 = depot location)
		double[][] coordinates = new double[NUM_DEPOTS + numLocations][2];

		coordinates[0][0] = 0;
		coordinates[0][1] = 0;
		coordinates[1][0] = GRID_SIZE;
		coordinates[1][1] = 0;
		coordinates[2][0] = GRID_SIZE;
		coordinates[2][1] = GRID_SIZE;
		coordinates[3][0] = 0;
		coordinates[3][1] = GRID_SIZE;

		boolean satisfiesTI = false;
		// Generate distance matrix
		int[][] distance = new int[numLocations][numLocations];
		while(!satisfiesTI) {
			for (int i = 4; i < NUM_DEPOTS + numLocations; i++)
			{
				coordinates[i][0] = GRID_SIZE * rand.nextDouble();
				coordinates[i][1] = GRID_SIZE * rand.nextDouble();
			}

			for (int i = 0; i < numLocations; i++)
			{
				for (int j = i+1; j < numLocations; j++)
				{
					distance[i][j] = (int) Math.round(Math.sqrt(Math.pow(coordinates[i][0] - coordinates[j][0], 2) + Math.pow(coordinates[i][1] - coordinates[j][1], 2)))+1;
					distance[j][i] = distance[i][j];
				}
			}
			satisfiesTI = (satisfiesTI(distance));
		}
		
		int nShort = (int) Math.round(short_trips*num_trips);

		// Generate short trips
		for (int i = 0; i < nShort; i++)
		{
			int from = NUM_DEPOTS + rand.nextInt(numLocations - NUM_DEPOTS);
			int to = NUM_DEPOTS + rand.nextInt(numLocations - NUM_DEPOTS);

			while (from == to)
			{
				to = NUM_DEPOTS + rand.nextInt(numLocations - NUM_DEPOTS);
			}

			int startTime;
			double prob = rand.nextDouble();

			if (prob < 0.15)
			{
				startTime = 420 + rand.nextInt(60);
			}
			else if (prob < 0.85)
			{
				startTime = 480 + rand.nextInt(540);
			}
			else
			{
				startTime = 1020 + rand.nextInt(60);
			}

			int endTime = startTime + distance[from][to] + 5 + rand.nextInt(36);
			bw.write(from + " " + startTime + " " + to + " " + endTime);
			bw.newLine();
		}

		// Generate long trips
		for (int i = 0; i < num_trips-nShort; i++)
		{
			int location = NUM_DEPOTS + rand.nextInt(numLocations - NUM_DEPOTS);
			int startTime = 300 + rand.nextInt(900);
			int endTime = startTime + 180 + rand.nextInt(120);
			bw.write(location + " " + startTime + " " + location + " " + endTime);
			bw.newLine();
		}

		// Write distance matrix
		for (int i = 0; i < numLocations; i++)
		{
			for (int j = 0; j < numLocations; j++)
			{
				bw.write(distance[i][j] + " ");
			}

			bw.newLine();
		}

		bw.close();		
		return data;
	}


	private boolean satisfiesTI(int[][] distance) {
		for(int from = 0; from<distance.length; from++) {
			for(int to = 0; to<distance.length; to++) {
				int direct = distance[from][to];
				for(int via = 0; via<distance.length; via++) {
					int indirect = distance[from][via]+distance[via][to];
					if(indirect<direct) {
						System.out.println("FAIL SO TRY AGAIN");
						return false;
					}
				}
			}
		}
		return true;
	}
	
}
