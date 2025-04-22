package bnp;

import java.util.Comparator;

/**
 * Class used to compare CN_Node objects based on their start time
 * @author 20215535
 *
 */
public class CN_NodeComparator implements Comparator<CN_Node>
{
	@Override
	public int compare(CN_Node o1, CN_Node o2)
	{
		int startTime1 = 0;
		int startTime2 = 0;
		if(o1.isTripNode()) {
			startTime1 = o1.getTrip().getStartTime();
		} else if(o1.isSource()) {
			startTime1 = -Integer.MAX_VALUE;
		} else {
			startTime1 = Integer.MAX_VALUE;
		}
		if(o2.isTripNode()) {
			startTime1 = o2.getTrip().getStartTime();
		} else if(o2.isSource()) {
			startTime2 = -Integer.MAX_VALUE;
		} else {
			startTime2 = Integer.MAX_VALUE;
		}
		
		return Integer.compare(startTime1,startTime2);
	}
}
