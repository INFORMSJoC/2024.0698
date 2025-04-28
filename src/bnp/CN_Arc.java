package bnp;

import java.util.Objects;

/**
 * Class for modeling an arc in the Connection Network *
 */
public class CN_Arc  {
	private final CN_Node from;
	private final CN_Node to; 
	private final int cost;
	private final int dist;
	private final int type; //0 means trip node, -1 means source and 1 means sink
	public CN_Arc(CN_Node from, CN_Node to, int cost, int dist) {
		super();
		this.from = from;
		this.to = to;
		this.cost = cost;
		this.dist = dist;
		int nodeType = 0; 
		if(from.isSource()) {
			nodeType = -1;
		} else if(!to.isTripNode()) {
			nodeType = 1;
		}
		this.type = nodeType;
	}
	
	public int getDist() {
		return dist;
	}

	public CN_Node getFrom() {
		return from;
	}

	public CN_Node getTo() {
		return to;
	}

	public int getCost() {
		return cost;
	}

	@Override
	public String toString() {
		return "CN_Arc [from=" + from.getTrip() + ", to=" + to.getTrip() + ", cost=" + cost + ", dist=" + dist + "]";
	}

	public int getType() {
		return type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(cost, dist, from, to, type);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CN_Arc other = (CN_Arc) obj;
		return cost == other.cost && dist == other.dist && Objects.equals(from, other.from)
				&& Objects.equals(to, other.to) && type == other.type;
	}
	
	
}
