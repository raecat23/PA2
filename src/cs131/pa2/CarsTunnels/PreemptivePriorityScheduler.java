package cs131.pa2.CarsTunnels;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;
import cs131.pa2.Abstract.Log.Log;

public class PreemptivePriorityScheduler extends Tunnel{
	Map<Tunnel, Lock> progressingLocks = new HashMap<Tunnel, Lock>();
	Map<Tunnel, Lock> nonProgressingLocks = new HashMap<Tunnel, Lock>();
	Map<Tunnel, Condition> progressingConditions = new HashMap<Tunnel, Condition>();
	Map<Tunnel, Condition> nonProgressingConditions = new HashMap<Tunnel, Condition>();
	//public HashMap<Vehicle, Tunnel> VehicleAndTunnel = new HashMap();
	//public PriorityScheduler prioSched;
	private final Lock lock = new ReentrantLock(); 
	private final Condition prioCond = lock.newCondition();
public HashMap<Vehicle, Tunnel> VehicleAndTunnel = new HashMap<Vehicle, Tunnel>();
	
	public ArrayList<Vehicle> prioWait = new ArrayList<Vehicle>();
	
	//public HashMap<Tunnel, Lock> tunnelList = new HashMap<Tunnel, Lock>();
	int maxWaitingPriority = 0;
	public PreemptivePriorityScheduler(String name, Collection<Tunnel> tunnels, Log log) {
		super(name, log);
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
	
	@SuppressWarnings("finally")
	public boolean onWaitingList(Vehicle vehicle) {
		boolean answer = prioWait.contains(vehicle);
		return answer;
	}
	
	

	@SuppressWarnings("finally")
	@Override
	public boolean tryToEnterInner(Vehicle vehicle) {
		lock.lock();
		boolean entered = false;
		try {
		vehicle.addPPS(this);	
		boolean ambulance = false;
		////("TRYING TO ENTER WITH PPS");
		if(vehicle instanceof Ambulance ) {
			System.out.println("Theres an ambulance");
			ambulance = true;
		}
		while(!entered) {
			//If your cool enough to go right in
			if (!gottaWait(vehicle)&&!entered&&!onWaitingList(vehicle)) {
				Iterator<Entry<Tunnel, Lock>> it = progressingLocks.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>)it.next();
					if(pair.getKey().tryToEnter(vehicle) && !entered) {
						VehicleAndTunnel.put(vehicle, pair.getKey());
						entered = true;
						if(ambulance) {
							System.err.println("Signaling" + vehicle.toString());
							System.out.println(progressingConditions.get(pair.getKey()).toString());
							vehicle.p.getProgressingLock(vehicle).lock();
							try {
							vehicle.p.getProgressingCon(vehicle).signalAll();
							}finally {
							vehicle.p.getProgressingLock(vehicle).unlock();
							}
						}
						break;
					}		
				}
				//If you didn't enter, go into 
				if(!entered) {
					if(vehicle.getPriority() > maxWaitingPriority) {//Set the maxWaitingPriority
						maxWaitingPriority = vehicle.getPriority();
					}
					prioWait.add(vehicle);
				}
			} else if (onWaitingList(vehicle)&&!entered&&!gottaWait(vehicle)){
				Iterator<Entry<Tunnel, Lock>> it = progressingLocks.entrySet().iterator();
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
						VehicleAndTunnel.put(vehicle, pair.getKey());
						entered = true;	
						if(ambulance) {
							System.err.println("Signaling from wait list");
							vehicle.p.getProgressingLock(vehicle).lock();
							try {
							vehicle.p.getProgressingCon(vehicle).signalAll();
							}finally {
							vehicle.p.getProgressingLock(vehicle).unlock();
							}
						}
						break;
					}					
				
					
				}
			}
			
			if(!entered) {	
				prioCond.await();
			}
		}
			
		
	} finally {
		lock.unlock();
		return entered;
	}
		
		
	}
	//	

	@Override
	public void exitTunnelInner(Vehicle vehicle) {
		boolean removedSomething = false;
		lock.lock();
		try {
			Iterator<Entry<Vehicle, Tunnel>> iter = VehicleAndTunnel.entrySet().iterator();
			while(iter.hasNext()) {
				Map.Entry<Vehicle, Tunnel> bingo = (Map.Entry<Vehicle, Tunnel>)iter.next();
				//System.out.println(bingo.toString());
				if(bingo.getKey().equals(vehicle)) {
					try {
						Iterator<Entry<Tunnel, Lock>> bitter = progressingLocks.entrySet().iterator();
						while (bitter.hasNext()) {
							Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>)bitter.next();
							if(pair.getKey().equals(bingo.getValue()) && !removedSomething) {
								bingo.getValue().exitTunnel(bingo.getKey());
								removedSomething = true;
								if(vehicle instanceof Ambulance) {
									boolean ambulanceWaiting = false;
									for(Vehicle v : prioWait) {
										if (v instanceof Ambulance) {
											ambulanceWaiting = true;
										}
									}
									//if(!ambulanceWaiting) {
									nonProgressingLocks.get(pair.getKey()).lock();
									try {
									nonProgressingConditions.get(pair.getKey()).signalAll();
									}finally {
									nonProgressingLocks.get(pair.getKey()).unlock();
									}
									//}
								/*	else {
										nonProgressingLocks.get(pair.getKey()).lock();
										nonProgressingConditions.get(pair.getKey()).signalAll();
										nonProgressingLocks.get(pair.getKey()).unlock();
									}*/
									}
								//System.out.println("FRIENDSHIP ENDED WITH" + bingo.toString() );
							}
						}
					} finally {
						iter.remove();						
					}
				}
			}
		} finally {
			if (removedSomething) {prioCond.signalAll();}
			lock.unlock();
		}
		}
	/*
	 * i
	 */
	
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

