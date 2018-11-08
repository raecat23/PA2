package cs131.pa2.CarsTunnels;

import java.util.Collection;

import cs131.pa2.Abstract.Direction;
import cs131.pa2.Abstract.Factory;
import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;
import cs131.pa2.Abstract.Log.Log;

public class ConcreteFactory implements Factory {

    @Override
    public Tunnel createNewBasicTunnel(String label){
    	return new BasicTunnel(label);
    }

    @Override
    public Vehicle createNewCar(String label, Direction direction){
    	return new Car(label, direction);
    }

    @Override
    public Vehicle createNewSled(String label, Direction direction){
    	return new Sled(label, direction);    
    }

    @Override
    public Tunnel createNewPriorityScheduler(String label, Collection<Tunnel> tunnels, Log log){
    	return new PriorityScheduler(label, tunnels, log);
    }

	@Override
	public Vehicle createNewAmbulance(String label, Direction direction) {
		return new Ambulance(label, direction);
	}

	@Override
	public Tunnel createNewPreemptivePriorityScheduler(String label, Collection<Tunnel> tunnels, Log log) {
		return new PreemptivePriorityScheduler(label, tunnels, log);
	}
}