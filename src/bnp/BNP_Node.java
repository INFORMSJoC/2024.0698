package bnp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import digraph.DirectedGraphArc;
import problem.Location;
import problem.Trip;
import util.Pair;
import util.Triple;

/**
 * Class for modeling a node in the branch-and-bound tree, used in branch-and-price
 */
public class BNP_Node {
	
	private Map<Location,ConnectionNetwork> pricingGraphs;
	private Map<Location,List<CN_Arc>> atLB;
	private double lb;
	private double optValue;
	private final int depth;
	private final String id;
	
	private List<Pair<Location,CN_Arc>> down;
	private List<Pair<Location,CN_Arc>> up;
	
	/**
	 * Constructor for the root node
	 * @param pricingGraphs
	 */
	public BNP_Node(Map<Location,ConnectionNetwork> pricingGraphs) {
		this.pricingGraphs = pricingGraphs;
		this.lb = 0;
		this.atLB = new LinkedHashMap<>();
		for(Location d: pricingGraphs.keySet()) {
			atLB.put(d, new ArrayList<>());
		}
		this.depth = 0;
		this.down = new ArrayList<>();
		this.up = new ArrayList<>();
		this.id = "R";
	}
	
	public BNP_Node(Map<Location, List<CN_Arc>> atLB_copy, BNP_Node parent, List<Pair<Location, CN_Arc>> downCopy, List<Pair<Location, CN_Arc>> upCopy, String dir) {
		this.atLB = atLB_copy;
		this.lb = parent.getOptValue();
		this.pricingGraphs = parent.pricingGraphs;
		this.depth = parent.depth + 1;
		this.down = downCopy;
		this.up = upCopy;
		this.id = parent.id+"-"+dir;
	}

	public void setOptValue(double masterCost) {
		this.optValue = masterCost;
	}
	
	public BNP_Node getDownBranchColor(Triple<Location, Location, Trip> color) {
		Trip t = color.getC();
		Map<Location,List<CN_Arc>> atLB_copy = getMapCopy();
		
		for(Location depot: pricingGraphs.keySet()) {
			if(depot.equals(color.getA())||depot.equals(color.getB())) {
				for(DirectedGraphArc<CN_Node, CN_Arc> arc: pricingGraphs.get(depot).getArcs()) {
					CN_Arc a = arc.getData();
					if(t.equals(a.getFrom().getTrip())||t.equals(a.getTo().getTrip())) {
						atLB_copy.get(depot).add(a);
					}
				}

			}
		}		
		return new BNP_Node(atLB_copy,this,down,up,"D"); 
	}
	
	public BNP_Node getUpBranchColor(Triple<Location, Location, Trip> color) {
		Trip t = color.getC();
		Map<Location,List<CN_Arc>> atLB_copy = getMapCopy();
		
		for(Location depot: pricingGraphs.keySet()) {
			if(!depot.equals(color.getA())&&!depot.equals(color.getB())) {
				for(DirectedGraphArc<CN_Node, CN_Arc> arc: pricingGraphs.get(depot).getArcs()) {
					CN_Arc a = arc.getData();
					if(t.equals(a.getFrom().getTrip())||t.equals(a.getTo().getTrip())) {
						atLB_copy.get(depot).add(a);
					}
				}

			}
		}		
		return new BNP_Node(atLB_copy,this,down,up,"D"); 
	}
	
	public BNP_Node getDownBranch(Triple<Location, Location, CN_Arc> interTask) {

		Map<Location,List<CN_Arc>> atLB_copy = getMapCopy();
		atLB_copy.get(interTask.getA()).add(interTask.getC());
		atLB_copy.get(interTask.getB()).add(interTask.getC());
		return new BNP_Node(atLB_copy,this,down,up,"D"); 
	}
	
	public BNP_Node getDownBranch(Pair<Location,CN_Arc> branchVariable) {
		if(down.contains(branchVariable)) {
			System.out.println("mappie: ");
			for(Location d: pricingGraphs.keySet()) {
				System.out.println("depot "+d+" list: "+atLB.get(d));
			}
			throw new Error("Already have this down branch");
		}
		Map<Location,List<CN_Arc>> atLB_copy = getMapCopy();
		atLB_copy.get(branchVariable.getA()).add(branchVariable.getB());
		

		List<Pair<Location,CN_Arc>> downCopy = new ArrayList<>(down);
		downCopy.add(branchVariable);
		return new BNP_Node(atLB_copy,this,downCopy,up,"D");
	}

	private Map<Location, List<CN_Arc>> getMapCopy() {
		Map<Location, List<CN_Arc>> copy = new LinkedHashMap<>();
		for(Location d: pricingGraphs.keySet()) {
			copy.put(d, new ArrayList<>(atLB.get(d)));
		}
		return copy;
	}

	public BNP_Node getUpBranch(Pair<Location,CN_Arc> pair) {
		if(up.contains(pair)) {
			throw new Error("Already have this up branch");
		}
		Location depotBranch = pair.getA();
		CN_Arc branchVariable = pair.getB();
		Map<Location,List<CN_Arc>> atLB_copy = getMapCopy();
		
		//suppose it is an arc to trip j, then all other arcs to trip are forbidden
		for(Location depot: pricingGraphs.keySet()) {
			for(DirectedGraphArc<CN_Node, CN_Arc> arc: pricingGraphs.get(depot).getArcs()) {
				CN_Arc c_arc = arc.getData();
				if(c_arc.getType()!=0) {
					//not a connection arc between two trips, skip!
					continue;
				}
				boolean sameTail = (c_arc.getFrom().getTrip().equals(branchVariable.getFrom().getTrip()));
				boolean sameHead = (c_arc.getTo().getTrip().equals(branchVariable.getTo().getTrip()));
				
				if(!sameTail && !sameHead) {
					//no match, should continue;
					continue;
				}
				if(depotBranch.equals(depot)) {
					//matching depots,
					if(c_arc.getType()==0) {
						//trip arc, can only stay if both are the same
						if(!sameTail||!sameHead) {
							atLB_copy.get(depot).add(c_arc);
						} 
					} 
					
				} else {
					//the depots don't match, should always set flow to 0
					atLB_copy.get(depot).add(c_arc);
				}
			}
			
		}
		List<Pair<Location,CN_Arc>> upCopy = new ArrayList<>(up);
		upCopy.add(pair);
		
		return new BNP_Node(atLB_copy,this,down,upCopy,"U");
	}
	
	public BNP_Node getUpBranch(Triple<Location,Location,CN_Arc> interTask) {
		Location depotBranch1 = interTask.getA();
		Location depotBranch2 = interTask.getB();
		CN_Arc branchVariable = interTask.getC();
		Map<Location,List<CN_Arc>> atLB_copy = getMapCopy();
		
		//suppose it is an arc to trip j, then all other arcs to trip are forbidden
		for(Location depot: pricingGraphs.keySet()) {
			for(DirectedGraphArc<CN_Node, CN_Arc> arc: pricingGraphs.get(depot).getArcs()) {
				CN_Arc c_arc = arc.getData();
				if(c_arc.getType()!=0) {
					//not a connection arc between two trips, skip!
					continue;
				}
				boolean sameTail = (c_arc.getFrom().getTrip().equals(branchVariable.getFrom().getTrip()));
				boolean sameHead = (c_arc.getTo().getTrip().equals(branchVariable.getTo().getTrip()));
				
				if(!sameTail && !sameHead) {
					//no match, should continue;
					continue;
				}
				if(depotBranch1.equals(depot)||depotBranch2.equals(depot)) {
					//matching depots,
					if(c_arc.getType()==0) {
						//trip arc, can only stay if both are the same
						if(!sameTail||!sameHead) {
							atLB_copy.get(depot).add(c_arc);
						} 
					} 
					
				} else {
					//the depots don't match, should always set flow to 0
					atLB_copy.get(depot).add(c_arc);
				}
			}
			
		}
		return new BNP_Node(atLB_copy,this,down,up,"U");
	}
	
	
	
	@Override
	public String toString() {
		return "BNP_Node [id="+id+" depth=" + depth + ", down=" + down + ", up=" + up + "]";
	}

	public Map<Location, List<CN_Arc>> getAtLowerBound() {
		return atLB;
	}

	public double getLB() {
		return lb;
	}

	public double getOptValue() {
		return optValue;
	}

	public int getDepth() {
		return depth;
	}

	public void addToLB(Pair<Location, CN_Arc> toFix) {
		atLB.get(toFix.getA()).add(toFix.getB());
	}


	
}
