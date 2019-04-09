
import visidia.simulation.process.algorithm.Algorithm;

public abstract class RenamingAlgorithm extends Algorithm {
	
	private int name;
	
	protected void decide(int value) {
		System.out.println("Renaming to -> "+value+" at -> "+getId());
		this.name = value;
	}
}