
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import visidia.simulation.process.algorithm.Algorithm;

public abstract class Philosopher extends Algorithm {
	
	protected static final String REMAINDER_SECTION_LABEL = "R";
	protected static final String CRITICAL_SECTION_LABEL = "C";
	protected static final String EXIT_SECTION_LABEL = "E";
	protected static final String TRY_SECTION_LABEL = "T";
	protected static final String CRITICAL_SECTION_ERROR_LABEL = "err";

	protected volatile String status;
	
	protected int getCriticalSectionDelay() {
		return 2000;
	}

	protected int getRemainderSectionDelay() {
		return 5000;
	}

	protected static Set<Integer> critical_phs = Collections.synchronizedSet(new HashSet<Integer>());
		
	protected static final long MSG_HANDLER_DELAY = 100L;
	protected static final long TRY_WAITING_DELAY = 100L;

	protected Random rnd = new Random();

	protected boolean stopped = false;

	@Override
	public void init() {
		while (!stopped) {
			try {
				remainderSection();
				trySection();
				criticalSection();
				exitSection();
			}
			catch (InterruptedException e) {
				stopped = true;
			}
		}
	}

	protected void trySection() throws InterruptedException {
		System.out.println("Ph " + getId() + " is entering try section...");				
		status = TRY_SECTION_LABEL;
		putProperty("label", TRY_SECTION_LABEL);
	}
	
	protected boolean isTrySection() {
		return status.equals(TRY_SECTION_LABEL);
	}

	protected void criticalSection() throws InterruptedException {
		System.out.println("Ph " + getId() + " is entering critical section...");		
		status = CRITICAL_SECTION_LABEL;
		putProperty("label", CRITICAL_SECTION_LABEL);
		checkCritical();
		critical_phs.add(getId());
		Thread.sleep(rnd.nextInt(getCriticalSectionDelay()));
		critical_phs.remove(getId());
	}

	protected boolean isCriticalSection() {
		return status.equals(CRITICAL_SECTION_LABEL);
	}
	
	protected void checkCritical() {
		if (critical_phs.size()>=1) {
			status = CRITICAL_SECTION_ERROR_LABEL;
			putProperty("label", CRITICAL_SECTION_ERROR_LABEL);
			throw new IllegalStateException();
		}		
	}

	protected void exitSection() throws InterruptedException {
		System.out.println("Ph " + getId() + " is entering exit section...");						
		status = EXIT_SECTION_LABEL;
		putProperty("label", EXIT_SECTION_LABEL);
	}

	protected boolean isExitSection() {
		return status.equals(EXIT_SECTION_LABEL);
	}

	protected void remainderSection() throws InterruptedException {
		System.out.println("Ph " + getId() + " entering remainder section...");		
		status = REMAINDER_SECTION_LABEL;
		putProperty("label", REMAINDER_SECTION_LABEL);
		Thread.sleep(rnd.nextInt(getRemainderSectionDelay()));
	}

	protected boolean isRemainderSection() {
		return status.equals(REMAINDER_SECTION_LABEL);
	}
	
}
