

public class UnsynchronizedPh extends Philosopher {

	@Override
	public Object clone() {
		return new UnsynchronizedPh();
	}

}
