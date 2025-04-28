package problem;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import ddd.TimedTrip;

/**
 * Class for modeling an instance of the MDVSP-TS
 */
public class Instance 
{
	
	public static int FIXED_COST = 500; 	//pull-ins and pull-outs have to pay the fixed cost, so 1000 per vehicle
	public static int VARIABLE_COST = 1;	//variable cost per minute driving
	
	private int numDepots;
	private int numTrips;
	private int numLocations;
	private int ind;

	private List<Location> locations;
	private Set<Location> startLocations;
	private Set<Location> endLocations;
	private List<Trip> trips;

	private int startHorizon;
	private int earliestStart;
	private int endHorizon;
	private int horizon;

	private final int maxDeviation;
	
	public Instance(File f, int maxDeviation, int ind) throws FileNotFoundException
	{
		this.maxDeviation = maxDeviation;
		this.ind = ind;
		readFile(f);
		determineStartAndEndLocations();
	}

	private void determineStartAndEndLocations() {
		startLocations = new HashSet<>();
		endLocations = new HashSet<>();
		for(Trip t: trips) {
			startLocations.add(t.getStartLocation());
			endLocations.add(t.getEndLocation());
		}
		
	}
	
	/**
	 * Method that makes a new instance with fixed deptimes based on a solution
	 * @param parentInst
	 * @param sol
	 */
	public Instance(Instance parentInst, Solution sol) {
		List<TimedTrip> timed = sol.getTimedTrips();
		numDepots = parentInst.numDepots;
		numTrips = timed.size();
		numLocations = parentInst.numLocations;
		trips = new ArrayList<>();
		for(TimedTrip tt: timed) {
			Trip t = tt.getT();
			trips.add(new Trip(t.getID(),t.getStartLocation(),tt.getDepTime(),t.getEndLocation(),tt.getDepTime()+t.getTripTime()));
		}
		ind = -1;
		startHorizon = parentInst.startHorizon;
		endHorizon = parentInst.endHorizon;
		earliestStart = parentInst.earliestStart;
		horizon = parentInst.horizon;
		locations = new ArrayList<>(parentInst.locations);
		maxDeviation = parentInst.maxDeviation;
		determineStartAndEndLocations();
	}

	public Instance(Instance parentInst, List<Trip> unserved) {
		numDepots = parentInst.numDepots;
		numTrips = unserved.size();
		numLocations = parentInst.numLocations;
		trips = unserved;
		ind = -1;
		startHorizon = parentInst.startHorizon;
		endHorizon = parentInst.endHorizon;
		earliestStart = parentInst.earliestStart;
		horizon = parentInst.horizon;
		locations = new ArrayList<>(parentInst.locations);
		maxDeviation = parentInst.maxDeviation;
		determineStartAndEndLocations();
	}
	
	public Instance(Instance parentInst, int depotID) {
		numDepots = 2;
		//determine locations, only take ones closest to the depot
		Location centerDepot = parentInst.getLocations().get(depotID);
		Location otherDepot = parentInst.getLocations().get(1-depotID); 
		
		
		//determine trips based on locations
		trips = new ArrayList<>();
		Set<Location> locSet = new HashSet<>();
		for(Trip t: parentInst.getTrips()) {
			Location start = t.getStartLocation();
			Location end = t.getEndLocation();
			int distCenter = centerDepot.getTimeFrom(start)+centerDepot.getTimeFrom(end);
			int distOther = otherDepot.getTimeFrom(start)+otherDepot.getTimeFrom(end);
			if(distCenter<distOther || (distCenter==distOther&&depotID==1)) {
				trips.add(t);
				locSet.add(start);
				locSet.add(end);
			}
		}
		
		locations = new ArrayList<>();
		locations.add(centerDepot);
		locations.add(parentInst.getLocations().get(2));
		for(int i = 3; i<parentInst.numLocations; i++) {
			Location l_i = parentInst.getLocations().get(i);
			if(locSet.contains(l_i)) {
				locations.add(l_i);
			}
		}
		numLocations = locations.size();
		
		numTrips = trips.size();
		System.out.println("Created instance with "+numTrips + " trips and "+numLocations+" locations");
		ind = -1;
		startHorizon = parentInst.startHorizon;
		endHorizon = parentInst.endHorizon;
		earliestStart = parentInst.earliestStart;
		maxDeviation = parentInst.maxDeviation;
		determineStartAndEndLocations();
	}

	public void readFile(File f) throws FileNotFoundException
	{
		Scanner sc = new Scanner(f);

		numDepots = sc.nextInt();
		numTrips = sc.nextInt();
		numLocations = sc.nextInt();

		locations = new ArrayList<>();
		trips = new ArrayList<>();

		// Initialize depots (type = 0)
		for (int i = 0; i < numDepots; i++)
		{
			Location depot = new Location(0, sc.nextInt(), i);
			locations.add(depot);
		}

		// Initialize stations (type = 1)
		for (int i = numDepots; i < numLocations; i++)
		{
			Location station = new Location(1, Integer.MAX_VALUE, i);
			locations.add(station);
		}

		// Initialize trips
		startHorizon = 0;
		earliestStart = Integer.MAX_VALUE;
		endHorizon = 0;

		for (int i = 0; i < numTrips; i++)
		{
			Trip tr = new Trip(i+1,locations.get(sc.nextInt()), sc.nextInt(), locations.get(sc.nextInt()), sc.nextInt());
			trips.add(tr);

			if (tr.getEndTime() > endHorizon)
			{
				endHorizon = tr.getEndTime();
			}

			if (tr.getStartTime() < earliestStart)
			{
				earliestStart = tr.getStartTime();
			}


		}
		
		Collections.sort(trips);
		
		horizon = endHorizon - earliestStart;

		endHorizon += 10; // The Readme file definition of the end of the time horizon

		// Read travel times matrix
		for (int i = 0; i < numLocations; i++)
		{
			for (int j = 0; j < numLocations; j++)
			{
				int travelTime = sc.nextInt();

				locations.get(i).addTimeTo(locations.get(j), travelTime);		
				locations.get(j).addTimeFrom(locations.get(i), travelTime);
				if(i==j && travelTime>0) {
					throw new Error("huh wat? "+travelTime+" index: "+i);
				}
			}
		}

		sc.close();
		System.out.println("File successfully read!");
		this.checkTriangleInequality();
	}
	
	public boolean checkTriangleInequality() {
		boolean satisfied = true;
		for(Location from: locations) {
			for(Location to: locations) {
				int direct = from.getTimeTo(to);
				for(Location via: locations) {
					int indirect = from.getTimeTo(via)+via.getTimeTo(to);
					if(indirect<direct) {
						/*if(indirect<20) {
							System.out.println("direct: "+direct + " indirect: "+indirect);
							System.out.println("From: "+from.getIndex()+" to: "+to.getIndex()+" via: "+via.getIndex());
						}*/

						//System.out.println("FAIL SO TRY AGAIN");
						satisfied = false;
						//return false;
					}
				}
			}
		}
		if(!satisfied) {
			//throw new Error("Not satisfied");
		}
		return satisfied;
	}
	
	public void fixTriangleInequality() {
		boolean satisfied = false;
		while(!satisfied) {
			System.out.println("Correcting triangle inequality ");
			satisfied = true;
			for(Location from: locations) {
				for(Location to: locations) {
					int direct = from.getTimeTo(to);
					for(Location via: locations) {
						int indirect = from.getTimeTo(via)+via.getTimeTo(to);
						if(indirect<direct) {
							from.addTimeTo(to, indirect);
							to.addTimeFrom(from, indirect);
							/*if(indirect<20) {
								System.out.println("direct: "+direct + " indirect: "+indirect);
								System.out.println("From: "+from.getIndex()+" to: "+to.getIndex()+" via: "+via.getIndex());
							}*/

							//System.out.println("FAIL SO TRY AGAIN");
							satisfied = false;
							//return false;
						}
					}
				}
			}
		}
	}

	public int getNumDepots()
	{
		return numDepots;
	}

	public int getNumTrips()
	{
		return numTrips;
	}

	public int getNumLocations()
	{
		return numLocations;
	}
	
	public int getInd() 
	{
		return ind;
	}

	public List<Location> getLocations()
	{
		return locations;
	}

	public List<Location> getDepots()
	{
		return locations.subList(0, numDepots);
	}

	public List<Location> getStations()
	{
		return locations.subList(numDepots, numLocations);
	}

	public List<Trip> getTrips()
	{
		return trips;
	}

	public int getStartHorizon()
	{
		return startHorizon;
	}

	public int getEndHorizon()
	{
		return endHorizon;
	}

	public int getMaxDeviation()
	{
		return maxDeviation;
	}

	public boolean isEndStation(Location l) {
		return this.endLocations.contains(l);
	}

	public boolean isStartStation(Location l) {
		return this.startLocations.contains(l);
	}

	public int getHorizon() {
		return horizon;
	}

	public void checkLongTrips() {
		int nLong = 0;
		int longest = 0;
		for(Trip t: trips) {
			if(t.getTripTime()>longest) {
				longest = t.getTripTime();
			}
		}
		System.out.println("There are "+nLong +" long trips" +" longest has time "+longest);
	}
}
