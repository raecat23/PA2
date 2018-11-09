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
	
	public boolean gottaWait(Vehicle vehicle) { //Do you have to wait because of priority.
		return (vehicle.getPriority() < maxWaitingPriority);
	}
	
	public boolean onWaitingList(Vehicle vehicle) { //If you are on the waiting list or not
		boolean answer = prioWait.contains(vehicle);
		return answer;
	}
	
	

	PriorityScheduler(String name, Collection<Tunnel> c, Log log) {
		super(name);
		for(Tunnel tunnel: c) { //Make the tunnelList
			tunnelList.put(tunnel, new ReentrantLock());
		}
	}

	@Override
	public boolean tryToEnterInner(Vehicle vehicle) {
		lock.lock();//Lock the lock
		boolean entered = false; //Boolean to measure if vehicles have entered.
		try {
			while(!entered) {
				//If your cool enough to walk right into a tunnel
				if (!gottaWait(vehicle)&&!entered&&!onWaitingList(vehicle)) {//High priority, you havent entered yet and your not on the waiting list
					Iterator it = tunnelList.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>)it.next();
						if(pair.getKey().tryToEnter(vehicle)&&!entered) {//If you havent entered, and you pass tryToEnter
							VehicleAndTunnel.put(vehicle, pair.getKey());// Put it in the v and t list to keep track of it for exitting
							entered = true;
							break;
						}		
					}
					//If you didn't enter, go into the priowait
					if(!entered) {
						if(vehicle.getPriority() > maxWaitingPriority) {//Set maxwaitingprioirity
							maxWaitingPriority = vehicle.getPriority();
						}
						prioWait.add(vehicle);
					}
				} else if (onWaitingList(vehicle)&&!entered&&!gottaWait(vehicle)){//If you dont have to wait, you havent entered, and you are on the waiting list.
					Iterator it = tunnelList.entrySet().iterator();
					while (it.hasNext()) {//Go through every tunnel
						Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>)it.next();
						if(pair.getKey().tryToEnter(vehicle)&&!entered) {// If you pass trytoenter and you haven't entered
							prioWait.remove(vehicle);	
							
							int maxPrio = 0;//Checking what the highest priority in the list is.
							for (Vehicle v: prioWait) {
								if (v.getPriority() > maxPrio) {
									maxPrio = v.getPriority();
								}
							}
							maxWaitingPriority = maxPrio;	
							
							entered = true;
							VehicleAndTunnel.put(vehicle, pair.getKey());	//Put the vehicle in the v and t list to keep track of it later.
							break;
						}					
					}
				}
				
				if(!entered) {	//If you didn't enter twiddle your thumbs until you get notified.
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
		lock.lock();//Lock the lock
		try {
			Iterator iter = VehicleAndTunnel.entrySet().iterator();
			while(iter.hasNext()) {//Check through the hashmap
				Map.Entry<Vehicle, Tunnel> bingo = (Map.Entry<Vehicle, Tunnel>)iter.next();
				if(bingo.getKey().equals(vehicle)) {
					try {
						Iterator bitter = tunnelList.entrySet().iterator();
						while (bitter.hasNext()) {//Go through every tunnel
							Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>)bitter.next();
							if(pair.getKey().equals(bingo.getValue())&&!removedSomething) {//If you haven't removed something and the tunnels are the same
								bingo.getValue().exitTunnel(bingo.getKey());
								removedSomething = true;
								break;
							}
						}
					} finally {
						iter.remove();	//Take that guy out of the hashmap					
					}
				}
			}
		} finally {
			if (removedSomething) {prioCond.signalAll();}//Something left so wake everyone up
			lock.unlock();//Unlock locks.
		}
		
	}
	
}