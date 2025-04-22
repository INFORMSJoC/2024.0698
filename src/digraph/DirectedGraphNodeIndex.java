package digraph;

public abstract class DirectedGraphNodeIndex
{
	private int nodeIndex;
	private double weight;

	public int getNodeIndex()
	{
		return nodeIndex;
	}

	public void setIndex(int nodeIndex)
	{
		this.nodeIndex = nodeIndex;
	}

	public double getWeight()
	{
		return weight;
	}

	public void setWeight(double weight)
	{
		this.weight = weight;
	}

	public void addWeight(double weight)
	{
		this.weight += weight;
	}
}
