
import visidia.simulation.process.algorithm.Algorithm;
import visidia.simulation.process.messages.IntegerMessage;

public abstract class CellularAlgorithm extends Algorithm {

	@Override
	public void init() {
		java.util.Random r = new java.util.Random();
		int nbNeighbors = getArity();
		while (true) {
			// Randomly select a neighbor
			int neighborDoor = r.nextInt() % nbNeighbors;
			// Send synchronization messages (0 and 1)
			for (int i = 0; i < nbNeighbors; ++i)
				sendTo(i, new IntegerMessage(i == neighborDoor ? 1 : 0));
			// Receive a message
			boolean rendezVousAccepted = false;
			for (int i = 0; i < nbNeighbors; ++i) {
				IntegerMessage msg = (IntegerMessage) receiveFrom(i);
				if ((i == neighborDoor) && (msg.value() == 1))
					rendezVousAccepted = true;
			}
			// Mark an edge and send a "Hello" message
			if (rendezVousAccepted == true) {
				communicate(neighborDoor);
			}
		}
	}
	
	protected abstract void communicate(int neighborDoor);
}
