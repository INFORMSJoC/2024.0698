package problem;

public class Trip_TD {
	private int id;
	private int routeID;
	private String startTime;
	private String endTime;
	private int startStation;
	private int endStation;
	private String startLoc;
	private String endLoc;
	private String startLocSix;
	private String endLocSix;
	
	public Trip_TD(int id, int routeID, String startTime, String endTime, int startStation, int endStation) {
		super();
		this.id = id;
		this.routeID = routeID;
		this.startTime = startTime;
		this.endTime = endTime;
		this.startStation = startStation;
		this.endStation = endStation;
	}
	
	public String getStartLoc() {
		return startLoc;
	}

	public void setStartLoc(String startLoc) {
		this.startLoc = startLoc;
	}

	public String getEndLoc() {
		return endLoc;
	}

	public void setEndLoc(String endLoc) {
		this.endLoc = endLoc;
	}

	public int getId() {
		return id;
	}

	public String getStartTime() {
		return startTime;
	}

	public String getEndTime() {
		return endTime;
	}

	public int getStartStation() {
		return startStation;
	}

	public int getEndStation() {
		return endStation;
	}
	
	

	public String getStartLocSix() {
		return startLocSix;
	}

	public void setStartLocSix(String startLocSix) {
		this.startLocSix = startLocSix;
	}

	public String getEndLocSix() {
		return endLocSix;
	}

	public void setEndLocSix(String endLocSix) {
		this.endLocSix = endLocSix;
	}

	@Override
	public String toString() {
		return "Trip_TD [id=" + id + ", routeID="+routeID+" startTime=" + startTime + ", endTime=" + endTime + ", startStation="
				+ startStation + ", endStation=" + endStation + ", startLoc=" + startLoc + ", endLoc=" + endLoc + "]";
	}

	
	
}
