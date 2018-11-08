package cs131.pa2.CarsTunnels;

import java.util.LinkedList;
import java.util.concurrent.locks.*;

import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;

public class BasicTunnel extends Tunnel{
	
	private String dir = null; //Variable to check direction
	
	public int Priority = 0; //Added for Part 2.
	
	int activeCars;//Car count
	int activeSled;//Sled count
	
	
	private boolean carsShouldPass() {//Returns true if cars should pass this tunnel
		return (activeCars > 2 || activeSled > 0);
	}
	
	private boolean sledShouldPass() {//returns true if sleds should pass this tunnel
		return (activeCars > 0 || activeSled > 0);
	}
	

	public BasicTunnel(String name) {
		super(name);
	}

	@Override
	public synchronized boolean tryToEnterInner(Vehicle vehicle) {//Directional checking
			if (dir == null) {
				dir = vehicle.getDirection().toString(); 
				return checkToEnter(vehicle);
			} else if (vehicle.getDirection().toString().equals(dir)) {
				dir = vehicle.getDirection().toString();
				return checkToEnter(vehicle);
			} 
			return false;
	}
	
	public synchronized boolean checkToEnter(Vehicle vehicle) {//Is the tunnel full checking
		if(vehicle instanceof Car) {
			if (!carsShouldPass()) {
				activeCars++;
				return true;
			} else {
				return false;
			}
		} else if (vehicle instanceof Sled) {
			if(sledShouldPass()) {
				return false;
			} else {
				activeSled++;
				return true;
			}
		}
		return false;
	}
	
	
	@Override
	public synchronized void exitTunnelInner(Vehicle vehicle) {//Reset direction if tunnel is empty, and also exit vehicles.
		if(vehicle instanceof Car) {
			activeCars--;			
		} else if (vehicle instanceof Sled) {
			activeSled--;	
		}
		
		
		if (activeCars + activeSled == 0) {
			dir = null;
		}
	}
	
}
