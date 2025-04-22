package digraph;


/**
 * Class that models arcs in the directed arcs. Stores both the end-points, as
 * well as the data associated with the arc.
 * 
 * @author Paul Bouman
 *
 * @param <V> the type of data associated with nodes in the graph
 * @param <A> the type of data associated with arcs in the graph
 */
public class DirectedGraphArc<V, A>
{
	private final V from;
	private final V to;
	private final A data;

	// The cost and dual arrays store general properties and specific properties.
	private double[] costs;
	private double[] duals;

	/**
	 * Construct an arc of the graph
	 * 
	 * @param from the origin of this arc
	 * @param to   the destination of this arc
	 * @param data the data associated with this arc
	 */
	public DirectedGraphArc(V from, V to, A data, double cost)
	{
		this.from = from;
		this.to = to;
		this.data = data;

		this.costs = new double[2];
		costs[0] = cost;
		this.duals = new double[2];
	}

	public DirectedGraphArc(V from, V to, A data, double[] costs, double[] duals)
	{
		this.from = from;
		this.to = to;
		this.data = data;

		this.costs = costs;
		this.duals = duals;
	}

	/**
	 * Used to retrieve the origin of this arc
	 * 
	 * @return the origin of this arc
	 */
	public V getFrom()
	{
		return from;
	}

	/**
	 * Used to retrieve the destination of this arc
	 * 
	 * @return the destination of this arc
	 */
	public V getTo()
	{
		return to;
	}

	/**
	 * Used to retrieve the data associated with this arc
	 * 
	 * @return the data associated with this arc
	 */
	public A getData()
	{
		return data;
	}

	/**
	 * Used to retrieve the weight of this arc.
	 * 
	 * @return the weight of this arc.
	 */
	public double getWeight()
	{
		return costs[0] ;//+ costs[1] - duals[0] - duals[1];
	}

	public double getCost()
	{
		return costs[0];// + costs[1];
	}
	
	public double[] getCosts()
	{
		return costs;
	}

	public void setCost(double cost, int index)
	{
		costs[index] = cost;
	}

	public void addCost(double cost, int index)
	{
		costs[index] += cost;
	}

	public void setDual(double dual, int index)
	{
		duals[index] = dual;
	}

	public void addDual(double dual, int index)
	{
		duals[index] += dual;
	}

	public double[] getDuals()
	{
		return duals;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 17;
		result = prime * result + ((data == null) ? 0 : data.hashCode());
		result = prime * result + ((from == null) ? 0 : from.hashCode());
		result = prime * result + ((to == null) ? 0 : to.hashCode());
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
		DirectedGraphArc<?, ?> other = (DirectedGraphArc<?, ?>) obj;
		if (data == null)
		{
			if (other.data != null)
				return false;
		}
		else if (!data.equals(other.data))
			return false;
		if (from == null)
		{
			if (other.from != null)
				return false;
		}
		else if (!from.equals(other.from))
			return false;
		if (to == null)
		{
			if (other.to != null)
				return false;
		}
		else if (!to.equals(other.to))
			return false;
		return true;
	}

	@Override
	public String toString()
	{
		return "Arc [from=" + from + ", to=" + to + ", data=" + data + "]";
	}
}