package bnp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import digraph.DirectedGraph;
import digraph.DirectedGraphArc;
import problem.Instance;
import problem.Location;
import problem.Trip;

/**
 * Class for modeling a connection network
 */
public class ConnectionNetwork extends DirectedGraph<CN_Node, CN_Arc> {
	
	//maximum waiting time and deadhead time for deadheading arcs
	private final static int maxWaitingTime = 800;
	private final static int maxDeadhead = 650;

	private CN_Node source;
	private CN_Node sink;
	private Map<Trip,CN_Node> tripToNode;
	private Map<Trip,List<DirectedGraphArc<CN_Node,CN_Arc>>> outPerTrip;
	private Map<Trip,CN_Arc> pullOuts;
	private Map<Trip,CN_Arc> pullIns;
	private List<CN_Node> sortedNodes;
	
	public ConnectionNetwork(Instance inst, Location depot) {
		
		source = new CN_Node(depot,true);
		addNode(source);
		sink = new CN_Node(depot,false);
		addNode(sink);
		pullIns = new LinkedHashMap<>();
		pullOuts = new LinkedHashMap<>();
		
		tripToNode = new LinkedHashMap<>();
		for(Trip t: inst.getTrips()) {
			CN_Node tNode = new CN_Node(t);
			addNode(tNode);
			tripToNode.put(t, tNode);
			
			int dhOut = depot.getTimeTo(t.getStartLocation());
			int dhIn = depot.getTimeFrom(t.getEndLocation());
			CN_Arc pullOut = new CN_Arc(source,tNode,Instance.FIXED_COST+dhOut,dhOut);
			addArc(source,tNode,pullOut,pullOut.getCost());
			pullOuts.put(t, pullOut);
			CN_Arc pullIn = new CN_Arc(tNode,sink,Instance.FIXED_COST+dhIn,dhIn);
			addArc(tNode,sink,pullIn,pullIn.getCost());
			pullIns.put(t, pullIn);
		}
		
		outPerTrip = new LinkedHashMap<>();
		//now add all the connections
		for(Trip t1: inst.getTrips()) {
			List<DirectedGraphArc<CN_Node,CN_Arc>> arcsT1 = new ArrayList<>();
			for(Trip t2: inst.getTrips()) {
				if(t1.equals(t2)) {
					continue;
				}
				int dh = t1.getEndLocation().getTimeTo(t2.getStartLocation());
				int waitingTime = t2.getStartTime()-(t1.getEndTime()+dh)+2*inst.getMaxDeviation();
				
				if(waitingTime>=0 && waitingTime-2*inst.getMaxDeviation()<maxWaitingTime&&dh<=maxDeadhead) {
					//trips are potentially compatible
					CN_Node from = tripToNode.get(t1);
					CN_Node to = tripToNode.get(t2);
					CN_Arc conn = new CN_Arc(from,to,dh,dh);
					DirectedGraphArc<CN_Node,CN_Arc> d_conn = addArc(from,to,conn,dh);
					arcsT1.add(d_conn);
				}
			}
			outPerTrip.put(t1, arcsT1);
		}
		
		sortedNodes = new ArrayList<>(nodes);
		Collections.sort(sortedNodes, new CN_NodeComparator());
	}
	
	public List<CN_Node> getSortedNodes() {
		return sortedNodes;
	}
	
	/**
	 * Method that updates the arc costs based on duals, used by branch-and-price
	 */
	public void updateCosts(Map<Trip,Double> duals, boolean masterFeasible) {
		
		for(DirectedGraphArc<CN_Node,CN_Arc> cArc: arcs) {
			double dual = 0;
			if(cArc.getFrom().isTripNode()) {
				dual = duals.get(cArc.getFrom().getTrip());
			}
			if(masterFeasible) {
				//pay attention here: we use the original cost of the data!
				double newCost = cArc.getData().getCost()-dual;
				cArc.setCost(newCost,0);
			} else {
				double newCost = -dual;
				cArc.setCost(newCost,0);
			}
		}
	}

	public CN_Node getSource() {
		return source;
	}

	public CN_Node getSink() {
		return sink;
	}

	public CN_Arc getPullOut(Trip t) {
		return pullOuts.get(t);
	}

	public CN_Arc getPullIn(Trip t) {
		return pullIns.get(t);
	}
}
