package digraph;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

public class Dijkstra<V extends DirectedGraphNodeIndex, A>
{
	private final DirectedGraph<V, A> graph;
	private final V origin;
	private final int numNodes;
	private double[] distance;
	private int[] previous;
	private DirectedGraphArc<V, A>[] previousArc;

	private V destination = null;

	public Dijkstra(DirectedGraph<V, A> graph, V origin)
	{
		this.graph = graph;
		this.origin = origin;

		graph.setNodeIndices();
		this.numNodes = graph.getNumberOfNodes();
	}

	public void setDestination(V destination)
	{
		this.destination = destination;
	}

	@SuppressWarnings("unchecked")
	public void computeDistances()
	{
		// Initialise distance matrix.
		this.distance = new double[numNodes];

		// Initialise successor arrays;
		this.previous = new int[numNodes];
		this.previousArc = (DirectedGraphArc<V, A>[]) Array.newInstance(DirectedGraphArc.class, numNodes);

		// Initialise queue.
		PriorityQueue<Label> queue = new PriorityQueue<>(new LabelDistanceComparator());

		// Place nodes in queue.
		for (V node : graph.getNodes())
		{
			distance[node.getNodeIndex()] = Double.MAX_VALUE;
			if (node.equals(origin))
			{
				distance[node.getNodeIndex()] = 0;
				previous[node.getNodeIndex()] = node.getNodeIndex();
			}
			queue.add(new Label(node, distance[node.getNodeIndex()]));
		}

		// Process queue.
		while (!queue.isEmpty())
		{
			Label label = queue.poll();
			V node = label.getNode();
			for (DirectedGraphArc<V, A> arc : graph.getOutArcs(node))
			{
				double newDistance = distance[node.getNodeIndex()] + arc.getWeight();
				//System.out.println("Weight: "+arc.getWeight());
				if (newDistance < distance[arc.getTo().getNodeIndex()])
				{
					distance[arc.getTo().getNodeIndex()] = newDistance;
					previous[arc.getTo().getNodeIndex()] = node.getNodeIndex();
					previousArc[arc.getTo().getNodeIndex()] = arc;

					// Update label of V in the queue.
					Iterator<Label> iterator = queue.iterator();
					while (iterator.hasNext())
					{
						Label queueLabel = iterator.next();
						if (queueLabel.getNode().equals(arc.getTo()))
						{
							iterator.remove();
							queue.add(new Label(arc.getTo(), distance[arc.getTo().getNodeIndex()]));
							break;
						}
					}
				}
			}

			if (destination != null && destination.equals(node))
			{
				break;
			}
		}
	}

	public boolean containsPath(V to)
	{
		return distance[to.getNodeIndex()] < Double.MAX_VALUE;
	}

	public double getDistance(V to)
	{
		return distance[to.getNodeIndex()];
	}

	public List<DirectedGraphArc<V, A>> getPath(V to)
	{
		V currentNode = to;
		List<DirectedGraphArc<V, A>> path = new ArrayList<>();
		while (!currentNode.equals(origin))
		{
			//System.out.println("Current node: "+currentNode);
			int indexCurrent = currentNode.getNodeIndex();
			path.add(0, previousArc[indexCurrent]);
			V previousNode = graph.getNodes().get(previous[indexCurrent]);
			currentNode = previousNode;
		}
		return path;
	}

	private class Label
	{
		private final V node;
		private final double distance;

		public Label(V node, double distance)
		{
			this.node = node;
			this.distance = distance;
		}

		public V getNode()
		{
			return node;
		}

		public double getDistance()
		{
			return distance;
		}
	}

	public class LabelDistanceComparator implements Comparator<Label>
	{
		@Override
		public int compare(Label o1, Label o2)
		{
			return Double.compare(o1.getDistance(), o2.getDistance());
		}
	}
}
