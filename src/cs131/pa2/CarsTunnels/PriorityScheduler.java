package cs131.pa2.CarsTunnels;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;
import cs131.pa2.Abstract.Log.Log;
import javafx.util.Pair;

public class PriorityScheduler extends Tunnel{
	private final Lock lock = new ReentrantLock(); 
	private final Condition prioCond = lock.newCondition();
		
	public HashMap<Vehicle, Tunnel> VehicleAndTunnel = new HashMap();
	
	public ArrayList<Vehicle> prioWait = new ArrayList();
	
	public HashMap<Tunnel, Lock> tunnelList = new HashMap<Tunnel, Lock>();
	

	
	int maxWaitingPriority = 0;
	
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
	
	

	PriorityScheduler(String name, Collection<Tunnel> c, Log log) {
		super(name);
		for(Tunnel tunnel: c) {
			tunnelList.put(tunnel, new ReentrantLock());
		}
	}

	@Override
	public boolean tryToEnterInner(Vehicle vehicle) {
		lock.lock();
		boolean entered = false;
		try {
			while(!entered) {
				//If your cool enough to go right in
				if (!gottaWait(vehicle)&&!entered&&!onWaitingList(vehicle)) {
					Iterator it = tunnelList.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>)it.next();
						if(pair.getKey().tryToEnter(vehicle)) {
							VehicleAndTunnel.put(vehicle, pair.getKey());
							entered = true;
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
					prioCond.await();
				}
			}
				
			
		} finally {
			lock.unlock();
			return entered;
		}
	}
	

	@Override
	public void exitTunnelInner(Vehicle vehicle) {
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
	
}
