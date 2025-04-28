package bnp;

import java.util.Objects;

import digraph.DirectedGraphNodeIndex;
import problem.Location;
import problem.Trip;

/**
 * Class for modeling a node in the connection network
 */
public class CN_Node extends DirectedGraphNodeIndex {
	private final boolean isTripNode;
	private final Trip trip;
	private final Location depotLoc;
	private final boolean isSource;
	
	public CN_Node(Trip t) {
		isTripNode = true;
		this.trip = t;
		depotLoc = null;
		isSource = false;
	}
	
	public CN_Node(Location dLoc, boolean isSource) {
		isTripNode = false;
		this.trip = null;
		this.depotLoc = dLoc;
		this.isSource = isSource;
	}

	public boolean isTripNode() {
		return isTripNode;
	}

	public Trip getTrip() {
		return trip;
	}

	public Location getDepotLoc() {
		return depotLoc;
	}

	public boolean isSource() {
		return isSource;
	}

	@Override
	public String toString() {
		return "CN_Node [isTripNode=" + isTripNode + ", trip=" + trip + ", depotLoc=" + depotLoc + ", isSource="
				+ isSource + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(depotLoc, isSource, isTripNode, trip);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CN_Node other = (CN_Node) obj;
		return Objects.equals(depotLoc, other.depotLoc) && isSource == other.isSource && isTripNode == other.isTripNode
				&& Objects.equals(trip, other.trip);
	}
	
	
	
}
