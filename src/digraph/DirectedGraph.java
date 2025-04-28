package digraph;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Simple class that can be used to model directed graphs, where arbitrary types
 * of data are associated with the nodes and arcs of the graph. Note that this
 * implementation of an arc allows multiple copies of the same arc. As such, it
 * can not be assumed that graphs stored by instances of this class are simple;
 * they can be multigraphs.
 * 
 * It is assumed that the data type associated with the nodes has a consistent
 * implementation of hashCode() and equals().
 * 
 * @author Paul Bouman
 *
 * @param <V> the type of data associated with nodes in this graph
 * @param <A> the type of data associated with arcs in this graph
 */

public class DirectedGraph<V extends DirectedGraphNodeIndex, A>
{
	protected final List<V> nodes;
	protected final List<DirectedGraphArc<V, A>> arcs;
	protected final Map<V, List<DirectedGraphArc<V, A>>> outArcs;
	protected final Map<V, List<DirectedGraphArc<V, A>>> inArcs;

	/**
	 * Creates an empty graph with no nodes or arcs.
	 */
	public DirectedGraph()
	{
		this.nodes = new ArrayList<>();
		this.arcs = new ArrayList<>();
		this.outArcs = new LinkedHashMap<>();
		this.inArcs = new LinkedHashMap<>();
	}

	/**
	 * Add a new node to this graph
	 * 
	 * @param node the data associated with the node that is added
	 * @throws IllegalArgumentException if the node is already in the graph or is
	 *                                  null
	 */
	public void addNode(V node) throws IllegalArgumentException
	{
		if (node == null)
		{
			throw new IllegalArgumentException("Unable to add null to the graph");
		}
		else if (inArcs.containsKey(node))
		{
			throw new IllegalArgumentException("Unable to add the same node twice to the same graph");
		}
		else
		{
			nodes.add(node);
			inArcs.put(node, new ArrayList<>());
			outArcs.put(node, new ArrayList<>());
		}
	}

	/**
	 * Adds an arc to this graph.
	 * 
	 * @param from    the origin node of the arc to be added
	 * @param to      the destination of the arc to be added
	 * @param arcData the data associated with the arc
	 * @param weight  the weight of the arc
	 * @throws IllegalArgumentException if one of the end points is not in the graph
	 */
	public DirectedGraphArc<V, A> addArc(V from, V to, A arcData, double cost) throws IllegalArgumentException
	{
		if (!inArcs.containsKey(from) || !outArcs.containsKey(to))
		{
			throw new IllegalArgumentException("Unable to add arcs between nodes not in the graph");
		}
		DirectedGraphArc<V, A> a = new DirectedGraphArc<>(from, to, arcData, cost);
		outArcs.get(from).add(a);
		inArcs.get(to).add(a);
		arcs.add(a);
		return a;
	}

	public void addArc(V from, V to, A arcData, double[] costs, double[] duals) throws IllegalArgumentException
	{
		if (!inArcs.containsKey(from) || !outArcs.containsKey(to))
		{
			throw new IllegalArgumentException("Unable to add arcs between nodes not in the graph");
		}
		DirectedGraphArc<V, A> a = new DirectedGraphArc<>(from, to, arcData, costs, duals);
		outArcs.get(from).add(a);
		inArcs.get(to).add(a);
		arcs.add(a);
	}

	public void removeNode(V node) throws IllegalArgumentException
	{
		if (node == null)
		{
			throw new IllegalArgumentException("Unable to remove null from the graph");
		}
		else if (!nodes.contains(node))
		{
			throw new IllegalArgumentException("Unable to remove node that is not in the graph");
		}
		else
		{
			nodes.remove(node);
			for (DirectedGraphArc<V, A> arc : inArcs.get(node))
			{
				arcs.remove(arc);
				outArcs.get(arc.getFrom()).remove(arc);
			}
			inArcs.remove(node);
			for (DirectedGraphArc<V, A> arc : outArcs.get(node))
			{
				arcs.remove(arc);
				inArcs.get(arc.getTo()).remove(arc);
			}
			outArcs.remove(node);
		}
	}

	public void removeArc(DirectedGraphArc<V, A> arc) throws IllegalArgumentException
	{
		if (arc == null)
		{
			throw new IllegalArgumentException("Unable to remove null from the graph");
		}
		else if (!arcs.contains(arc))
		{
			throw new IllegalArgumentException("Unable to remove arc that is not in the graph");
		}
		else
		{
			arcs.remove(arc);
			outArcs.get(arc.getFrom()).remove(arc);
			inArcs.get(arc.getTo()).remove(arc);
		}
	}

	/**
	 * Gives a list of all nodes currently in the graph
	 * 
	 * @return the nodes in the graph
	 */
	public List<V> getNodes()
	{
		return Collections.unmodifiableList(nodes);
	}

	/**
	 * Gives a list of all arcs currently in the graph
	 * 
	 * @return the arcs in the graph
	 */
	public List<DirectedGraphArc<V, A>> getArcs()
	{
		return Collections.unmodifiableList(arcs);
	}

	
	public List<DirectedGraph<V,A>> getConnectedComponents() {
		List<DirectedGraph<V,A>> components = new ArrayList<>();
		List<V> notInComponent = new ArrayList<>(nodes);
		while(!notInComponent.isEmpty()) {
			//pick node and find all connected nodes
			List<V> component = new ArrayList<>();
			List<V> toScan = new ArrayList<>();
			toScan.add(notInComponent.get(0));
			component.add(notInComponent.get(0));
			
			while(!toScan.isEmpty()) {
				V scanning = toScan.get(0);
				for(V u: getAdjacentNodes(scanning)) {
					if(!component.contains(u)) {
						//found a new node
						component.add(u);
						toScan.add(u);
					}
				}
				toScan.remove(scanning);
			}
			
			components.add(getInducedGraph(component));
			notInComponent.removeAll(component);
		}
		
		return components;
	}
	
	private DirectedGraph<V, A> getInducedGraph(List<V> nodeSet) {
		DirectedGraph<V, A> g = new DirectedGraph<V,A>();
		for(V v: nodeSet) {
			g.addNode(v);
		}
		for(DirectedGraphArc<V,A> a: arcs) {
			if(nodeSet.contains(a.getFrom())&&nodeSet.contains(a.getTo())) {
				g.addArc(a.getFrom(), a.getTo(), a.getData(), a.getCost());
			}
		}
		return g;
	}
	
	
	/**
	 * Method for finding a cycle that is generated if an activity is added to a
	 * tree
	 */
	public List<DirectedGraphArc<V, A>> getCycle(DirectedGraphArc<V, A> a)
	{
		List<DirectedGraphArc<V, A>> cycle = new ArrayList<>();
		cycle.add(a);
		List<V> evInPath = new ArrayList<>();
		evInPath.add(a.getTo());
		// add the path from e2 to e1 using the tree
		List<DirectedGraphArc<V, A>> path = getPath(a.getFrom(), a.getTo(), new ArrayList<>());
		for (DirectedGraphArc<V, A> arc : path)
		{
			cycle.add(arc);
		}
		return cycle;
	}
	
	/**
	 * Return the path between two nodes. Should only be used if graph is a tree
	 */
	public List<DirectedGraphArc<V, A>> getPath(V to, V from,
			List<DirectedGraphArc<V, A>> curPath)
	{
		// System.out.println("\n Finding path to "+to+" from "+from);
		// System.out.println("Curpath: "+curPath);
		Set<DirectedGraphArc<V, A>> candidates = new HashSet<>(); // all arcs that can be added to the path
																				// in a given iteration
		candidates.addAll(getInArcs(from));
		candidates.addAll(getOutArcs(from));
		candidates.removeAll(curPath); // cannot go back

		for (DirectedGraphArc<V, A> a : candidates)
		{
			V newNode = a.getFrom();
			if (a.getFrom().equals(from))
			{
				newNode = a.getTo();
			}
			List<DirectedGraphArc<V, A>> newPath = new ArrayList<>(curPath);
			newPath.add(a);
			if (newNode.equals(to))
			{
				return newPath;
			}
			else
			{
				// continue recursion
				List<DirectedGraphArc<V, A>> completePath = getPath(to, newNode, newPath);
				if (completePath != null)
				{
					return completePath;
				}
			}
		}

		return null;
	}
	
	/**
	 * Gives all the arcs that leave a particular node in the graph. Note that this
	 * list may be empty if no arcs leave this node.
	 * 
	 * @param node the node for which we want the leaving arcs
	 * @return a list of arcs leaving the node
	 * @throws IllegalArgumentException if the node is not in the graph
	 */
	public List<DirectedGraphArc<V, A>> getOutArcs(V node) throws IllegalArgumentException
	{
		if (!outArcs.containsKey(node))
		{
			throw new IllegalArgumentException("Unable to provide out-arcs for a node that is not in the graph");
		}
		return Collections.unmodifiableList(outArcs.get(node));
	}

	/**
	 * Gives all the arcs that enter a particular node in the graph. Note that this
	 * list may be empty if no arcs enter this node.
	 * 
	 * @param node the node for which we want the entering arcs
	 * @return a list of arcs entering the node
	 * @throws IllegalArgumentException if the node is not in the graph
	 */
	public List<DirectedGraphArc<V, A>> getInArcs(V node) throws IllegalArgumentException
	{
		if (!inArcs.containsKey(node))
		{
			throw new IllegalArgumentException("Unable to provide in-arcs for a node that is not in the graph");
		}
		return Collections.unmodifiableList(inArcs.get(node));
	}

	/**
	 * The total number of nodes in this graph
	 * 
	 * @return the number of nodes in the graph
	 */
	public int getNumberOfNodes()
	{
		return nodes.size();
	}

	/**
	 * The total number of arcs in this graph
	 * 
	 * @return the number of arcs in the graph
	 */
	public int getNumberOfArcs()
	{
		return arcs.size();
	}

	/**
	 * Gives the in-degree of a node in the graph.
	 * 
	 * @param node the node for which we want the in-degree
	 * @return the in-degree of the node
	 * @throws IllegalArgumentException if the node is not in the graph
	 */
	public int getInDegree(V node) throws IllegalArgumentException
	{
		return getInArcs(node).size();
	}
	
	/**
	 * Method that gives all adjacent nodes, ignoring direction
	 * @param node
	 * @return
	 */
	public List<V> getAdjacentNodes(V node) {
		List<V> adjacent = new ArrayList<>();
		for(DirectedGraphArc<V, A> in: getInArcs(node)) {
			adjacent.add(in.getFrom());
		}
		for(DirectedGraphArc<V, A> out: getOutArcs(node)) {
			adjacent.add(out.getTo());
		}
		return adjacent;
	}

	/**
	 * Gives the out-degree of a node in the graph
	 * 
	 * @param node the node for which we want the out-degree
	 * @return the out-degree of the node
	 * @throws IllegalArgumentException if the node is not in the graph
	 */
	public int getOutDegree(V node) throws IllegalArgumentException
	{
		return getOutArcs(node).size();
	}

	/**
	 * Iterate over all nodes to set the index. This is an auxiliary function used
	 * in the shortest path algorithms
	 */
	public void setNodeIndices()
	{
		for (int i = 0; i < nodes.size(); i++)
		{
			nodes.get(i).setIndex(i);
		}
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 17;
		result = prime * result + ((arcs == null) ? 0 : arcs.hashCode());
		result = prime * result + ((nodes == null) ? 0 : nodes.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DirectedGraph<?, ?> other = (DirectedGraph<?, ?>) obj;
		if (arcs == null)
		{
			if (other.arcs != null)
				return false;
		}
		else if (!arcs.equals(other.arcs))
			return false;
		if (nodes == null)
		{
			if (other.nodes != null)
				return false;
		}
		else if (!nodes.equals(other.nodes))
			return false;
		return true;
	}

	@Override
	public String toString()
	{
		return "DirectedGraph [nodes=" + nodes + ", arcs=" + arcs + "]";
	}
}
