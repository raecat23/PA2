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

public class PreemptivePriorityScheduler extends Tunnel {
	Map<Tunnel, Lock> progressingLocks = new HashMap<Tunnel, Lock>();
	Map<Tunnel, Lock> nonProgressingLocks = new HashMap<Tunnel, Lock>();
	Map<Tunnel, Condition> progressingConditions = new HashMap<Tunnel, Condition>();
	Map<Tunnel, Condition> nonProgressingConditions = new HashMap<Tunnel, Condition>();

	private final Lock lock = new ReentrantLock();
	private final Condition prioCond = lock.newCondition();
	public HashMap<Vehicle, Tunnel> VehicleAndTunnel = new HashMap<Vehicle, Tunnel>();

	public ArrayList<Vehicle> prioWait = new ArrayList<Vehicle>();

	int maxWaitingPriority = 0;

	public PreemptivePriorityScheduler(String name, Collection<Tunnel> tunnels, Log log) {
		super(name, log);
		for (Tunnel t : tunnels) { // create the locks and conditions for each tunnel
			Lock prog = new ReentrantLock();
			progressingLocks.put(t, prog);
			Lock nonprog = new ReentrantLock();
			nonProgressingLocks.put(t, nonprog);
			progressingConditions.put(t, prog.newCondition());
			nonProgressingConditions.put(t, nonprog.newCondition());
		}
	}

	public boolean gottaWait(Vehicle vehicle) {// Do you have to wait because of priority.
		return (vehicle.getPriority() < maxWaitingPriority);
	}

	@SuppressWarnings("finally")
	public boolean onWaitingList(Vehicle vehicle) { // If you are on the waiting list or not
		boolean answer = prioWait.contains(vehicle);
		return answer;
	}

	@SuppressWarnings("finally")
	@Override
	public boolean tryToEnterInner(Vehicle vehicle) {
		lock.lock(); //entering a CS, so locking the lock
		boolean entered = false;
		try {
			vehicle.addPPS(this); //add this to vehicle so it can access the data structures
			boolean ambulance = false;
			if (vehicle instanceof Ambulance) { //if its an ambulance, set boolean to true to use later 
				ambulance = true;
			}
			while (!entered) {
				// if you can enter immediately (nothing has entered, you dont have to wait for specific conditions, and youre not on the waiting list)
				if (!gottaWait(vehicle) && !entered && !onWaitingList(vehicle)) {
					Iterator<Entry<Tunnel, Lock>> it = progressingLocks.entrySet().iterator();
					while (it.hasNext()) { //proceed through each tunnel and find one you can go into (if any)
						Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>) it.next();
						if (pair.getKey().tryToEnter(vehicle) && !entered) { //if you find one and you havent already entered
							VehicleAndTunnel.put(vehicle, pair.getKey());
							entered = true;
							if (ambulance) {//if it is an ambulance, lock because of an inner CS, interrupt all cars, and then unlock
								vehicle.p.getProgressingLock(vehicle).lock();
								try {
									vehicle.p.getProgressingCon(vehicle).signalAll();
								} finally {
									vehicle.p.getProgressingLock(vehicle).unlock();
								}
							}
							break;
						}
					}
					// If you didn't enter, go into
					if (!entered) {
						if (vehicle.getPriority() > maxWaitingPriority) {// Set the maxWaitingPriority
							maxWaitingPriority = vehicle.getPriority();
						}
						prioWait.add(vehicle); //add the car to the waiting list
					}
				} else if (onWaitingList(vehicle) && !entered && !gottaWait(vehicle)) { //if you are on the waiting list and pass the conditions to enter
					Iterator<Entry<Tunnel, Lock>> it = progressingLocks.entrySet().iterator();
					while (it.hasNext()) { //proceed through each tunnel until you can enter one (if any)
						Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>) it.next();
						if (pair.getKey().tryToEnter(vehicle)) { //if you can enter remove yourself from the wait list, possibly set yourself as the highest priority, and enter
							prioWait.remove(vehicle);
							int maxPrio = 0;
							for (Vehicle v : prioWait) {
								if (v.getPriority() > maxPrio) {
									maxPrio = v.getPriority();
								}
							}
							maxWaitingPriority = maxPrio;
							VehicleAndTunnel.put(vehicle, pair.getKey());
							entered = true;
							if (ambulance) {//if you are an ambulance, lock because entering an inner CS, interrupt all cars, and then unlock
								vehicle.p.getProgressingLock(vehicle).lock();
								try {
									vehicle.p.getProgressingCon(vehicle).signalAll();
								} finally {
									vehicle.p.getProgressingLock(vehicle).unlock();
								}
							}
							break;
						}

					}
				}

				if (!entered) { //if nothing entered, await until you have been signaled 
					prioCond.await();
				}
			}

		} finally {//end the critical section
			lock.unlock();
			return entered;
		}

	}
	//

	@Override
	public void exitTunnelInner(Vehicle vehicle) {
		boolean removedSomething = false;
		lock.lock(); //entering a CS, lock
		try {
			Iterator<Entry<Vehicle, Tunnel>> iter = VehicleAndTunnel.entrySet().iterator();
			while (iter.hasNext()) { //proceed through the vehicles 
				Map.Entry<Vehicle, Tunnel> bingo = (Map.Entry<Vehicle, Tunnel>) iter.next();
				if (bingo.getKey().equals(vehicle)) { //if the vehicle that is leaving equals the vehicle the iterator is currently on
					try {//we found the vehicle, now we need to find the tunnel
						Iterator<Entry<Tunnel, Lock>> bitter = progressingLocks.entrySet().iterator(); 
						while (bitter.hasNext()) {//go through all the tunnels
							Map.Entry<Tunnel, Lock> pair = (Map.Entry<Tunnel, Lock>) bitter.next();
							if (pair.getKey().equals(bingo.getValue()) && !removedSomething) {//if we find the tunnel that equals the tunnel the vehicle is in
								bingo.getValue().exitTunnel(bingo.getKey()); //exit the tunnel
								removedSomething = true;
								if (vehicle instanceof Ambulance) {//enter inner CS, lock, signal all cars to wake up from awaiting, and unlock
									nonProgressingLocks.get(pair.getKey()).lock();
									try {
										nonProgressingConditions.get(pair.getKey()).signalAll();
									} finally {
										nonProgressingLocks.get(pair.getKey()).unlock();
									}
								}
							}
						}
					} finally {//remove if weve found something to remove 
						iter.remove();
					}
				}
			}
		} finally {
			if (removedSomething) {//if that was removed, signal everything in the waiting list to try to get in
				prioCond.signalAll();
			}
			lock.unlock();//exiting CS, unlock
		}
	}
	/*
	 * methods that get locks and conditions when given the vehicle to be used in vehicle class
	 */
	public Lock getNonProgressingLock(Vehicle vehicle) {
		Tunnel temp = VehicleAndTunnel.get(vehicle);
		return nonProgressingLocks.get(temp);
	}

	public Lock getProgressingLock(Vehicle vehicle) {
		Tunnel temp = VehicleAndTunnel.get(vehicle);
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
