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
	public PriorityScheduler prioSched;
	private final Lock lock = new ReentrantLock(); 
	private final Condition prioCond = lock.newCondition();
public HashMap<Vehicle, Tunnel> VehicleAndTunnel = new HashMap();
	
	public ArrayList<Vehicle> prioWait = new ArrayList();
	
	public HashMap<Tunnel, Lock> tunnelList = new HashMap<Tunnel, Lock>();
	int maxWaitingPriority = 0;
	public PreemptivePriorityScheduler(String name, Collection<Tunnel> tunnels, Log log) {
		super(name);
		for(Tunnel t : tunnels) {
			System.out.println("IS THIS THE GODDAMN PROBLEM");
			Lock nonprog = new ReentrantLock();
			progressingLocks.put(t, nonprog);
			Lock prog = new ReentrantLock();
			nonProgressingLocks.put(t, prog);
			progressingConditions.put(t, prog.newCondition());
			nonProgressingConditions.put(t, nonprog.newCondition());
		}
		System.out.println("Everything created");
		prioSched = new PriorityScheduler(name, tunnels, log);
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
		vehicle.addPPS(this);
		boolean ambulance = false;
		//System.out.println("TRYING TO ENTER WITH PPS");
		if(vehicle instanceof Ambulance ) {
			System.out.println("Theres an ambulance");
			ambulance = true;
		}
		while(ambulance) {//im forgetting locks here
			for(Tunnel t : prioSched.tunnelList.keySet()) {
				//System.out.println("checking tunnels");
				for(Vehicle v : prioSched.VehicleAndTunnel.keySet()) {
		//			System.out.println("Checking vehicles");
					if(prioSched.VehicleAndTunnel.get(v).equals(t) && !(v instanceof Ambulance)) {
						nonProgressingConditions.get(t).signalAll();
			//			System.out.println("Ambulance entered");
						//ambulance = false;
						return true;
					}
				}
			}
			//i thinnk the ambulance wait should go here like if all tunnels are full of ambulances
		}
		if(!ambulance) {
			boolean entered = false;
			while(!entered) {
				System.out.println("entering while loop");
				//If your cool enough to go right in
				if (!gottaWait(vehicle)&&!entered&&!onWaitingList(vehicle)) {
					System.out.println("You dont need to wait");
					Iterator it = tunnelList.entrySet().iterator();
					while (it.hasNext()) {
						System.out.println("Iterating through tunnels");
						Pair<Tunnel, Lock> pair = (Pair<Tunnel, Lock>)it.next();
						if(pair.getKey().tryToEnter(vehicle)) {
							System.out.println("Entering tunnel");
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
					Iterator it = tunnelList.entrySet().iterator();
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
		
			//System.out.println(vehicle.toString() + " Trying to enter in PPS");
			//return prioSched.tryToEnterInner(vehicle);
		
		return false;
		//priority scheduler
		/*
		 * the locks go in here and condition variables and they are put in
		 * keep track of two hashmaps in preemptive priorty scheduler where the key is the tunnel and the value is the lock or condition variable 
		 * vehicles should only have access to their locks and also their condition variables
		 * when theres an ambulance you signal all and it interrupts where it is and then theres an ambulance
		 * if its an ambulance everything is immediately stopped
		 * else the priority scheduler should happen except if something of higher priority comes it should stop whatever is in there 
		 * to go in (i think?)
		 */
	}

	@Override
	public void exitTunnelInner(Vehicle vehicle) {
		if(vehicle instanceof Ambulance) {
			System.out.println("Ambulance exiting");
			Tunnel temp = prioSched.VehicleAndTunnel.get(vehicle);
			nonProgressingConditions.get(temp).notifyAll();
		}
		boolean removedSomething = false;
		lock.lock();
		try {
			Iterator iter = VehicleAndTunnel.entrySet().iterator();
			while(iter.hasNext()) {
				Map.Entry<Vehicle, Tunnel> bingo = (Map.Entry<Vehicle, Tunnel>)iter.next();
				System.out.println(bingo.toString());
				if(bingo.getKey().equals(vehicle)) {
					try {
						Iterator bitter = tunnelList.entrySet().iterator();
						while (bitter.hasNext()) {
							Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>)bitter.next();
							if(pair.getKey().equals(bingo.getValue())) {
								bingo.getValue().exitTunnel(bingo.getKey());
								removedSomething = true;
								System.out.println("FRIENDSHIP ENDED WITH" + bingo.toString() );
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
	
	public Lock getNonProgressingLock(Vehicle vehicle) {
		Tunnel temp = prioSched.VehicleAndTunnel.get(vehicle);
		System.out.println(temp.toString());
		return nonProgressingLocks.get(temp);
	}
	public Lock getProgressingLock(Vehicle vehicle) {
		Tunnel temp = prioSched.VehicleAndTunnel.get(vehicle);
		return progressingLocks.get(temp);
	}
	public Condition getNonProgressingCon(Vehicle vehicle) {
		Tunnel temp = prioSched.VehicleAndTunnel.get(vehicle);
		return nonProgressingConditions.get(temp);
	}
	public Condition getProgressingCon(Vehicle vehicle) {
		Tunnel temp = prioSched.VehicleAndTunnel.get(vehicle);
		return progressingConditions.get(temp);
	}
	
}

