package ddd;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import problem.Instance;
import problem.Trip;

/*
 * Class that implements the model that minimizes the total deviation of a given duty. 
 */
public class ConversionModel
{
	private IloCplex cplex;
	private Instance in;
	private List<Trip> trips;
	private boolean minimizeDeviation;
	
	// Decision variables
	private Map<Trip,IloNumVar> pi;
	private Map<Trip,IloNumVar> deltaplus;
	private Map<Trip,IloNumVar> deltaminus;

	public ConversionModel(Instance in, Duty route, boolean minDeviation) throws IloException
	{
		this.cplex = new IloCplex();
		this.in = in;
		this.trips = route.getTrips();
		this.minimizeDeviation = minDeviation;
		//cplex.setParam(IloCplex.DoubleParam.TiLim, timeLimit);
		cplex.setOut(null);

		this.pi = new HashMap<>();
		this.deltaplus = new HashMap<>();
		this.deltaminus = new HashMap<>();

		addVariables();
		//System.out.println("Variables added!");
		addTimeWindowConstraints();
		addTravelTimeConstraints();
		//System.out.println("Constraints added!");
		addObjective();
		//System.out.println("Objective function added!");

		//cplex.exportModel("coversion.lp");
	}

	public void addVariables() throws IloException
	{
		for (Trip t: trips)
		{
			//TODO: soft code the end horizon
			pi.put(t,cplex.numVar(in.getStartHorizon(), in.getEndHorizon() + 1000,"pi"+t.getID()));
			deltaplus.put(t,cplex.intVar(0, in.getMaxDeviation(),"dp"+t.getID()));
			deltaminus.put(t,cplex.intVar(0, in.getMaxDeviation(),"dp"+t.getID()));
		}
	}

	public void addTimeWindowConstraints() throws IloException
	{
		for(Trip t: trips) {
			IloNumExpr rhs = cplex.constant(t.getStartTime());
			rhs = cplex.sum(rhs,deltaplus.get(t));
			rhs = cplex.diff(rhs, deltaminus.get(t));
			cplex.addEq(pi.get(t), rhs);
		}
	}

	public void addTravelTimeConstraints() throws IloException
	{
		for (int i = 0; i < trips.size() - 1; i++)
		{
			Trip first = trips.get(i);
			Trip second = trips.get(i+1);
			int tripTime = first.getTripTime();
			int deadhead = first.getEndLocation().getTimeTo(second.getStartLocation());
			if(deadhead==10000) {
				System.out.println("NO dh from "+first.getEndLocation().getIndex()+" to "+second.getStartLocation().getIndex());
			}
			IloNumExpr rhs =pi.get(first);
			rhs = cplex.sum(rhs,tripTime);
			rhs = cplex.sum(rhs, deadhead);
			cplex.addGe(pi.get(second),rhs);
		}
	}

	public void addObjective() throws IloException
	{
		IloNumExpr obj = cplex.constant(0);
		if(minimizeDeviation) {
			for (Trip t: trips)
			{
				IloNumExpr term = cplex.sum(deltaplus.get(t), deltaminus.get(t));
				obj = cplex.sum(obj, term);
			}
		} else {
			for (int i = 0; i < trips.size() - 1; i++)
			{
				Trip first = trips.get(i);
				Trip second = trips.get(i+1);
				int tripTime = first.getTripTime();
				int deadhead = first.getEndLocation().getTimeTo(second.getStartLocation());
				if(deadhead==10000) {
					System.out.println("NO dh from "+first.getEndLocation().getIndex()+" to "+second.getStartLocation().getIndex());
				}
				IloNumExpr rhs =pi.get(first);
				rhs = cplex.sum(rhs,tripTime);
				rhs = cplex.sum(rhs, deadhead);
				IloNumExpr waiting = cplex.diff(pi.get(second),rhs);
				obj = cplex.sum(obj,waiting);
			}
		}
		

		cplex.addMinimize(obj);
	}

	public boolean isFeasible() throws IloException
	{
		return cplex.isPrimalFeasible();
	}

	public void solve() throws IloException 
	{
		cplex.solve();
		//System.out.println("obj conversion: "+cplex.getObjValue());
	}

	public void cleanup() throws IloException 
	{
		cplex.clearModel();
		cplex.end();
	}

	public double getObjectiveValue() throws IloException
	{
		return cplex.getObjValue();
	}

	public int getNumTripShifts() throws IloException
	{
		int numShifts = 0;

		for (Trip t: trips)
		{
			if(cplex.getValue(deltaplus.get(t)) + cplex.getValue(deltaminus.get(t)) > 0.5)
			{
				numShifts++;
			}
		}
		
		return numShifts;
	}

	public List<TimedTrip> getDepartureTimeSolution() throws IloException
	{
		List<TimedTrip> timedTrips = new ArrayList<>();

		for (Trip t: trips)
		{
			//System.out.println(t+ " has dep time: "+cplex.getValue(pi.get(t)));
			int depTime = (int) Math.round(cplex.getValue(pi.get(t)));
			timedTrips.add(new TimedTrip(t,depTime));
		}

		return timedTrips;
	}
}
