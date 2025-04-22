package ddd;
import problem.Trip;

/**
 * Class for modeling a timed trip
 */
public class TimedTrip {
	private Trip t;
	private int depTime;
	
	public TimedTrip(Trip t, int depTime) {
		super();
		this.t = t;
		this.depTime = depTime;
	}
	public Trip getT() {
		return t;
	}
	public int getDepTime() {
		return depTime;
	}
	@Override
	public String toString() {
		return "TimedTrip [t=" + t + ", depTime=" + depTime + "]";
	}
	
	
}
