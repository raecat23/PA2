package cs131.pa2.CarsTunnels;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;
import cs131.pa2.Abstract.Log.Log;
import javafx.util.Pair;

public class PreemptivePriorityScheduler extends Tunnel{
	Map<Tunnel, Lock> progressingLocks = new HashMap();
	Map<Tunnel, Lock> nonProgressingLocks = new HashMap();
	Map<Tunnel, Condition> progressingConditions = new HashMap();
	Map<Tunnel, Condition> nonProgressingConditions = new HashMap();
	//public HashMap<Vehicle, Tunnel> VehicleAndTunnel = new HashMap();
	//public PriorityScheduler prioSched;
	private final Lock lock = new ReentrantLock(); 
	private final Condition prioCond = lock.newCondition();
public HashMap<Vehicle, Tunnel> VehicleAndTunnel = new HashMap();
	
	public ArrayList<Vehicle> prioWait = new ArrayList();
	
	//public HashMap<Tunnel, Lock> tunnelList = new HashMap<Tunnel, Lock>();
	int maxWaitingPriority = 0;
	public PreemptivePriorityScheduler(String name, Collection<Tunnel> tunnels, Log log) {
		super(name);
		for(Tunnel t : tunnels) {
			////("IS THIS THE GODDAMN PROBLEM");
			Lock prog = new ReentrantLock();
			//("Progressing Lock: " + prog.toString());
			progressingLocks.put(t, prog);
			
			Lock nonprog = new ReentrantLock();
			//("NONProgressing Lock: " + nonprog.toString());
			nonProgressingLocks.put(t, nonprog);
			progressingConditions.put(t, prog.newCondition());
			nonProgressingConditions.put(t, nonprog.newCondition());
		}
		////("Everything created");
		//prioSched = new PriorityScheduler(name, tunnels, log);
		//use this to make the locks and map them to each lock
	}
	public boolean gottaWait(Vehicle vehicle) {
		return (vehicle.getPriority() < maxWaitingPriority);
	}
	
	public boolean onWaitingList(Vehicle vehicle) {
		boolean answer = false;
		try {
			answer = prioWait.contains(vehicle);
		} finally {
			return answer;
		}
	}
	
	

	@Override
	public boolean tryToEnterInner(Vehicle vehicle) {
		lock.lock();
		boolean entered = false;
		try {
		vehicle.addPPS(this);	
		boolean ambulance = false;
		////("TRYING TO ENTER WITH PPS");
		if(vehicle instanceof Ambulance ) {
			//("Theres an ambulance");
			ambulance = true;
		}
		while(ambulance) {//im forgetting locks here
			for(Tunnel t : progressingLocks.keySet()) {
				////("checking tunnels");
				for(Vehicle v : VehicleAndTunnel.keySet()) {
		//			//("Checking vehicles");
					if(VehicleAndTunnel.get(v).equals(t) && !(v instanceof Ambulance)) {
						progressingConditions.get(t).signalAll();
						//("Entering tunnel");
		
						VehicleAndTunnel.put(vehicle, t);
						entered = true;
					
					}
				}
			}
			//i thinnk the ambulance wait should go here like if all tunnels are full of ambulances
		}
		if(!ambulance) {
			//lock.lock();
			
			while(!entered) {
				//("entering while loop");
				//If your cool enough to go right in
				if (!gottaWait(vehicle)&&!entered&&!onWaitingList(vehicle)) {
					//("You dont need to wait");
					Iterator it = progressingLocks.entrySet().iterator();
					while (it.hasNext()) {
						//("Iterating through tunnels");
						Map.Entry<Tunnel, Lock>pair = (Map.Entry<Tunnel, Lock>)it.next();
						if(pair.getKey().tryToEnter(vehicle)) {
							//("Entering tunnel");
							VehicleAndTunnel.put(vehicle, pair.getKey());
							entered = true;
							break;
						}		
					}
					//If you didn't enter, go into 
					if(!entered) {
						prioWait.add(vehicle);
					}
				} else if (onWaitingList(vehicle)&&!entered&&!gottaWait(vehicle)){
					Iterator it = progressingLocks.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>)it.next();
						if(pair.getKey().tryToEnter(vehicle)) {
							prioWait.remove(vehicle);										
							int maxPrio = 0;
							for (Vehicle v: prioWait) {
								if (v.getPriority() > maxPrio) {
									maxPrio = v.getPriority();
								}
							}
							maxWaitingPriority = maxPrio;	
						}					
						VehicleAndTunnel.put(vehicle, pair.getKey());
						entered = true;	
					}
				}
				
				if(!entered) {	
					try {
						prioCond.await();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
				
		
		} 
		}finally {
		lock.unlock();
		return entered;
		}
	}

	@Override
	public void exitTunnelInner(Vehicle vehicle) {
		lock.lock();
		if(vehicle instanceof Ambulance) {
			//("Ambulance exiting");
			Tunnel temp = VehicleAndTunnel.get(vehicle);
			//("TEMP IN EXITING: " +temp.toString());
			temp.exitTunnel(vehicle);
			
			//nonProgressingLocks.get(temp).lock();
			nonProgressingConditions.get(temp).signalAll();
		//	nonProgressingLocks.get(temp).unlock();
		}
		else {
		boolean removedSomething = false;

		try {
			Iterator iter = VehicleAndTunnel.entrySet().iterator();
			while(iter.hasNext()) {
				Map.Entry<Vehicle, Tunnel> bingo = (Map.Entry<Vehicle, Tunnel>)iter.next();
				//(bingo.toString());
				if(bingo.getKey().equals(vehicle)) {
					try {
						Iterator bitter = progressingLocks.entrySet().iterator();
						while (bitter.hasNext()) {
							Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>)bitter.next();
							if(pair.getKey().equals(bingo.getValue())) {
								bingo.getValue().exitTunnel(bingo.getKey());
								removedSomething = true;
								//("FRIENDSHIP ENDED WITH" + bingo.toString() );
							}
						}
					} finally {
						iter.remove();						
					}
				}
			}
		} finally {
			
			if (removedSomething) {prioCond.signalAll();}
			//("EXITING");
			lock.unlock();
		}
		}
	}
	
	public Lock getNonProgressingLock(Vehicle vehicle) {
		Tunnel temp = VehicleAndTunnel.get(vehicle);
		//(temp.toString());
		return nonProgressingLocks.get(temp);
	}
	public Lock getProgressingLock(Vehicle vehicle) {
		Tunnel temp = VehicleAndTunnel.get(vehicle);
		//("TEMP : " + temp.toString());
		return progressingLocks.get(temp);
	}
	public Condition getNonProgressingCon(Vehicle vehicle) {
		Tunnel temp = VehicleAndTunnel.get(vehicle);
		return nonProgressingConditions.get(temp);
	}
	public Condition getProgressingCon(Vehicle vehicle) {
		Tunnel temp = VehicleAndTunnel.get(vehicle);
		return progressingConditions.get(temp);
	}
	
}

