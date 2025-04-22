package ddd;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import problem.Instance;
import problem.Location;
import problem.Trip;
import util.Pair;

import java.util.Set;
import java.util.TreeSet;

/**
 * Class representing the timespace network
 */
public class TimeSpaceGraph
{
	private Instance inst;
	private boolean fullNetwork;
	private int deadhead_type;
	private boolean aggregating;
	
	private Location depot;
	private TimedNode startDepot;
	private TimedNode endDepot;
	
	private Map<Location, TreeSet<TimedNode>> nodes; //treesets allow very easy "rounding" of nodes, which is great for DDD
	private Map<Location, TreeSet<TimedNode>> arrivalNodes; //all nodes that correspond to arrivals
	
	private List<TimedArc> arcs;
	private Map<TimedNode, List<TimedArc>> outArcs;
	private Map<TimedNode, List<TimedArc>> inArcs;
	private Map<Location,TimedArc> pullouts;
	private Map<Location,TimedArc> pullins;

	
	private Map<Trip, TreeSet<TimedArc>> tripArcs; //a map from trips to arcs
	private Map<Pair<Location,Location>, TreeSet<TimedArc>> deadheadArcs; // a map from pairs of locations to arcs

	//for stronger deadheads
	private Map<TimedNode,Integer> latestDepTime; //the maximum deviation every node corresponds to
	private Map<TimedNode,Integer> earliestArrTime; //the earliest possible dep time of the trip a node corresponds to
	
	private static int printDetail = 2;
	
	public TimeSpaceGraph(Instance inst, boolean fullNetwork, Location depot, int dh_type, boolean aggregating, boolean initMore)
	{
		this.inst = inst;
		this.fullNetwork = fullNetwork;
		this.depot = depot;
		this.deadhead_type = dh_type;
		this.aggregating = aggregating;
		this.nodes = new HashMap<>();
		
		this.initNodes(fullNetwork,initMore);
		this.constructArcs();
	}
	
	/**
	 * Copy-constructor from another depot. 
	 */
	public TimeSpaceGraph(TimeSpaceGraph toCopy, Location depot) {
		this.inst = toCopy.inst;
		this.depot = depot;
		arcs = new ArrayList<>();
		outArcs = new LinkedHashMap<>();
		inArcs = new LinkedHashMap<>();
		this.nodes = new LinkedHashMap<>();
		this.aggregating = toCopy.aggregating;
		for(Location l: toCopy.nodes.keySet()) {
			if(!l.isDepot()) {
				nodes.put(l, toCopy.nodes.get(l));
				for(TimedNode n: nodes.get(l)) {
					List<TimedArc> outN = new ArrayList<>();
					for(TimedArc out: toCopy.outArcs.get(n)) {
						if(out.getType()!=TimedArc.PULLIN_ARC) {
							outN.add(out);
						}
					}
					outArcs.put(n, outN);
					List<TimedArc> inN = new ArrayList<>();
					for(TimedArc in: toCopy.inArcs.get(n)) {
						if(in.getType()!=TimedArc.PULLOUT_ARC) {
							inN.add(in);
						}
					}
					inArcs.put(n, inN);
				}
			}
			
		}
		
		
		for(TimedArc a: toCopy.arcs) {
			//copy all arcs but the deadhead arcs
			if(!a.getFrom().equals(toCopy.startDepot)&&!a.getTo().equals(toCopy.endDepot)) {
				arcs.add(a);
			}	
		}
		startDepot = addNode(depot,  inst.getStartHorizon());
		endDepot = addNode(depot, inst.getEndHorizon());
		outArcs.put(startDepot, new ArrayList<>());
		inArcs.put(startDepot, new ArrayList<>());
		outArcs.put(endDepot, new ArrayList<>());
		inArcs.put(endDepot, new ArrayList<>());
		this.tripArcs = toCopy.tripArcs;
		this.deadheadArcs = toCopy.deadheadArcs;
		addPullinPulloutArcs();
		
	}
	
	/**
	 * Method to initialize the nodes
	 * @param fullNetwork if we use all time points
	 * @param initMore true if we initialize with the first and with the original start time
	 */
	public void initNodes(boolean fullNetwork, boolean initMore) {
		
		// Initialize depot nodes and depot waiting arcs
		nodes =  new LinkedHashMap<>();		
		arcs =  new ArrayList<>();
		outArcs = new LinkedHashMap<>();
		inArcs = new LinkedHashMap<>();
		tripArcs = new LinkedHashMap<>();
		deadheadArcs = new LinkedHashMap<>();

		startDepot = addNode(depot,  inst.getStartHorizon());
		endDepot = addNode(depot, inst.getEndHorizon());

		// Add trip nodes
		int maxDeviation = inst.getMaxDeviation();
		for(Trip t: inst.getTrips()) {
			if(fullNetwork) {
				for(int dev = -maxDeviation; dev<=maxDeviation; dev++) {
					addNode(t.getStartLocation(), t.getStartTime() + dev);
					addNode(t.getEndLocation(), t.getEndTime() + dev);
				}
			} else if(initMore) {
				addNode(t.getStartLocation(), t.getStartTime() - maxDeviation);
				addNode(t.getEndLocation(), t.getEndTime() - maxDeviation);
				addNode(t.getStartLocation(), t.getStartTime());
				addNode(t.getEndLocation(), t.getEndTime());
			} else {
				addNode(t.getStartLocation(), t.getStartTime() - maxDeviation);
				addNode(t.getEndLocation(), t.getEndTime() - maxDeviation);
			}
		}
		
	}
	
	/**
	 * Method that constructs all the arcs in the network
	 */
	public void constructArcs() {
		long startTime = System.currentTimeMillis();

		deleteArcs();
	
		arrivalNodes =  new LinkedHashMap<>();
		addTripArcs();
		addPullinPulloutArcs();
		if(printDetail>1) {
			System.out.println("Trip arcs and pullouts and pullins added!");
			System.out.println("Nodes: " + getNumNodes() + ". Arcs: " + getNumArcs());
			System.out.println("Time spent: " + (System.currentTimeMillis() - startTime) + "ms");
		}
		
		addDeadheadingArcs();
		
		if(printDetail>1) {	
			System.out.println("Deadheading arcs added!");
			System.out.println("Nodes: " + getNumNodes() + ". Arcs: " + getNumArcs());
			System.out.println("Time spent: " + (System.currentTimeMillis() - startTime) + "ms");
		}
		// Add waiting arcs
		addWaitingArcs();
		if(printDetail>1) {
			System.out.println("Waiting arcs added!");
			System.out.println("Nodes: " + getNumNodes() + ". Arcs: " + getNumArcs());
			System.out.println("Time spent: " + (System.currentTimeMillis() - startTime) + "ms");
		}
	}

	
	/**
	 * Method that creates the trip arcs and directly aggregates them
	 */
	private void addTripArcs() {
		if(printDetail>1) {
			System.out.println("We have "+inst.getTrips().size()+" trips.");
		}
		latestDepTime = new HashMap<>();
		earliestArrTime = new HashMap<>();
		for (Trip t : inst.getTrips())
		{
			TreeSet<TimedArc> arcsT = new TreeSet<>();
			
			//used for finding the latest-first match
			TimedArc keep = null;
			TimedNode tail = null;
			
			for(TimedNode from: nodes.get(t.getStartLocation()) ) {

				if(from.getTime()>=t.getStartTime()-inst.getMaxDeviation()) {
					//not too early, 
					if(from.getTime()<=t.getStartTime()+inst.getMaxDeviation()) {
						//node within time window
						
						TimedNode to = nodes.get(t.getEndLocation()).floor(new TimedNode(t.getEndLocation(),from.getTime()+t.getTripTime()));
						int latest = t.getStartTime()+inst.getMaxDeviation();
						if(!latestDepTime.containsKey(from)||latest>latestDepTime.get(from)) {
							latestDepTime.put(from, latest);
						}
						
						int earliest = t.getEndTime()-inst.getMaxDeviation();
						if(!earliestArrTime.containsKey(to)||earliest<earliestArrTime.get(to)) {
							earliestArrTime.put(to, earliest);
						}
						
						if(!arrivalNodes.containsKey(t.getEndLocation())) {
							arrivalNodes.put(t.getEndLocation(),new TreeSet<>());
						}
						arrivalNodes.get(t.getEndLocation()).add(to);
						TimedArc tripA = new TimedArc(from, to, TimedArc.TRIP_ARC, t, Integer.MAX_VALUE, t.getTripTime());
						
						if(tripA!=null) {
							if(tail!=null&&(!tripA.getTo().equals(tail)||!aggregating)) {
								//we found a new tail, add 
								arcsT.add(keep);
							} 
							keep = tripA; //update the latest arc and the tail
							tail = tripA.getTo();
							
						}
					}  else {
						//outside time window, can terminate
						break;
					}
					
				}
			}
			if(keep!=null) {
				arcsT.add(keep);
			}
			
			for(TimedArc a: arcsT) {
				addArc(a);
			}
			tripArcs.put(t, arcsT);
		}
	}
	
	/**
	 * Method that adds the pullin and pullout arcs
	 */
	private void addPullinPulloutArcs() {
		pullouts = new HashMap<>();
		pullins = new HashMap<>();
		for(Location loc: inst.getLocations()) {
			if(inst.isStartStation(loc)||inst.isEndStation(loc)) {
				TimedNode first = nodes.get(loc).first();
				TimedNode last = nodes.get(loc).last();
				// Create pull-in and pull-out arcs
				
				int pullOutTime = depot.getTimeTo(loc);
				int pullInTime = depot.getTimeFrom(loc);
				
				if(pullOutTime<1000) {
					TimedArc pullout = new TimedArc(startDepot, first, TimedArc.PULLOUT_ARC, Integer.MAX_VALUE, pullOutTime);
					addArc(pullout);
					pullouts.put(loc, pullout);
				} 
				if(pullInTime<1000) {
					TimedArc pullin = new TimedArc(last, endDepot, TimedArc.PULLIN_ARC, Integer.MAX_VALUE, pullInTime);
					addArc(pullin);
					pullins.put(loc, pullin);
				} else {
					//System.out.println("No pullin to "+depot.getIndex()+" from "+loc.getIndex());
				}
			}
		}
	}
	
	/**
	 * Method that adds the deadheading arcs
	 */
	private void addDeadheadingArcs() {
		for(Location locFrom: inst.getLocations()) {
			if(!inst.isEndStation(locFrom)) {
				continue;
			}
			for(Location locTo: inst.getLocations()) {
				if(locTo==locFrom||!inst.isStartStation(locTo)) {
					continue;
				}
				//only add normal sized arcs
				if(locFrom.getTimeTo(locTo)>1000) {
					continue;
				}
				//there are deadhead arcs between these locations
				TreeSet<TimedArc> dhArcs = new TreeSet<>();
				//used for finding the latest-first match
				TimedArc keep = null;
				TimedNode tail = null;
				
				for(TimedNode fromNode: arrivalNodes.get(locFrom)) {
					TimedArc dh = null;
					if(deadhead_type == 3) {
						dh = addDeadheadingArcType3(locFrom,locTo,fromNode,false);
					} else if(deadhead_type == 2){
						dh = addDeadheadingArcType2(locFrom,locTo,fromNode);
					} else {
						dh = addDeadheadingArcType1(locFrom,locTo,fromNode,false);
					}
					if(dh!=null) {
						if(tail!=null&&(!dh.getTo().equals(tail)||!aggregating)) {
							//we found a new tail, add 
							dhArcs.add(keep);
						}
						keep = dh; //update the latest arc and the tail
						tail = dh.getTo();
					}
				}
				if(keep!=null) {
					dhArcs.add(keep);
				}
				if(!dhArcs.isEmpty()) {
					deadheadArcs.put(new Pair<>(locFrom,locTo), dhArcs);
					for(TimedArc a: dhArcs) {
						addArc(a);
					}
				}
			}
		}
	}
	
	/**
	 * Method that adds a short deadheading arc
	 */
	private TimedArc addDeadheadingArcType1(Location locFrom, Location locTo, TimedNode fromNode, boolean print) {
		int deadhead = locFrom.getTimeTo(locTo);
		TimedNode floored = nodes.get(locTo).floor(new TimedNode(locTo,fromNode.getTime()+deadhead));
		TimedNode ceiled = nodes.get(locTo).ceiling(new TimedNode(locTo,fromNode.getTime()+deadhead));
		
		if(floored==null||fullNetwork) {
			if(print) {
				System.out.println("Type 1 Querying node "+locTo.getIndex()+" at "+(fromNode.getTime()+deadhead));
			}

			if(ceiled!=null) {
				if(print) {
					System.out.println("To type 1: "+ceiled);
				}
				return new TimedArc(fromNode, ceiled, TimedArc.DEADHEADING_ARC, Integer.MAX_VALUE, deadhead);
			} 
		} else {
			if(print) {
				System.out.println("To type 1: "+floored);
			}
				return new TimedArc(fromNode, floored, TimedArc.DEADHEADING_ARC, Integer.MAX_VALUE, deadhead);
		}
		System.out.println("Skipping "+fromNode+" to "+locTo);
		return null;
	}

	/**
	 * Method that adds a medium deadheading arc
	 */
	private TimedArc addDeadheadingArcType2(Location locFrom, Location locTo, TimedNode fromNode) {
		int deadhead = locFrom.getTimeTo(locTo);
		TimedNode floored = nodes.get(locTo).floor(new TimedNode(locTo,fromNode.getTime()+deadhead));

		if(floored==null||!latestDepTime.containsKey(floored)||fullNetwork||earliestArrTime.get(fromNode)+deadhead>latestDepTime.get(floored)) {
			TimedNode ceiled = nodes.get(locTo).ceiling(new TimedNode(locTo,fromNode.getTime()+deadhead));
			if(ceiled!=null) {
				return new TimedArc(fromNode, ceiled, TimedArc.DEADHEADING_ARC, Integer.MAX_VALUE, deadhead);
			} 
		} else {
			return new TimedArc(fromNode, floored, TimedArc.DEADHEADING_ARC, Integer.MAX_VALUE, deadhead);
		}
		return null;
	}

	private TimedArc addDeadheadingArcType3(Location locFrom, Location locTo, TimedNode fromNode, boolean print) {
		int deadhead = locFrom.getTimeTo(locTo);
		TimedNode floored = nodes.get(locTo).floor(new TimedNode(locTo,fromNode.getTime()+deadhead));
		if(print) {
			System.out.println("Type 3 Querying node "+locTo.getIndex()+" at "+(fromNode.getTime()+deadhead));
		}
		TimedNode ceiled = nodes.get(locTo).ceiling(new TimedNode(locTo,fromNode.getTime()+deadhead));
		
		if(floored==null||!latestDepTime.containsKey(floored)||fromNode.getTime()+deadhead>latestDepTime.get(floored)||fullNetwork) {
			if(ceiled!=null) {
				return new TimedArc(fromNode, ceiled, TimedArc.DEADHEADING_ARC, Integer.MAX_VALUE, deadhead);
			} else if(print) {
				System.out.println("Ceiled type 3: "+ceiled);
			}

		} else {
			return new TimedArc(fromNode, floored, TimedArc.DEADHEADING_ARC, Integer.MAX_VALUE, deadhead);
		}
		return null;
	}
	
	/**
	 * Method that adds waiting arcs
	 */
	public void addWaitingArcs() {
		for (int locationID = inst.getNumDepots(); locationID < inst.getNumLocations(); locationID++)
		{
			Location l = inst.getLocations().get(locationID);
			if(inst.isStartStation(l)||inst.isEndStation(l)) {
				TreeSet<TimedNode> consecutiveNodes = nodes.get(l);

				TimedNode prev = consecutiveNodes.first();
				for(TimedNode next: consecutiveNodes) {
					if(prev==next) {
						continue;
					}
					addArc(new TimedArc(prev,next, TimedArc.WAITING_STATION_ARC, l.getCapacity(), 0));
					prev = next;
				}
			}
		}
	}
		
	/**
	 * Method that deletes all arcs
	 */
	public void deleteArcs()
	{
		arcs = new ArrayList<>();
		outArcs = new HashMap<>();
		inArcs =  new HashMap<>();
		for(Entry<Location,TreeSet<TimedNode>> ent: nodes.entrySet()) {
			for(TimedNode n: ent.getValue()) {
				outArcs.put(n, new ArrayList<>());
				inArcs.put(n, new ArrayList<>());
			}
		}
		
		tripArcs = new HashMap<>();
	}

	public TimedNode addNode(Location station, int time)
	{
		
		TimedNode newNode = new TimedNode(station,time);
		if(!nodes.containsKey(station)) {
			nodes.put(station, new TreeSet<>());
		}
		nodes.get(station).add(newNode);
		
		return newNode;
	}
	
	public void addArc(TimedArc a) {
		arcs.add(a);
		outArcs.get(a.getFrom()).add(a);
		inArcs.get(a.getTo()).add(a);
	}

	public void removeArc(TimedArc a)
	{
		arcs.remove(a);
		outArcs.get(a.getFrom()).remove(a);
		inArcs.get(a.getTo()).remove(a);
		if(a.getType()==TimedArc.DEADHEADING_ARC) {
			deadheadArcs.get(new Pair<>(a.getFrom().getStation(),a.getTo().getStation())).remove(a);
		}
	}
	
	/**
	 * Method that takes in a feasible sequence of trips, and converts it to a path in the current TI network
	 */
	public List<TimedArc> convertPathNew(List<Trip> original) {
		List<TimedArc> converted = new ArrayList<>();
		
		//do the pullout
		TimedArc pullout = pullouts.get(original.get(0).getStartLocation());
		converted.add(pullout);
		TimedNode curNode = pullout.getTo();

		//the middle part
		for(Trip t: original) {
			//we need to find a path to the start location of t
			Location startOfT = t.getStartLocation();
			//check if you need to perform a deadhead trip
			if(!curNode.getStation().equals(startOfT)) {
				//first find the deadhead arc
				TreeSet<TimedArc> dhs = this.deadheadArcs.get(new Pair<>(curNode.getStation(),startOfT));
				if(dhs==null) {
					System.out.println("\n Trip: "+t);
					System.out.println("No dh from "+curNode.getStation().getIndex()+" to "+startOfT.getIndex()); 
				}
				TimedArc aux1 = new TimedArc(curNode,null, 0, 0, 0);
				TimedArc firstDH = dhs.ceiling(aux1);
				//add waiting arcs at curLoc
				converted.addAll(getWaitingArcs(curNode,firstDH.getFrom()));
				//add the deadhead and update curNode
				converted.add(firstDH);
				curNode = firstDH.getTo();
			}

			//retrieve the trip arc
			TimedArc aux2 = new TimedArc(curNode,null, 0, 0, 0);
			TimedArc tripArc = getTripArcs(depot,t).ceiling(aux2);
			//add waiting arcs at startOfT
			converted.addAll(getWaitingArcs(curNode,tripArc.getFrom()));
			//add the trip arc and update curNode
			converted.add(tripArc);
			curNode = tripArc.getTo();
		}
		
		//do the pullin
		TimedArc pullin = pullins.get(curNode.getStation());
		converted.addAll(getWaitingArcs(curNode,pullin.getFrom()));
		converted.add(pullin);
		
		if(!isPath(converted)) {
			System.out.println("\n Not a path");
			for(TimedArc a: converted) {
				System.out.println(a);
			}
			System.out.println("\n original path");
			for(Trip t: original) {
				System.out.println(t);
			}
			throw new Error("Not a path!");
		}
		
		return converted;
		
	}

	/**
	 * Method that checks if a sequence of arcs is a path
	 */
	private boolean isPath(List<TimedArc> path) {
		if(path.get(0).getFrom().getStation()!=depot) {
			return false;
		}
		if(path.get(path.size()-1).getTo().getStation()!=depot) {
			return false;
		}
		for(int i = 0; i<path.size()-1; i++) {
			TimedNode n1 = path.get(i).getTo();
			TimedNode n2 = path.get(i+1).getFrom();
			if(!n1.equals(n2)) {
				return false;
			}
		}
		
		return true;
	}

	/**
	 * Method that determines the waiting arcs at some location from a certain node to a departure time
	 */
	private List<TimedArc> getWaitingArcs(TimedNode firstNode, TimedNode lastFullNetwork) {
		List<TimedArc> wArcs = new ArrayList<>();
		TreeSet<TimedNode> nodesAtLocation = nodes.get(firstNode.getStation());
		TimedNode lastOne = nodesAtLocation.floor(lastFullNetwork);
		
		TimedNode curr = firstNode;
		while(!curr.equals(lastOne)) {
			TimedNode next = nodesAtLocation.higher(curr);
			wArcs.add(new TimedArc(curr, next, TimedArc.WAITING_STATION_ARC, firstNode.getStation().getCapacity(), 0));
			curr = next;
		}
		return wArcs;
	}


	public int getNumNodes()
	{
		int sum = 0;

		for (Location i : nodes.keySet())
		{
			sum += nodes.get(i).size();
		}

		return sum;
	}

	public int getNumArcs()
	{
		return arcs.size();
	}


	public Map<Location, TreeSet<TimedNode>> getAllNodes()
	{
		return nodes;
	}

	public Set<TimedNode> getNodes(Location station)
	{
		return nodes.get(station);
	}

	public List<TimedArc> getArcs()
	{
		return arcs;
	}

	public List<TimedArc> getOutArcs(TimedNode n)
	{
		return outArcs.get(n);
	}

	public List<TimedArc> getInArcs(TimedNode n)
	{
		return inArcs.get(n);
	}

	public TimedNode getStartDepot()
	{
		return startDepot;
	}

	public TimedNode getEndDepot()
	{
		return endDepot;
	}

	public TreeSet<TimedArc> getTripArcs(Location depot, Trip trip) {
		return tripArcs.get(trip);
	}

	public TreeSet<TimedArc> getDeadheadArcs(Location from, Location to) {
		return deadheadArcs.get(new Pair<>(from,to));
	}
}	

