
import visidia.simulation.process.edgestate.MarkedState;
import visidia.simulation.process.messages.StringMessage;

public class MyRendezVous extends CellularAlgorithm {

	@Override
	public Object clone() {
		return new MyRendezVous();
	}

	@Override
	protected void communicate(int neighborDoor) {
		setDoorState(new MarkedState(true), neighborDoor);
		sendTo(neighborDoor, new StringMessage("Hello"));
		receiveFrom(neighborDoor);
		setDoorState(new MarkedState(false), neighborDoor);
	}
}
