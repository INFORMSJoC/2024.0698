package bnp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import ddd.TimedArc;
import digraph.DirectedGraphArc;
import problem.Instance;
import problem.Trip;

public class ShortestPathWithResources {
	private ConnectionNetwork dag;
	private final int maxDeviation;
	
	private Map<CN_Node,List<Label>> labelsPerNode;
	
	private Label winner; 
	private List<Label> bestLabels;
	
	private List<CN_Arc> forbiddenArcs;
	
	public ShortestPathWithResources(ConnectionNetwork dag, int maxDeviation) {
		this.dag = dag;
		this.maxDeviation = maxDeviation;
		labelsPerNode = new LinkedHashMap<>();
		forbiddenArcs = new ArrayList<>();
	}

	public double getDistance() {
		if(winner==null) {
			System.out.println("No path, returning 0");
			return 0;
		}
		return winner.getCost();
	}
	
	public Route getBestRoute() {
		List<Trip> tripList = new ArrayList<>();
		List<CN_Arc> arcList = new ArrayList<>();
		Label curLabel = bestLabels.get(0);
		double redCost = curLabel.getCost();
		while(curLabel!=null) {
			if(curLabel.getNode().isTripNode()) {
				tripList.add(curLabel.getNode().getTrip());
				arcList.add(curLabel.getLastArc());
			}
			curLabel = curLabel.getPredecessor();
		}
		Collections.reverse(tripList);
		Collections.reverse(arcList);
		return new Route(dag.getSource().getDepotLoc(),tripList,arcList);
	}
	
	public double getReducedCost(CN_Arc a) {
		if(a.getType()==0) {
			return a.getDist()+getDistance(a.getFrom())-getDistance(a.getTo());
		} else {
			return Instance.FIXED_COST+ a.getDist()+getDistance(a.getFrom())-getDistance(a.getTo());
		}
	}
	
	public double getDistance(CN_Node n) {
		List<Label> labels = labelsPerNode.get(n);
		Collections.sort(labels);
		return labels.get(0).getCost();
	}
	
	public List<Route> getBestRoutes(int k) {
		List<Route> bestRoutes = new ArrayList<>();
		Collections.sort(bestLabels);//, Comparator.comparingDouble(item -> item.getCost())); 
		
		for(int i = 1; i<=k; i++) {
			List<Trip> tripList = new ArrayList<>();
			List<CN_Arc> arcList = new ArrayList<>();
			Label curLabel = bestLabels.get(i-1);
			double redCost = curLabel.getCost();
			if(redCost<-1e-4) {
				//System.out.println(i+"th label with red cost: "+redCost);
				while(curLabel!=null) {
					if(curLabel.getNode().isTripNode()) {
						tripList.add(curLabel.getNode().getTrip());
						arcList.add(curLabel.getLastArc());
					}
					curLabel = curLabel.getPredecessor();
				}
				Collections.reverse(tripList);
				Collections.reverse(arcList);
				bestRoutes.add(new Route(dag.getSource().getDepotLoc(),tripList,arcList));
			} else {
				break;
			}
			
		}
		
		
		return bestRoutes;
	}
	
	public void computeDistances() {
		//initialise the labels
		for(CN_Node node: dag.getNodes()) {
			labelsPerNode.put(node,new ArrayList<>());
		}
		//add the label for the origin
		labelsPerNode.get(dag.getSource()).add(new Label(dag.getSource(),0,0,null,null));
		bestLabels = new ArrayList<>();

		//System.out.println("Start scanning");
		for(CN_Node node: dag.getSortedNodes()) {
			//System.out.println("Scanning: "+node);
			scan(node);
		}
		//System.out.println("Stop scanning");
		
		//determine winner
		double shortest = Double.MAX_VALUE;
		winner = null;
		for(Label label: labelsPerNode.get(dag.getSink())) {
			if(label.getCost()<shortest) {
				shortest = label.getCost();
				winner = label;
			}
		}
	}
	
	
	
	
	private void scan(CN_Node node) {
		//System.out.println("Checking node: "+node +" nr labels :"+labelsPerNode.get(node).size());
		for(Label label: labelsPerNode.get(node)) {
//			if(node.isTripNode()&&node.getTrip().getID()==98) {
//				System.out.println("Checking label: "+label);
//			}
			//System.out.println("Checking label: "+label);
			double oldCost = label.getCost();
			int oldTime = label.getStartTime();
			if(node.isTripNode()) {
				oldTime += node.getTrip().getTripTime();
			}
			for(DirectedGraphArc<CN_Node, CN_Arc> outArc: dag.getOutArcs(node)) {
				if(forbiddenArcs.contains(outArc.getData())) {
					continue;
				}
				double cost = outArc.getCost();
				int dist = outArc.getData().getDist();
				double newCost = oldCost+cost;
				int arrTime = oldTime+dist; //arrival time at the start of the trip
//				if(node.isTripNode()&&node.getTrip().getID()==98&&outArc.getTo().isTripNode()&&outArc.getTo().getTrip().getID()==88) {
//					System.out.println("Checking label: "+label);
//				}
//				if(node.isSource()&&outArc.getTo().isTripNode()&&outArc.getTo().getTrip().getID()==98) {
//					System.out.println("Checking label: "+label);
//				}
				int newTime = arrTime;
				boolean feasibleExtension = true;
				
				if(outArc.getTo().isTripNode()) {
					Trip to = outArc.getTo().getTrip();
					if(arrTime<to.getStartTime()-maxDeviation) {
						//we're too early
						newTime = to.getStartTime()-maxDeviation;
					} else if(arrTime<=to.getStartTime()+maxDeviation) {
						//we're still in time
						newTime = arrTime;
					} else {
//						if(node.isSource()&&outArc.getTo().isTripNode()&&outArc.getTo().getTrip().getID()==98) {
//							System.out.println("Arrtime: "+arrTime);
//						}
						feasibleExtension = false;//else we're too late, don't make new label
					}
				}
//				if(node.isSource()&&outArc.getTo().isTripNode()&&outArc.getTo().getTrip().getID()==98) {
//					System.out.println("Feasible extension: "+feasibleExtension);
//				}
				
				if(feasibleExtension) {
					Label newLabel = new Label(outArc.getTo(),newCost,newTime,label,outArc.getData());
					addLabel(newLabel);
				}
			}
		}
		
	}
	
	/**
	 * Method that adds the label after a dominance check
	 */
	private void addLabel(Label newLabel) {
		List<Label> existingLabels = labelsPerNode.get(newLabel.getNode());
		boolean dominated = false;
		
		List<Label> isDominated = new ArrayList<>();
		for(Label other: existingLabels) {
			//do the dominance check 
			if(other.getCost()<=newLabel.getCost()&&other.getStartTime()<=newLabel.getStartTime()) {
				dominated = true;
				break;
			} else if(other.getCost()>=newLabel.getCost()&&other.getStartTime()>=newLabel.getStartTime()) {
				isDominated.add(other);
			}
		}
		
		if(!dominated) {
			existingLabels.add(newLabel);
		}
		existingLabels.removeAll(isDominated);
		if(newLabel.getNode().equals(dag.getSink())) {
			bestLabels.add(newLabel);
		}
	}

	private class Label implements Comparable<Label> {
		private final CN_Node node;
		private final double cost;
		private final int startTime;
		private final CN_Arc lastArc;
		private final Label predecessor;
		
		public Label(CN_Node node, double cost, int startTime, Label predecessor, CN_Arc lastArc) {
			super();
			this.node = node;
			this.cost = cost;
			this.startTime = startTime;
			this.lastArc = lastArc;
			this.predecessor = predecessor;
		}

		public CN_Arc getLastArc() {
			return lastArc;
		}

		public CN_Node getNode() {
			return node;
		}

		public double getCost() {
			return cost;
		}

		public int getStartTime() {
			return startTime;
		}

		public Label getPredecessor() {
			return predecessor;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getEnclosingInstance().hashCode();
			result = prime * result + Objects.hash(cost, node, predecessor, startTime);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Label other = (Label) obj;
			if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
				return false;
			return cost == other.cost && Objects.equals(node, other.node)
					&& Objects.equals(predecessor, other.predecessor) && startTime == other.startTime;
		}

		private ShortestPathWithResources getEnclosingInstance() {
			return ShortestPathWithResources.this;
		}

		@Override
		public String toString() {
			return "Label [node=" + node + ", distance=" + cost + ", startTime=" + startTime+"]";
		}
		
		public int compareTo(Label o) {
			return Double.compare(this.cost, o.cost);
		}
		
	}

	public void setForbiddenArcs(List<CN_Arc> forbiddenArcs) {
		this.forbiddenArcs = forbiddenArcs;
		//System.out.println("nr forbidden: "+forbiddenArcs.size());
//		for(CN_Arc a: forbiddenArcs) {
//			System.out.println("Forbidden: "+a);
//		}
	}
	
	
	
}


