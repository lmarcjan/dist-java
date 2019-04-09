
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import visidia.simulation.process.messages.Door;
import visidia.simulation.process.messages.IntegerMessage;
import visidia.simulation.process.messages.Message;
import visidia.simulation.process.messages.MessageType;


public class SaksAwerbuchDph extends DiningPhilosopher {


	private static class InitMsg extends IntegerMessage {
		
		public InitMsg(int from) {
			super(from, new MessageType("init", true, java.awt.Color.gray));
		}
		
		@Override
		public Object clone() {
			return new InitMsg(getData());
		}

		@Override
		public String toString() {
			return "InitMsg [from=" + getData()+"]";
		}
		
	}				

	private static class Position {
		
		int level;
		int slot;
		
		public Position(int level, int slot) {
			this.level = level;
			this.slot = slot;
		}
		
		@Override
		public String toString() {
			return "Position [level=" + level + ", slot=" + slot + "]";
		}
	}
	
	public static class ReportMsg extends Message {

		int from;
		Position data;

		public ReportMsg(Position position, int from) {
			this.data = position;
			this.from = from;
			setType(new MessageType("report", true, java.awt.Color.blue));
		}

		@Override
		public Object clone() {
			return new ReportMsg(new Position(this.data.level, this.data.slot), this.from);
		}

		@Override
		public Position getData() {
			return data;
		}

		@Override
		public String toString() {
			return "ReportMsg [data=" + data + ", from=" + from + "]";
		}
		
	}					
	private class MsgHandler extends Thread {

		SaksAwerbuchDph ph = SaksAwerbuchDph.this;
		
		@Override
		public void run() {
			while (true) {
				try {				
					Door door = new Door();
					Message msg = (Message) ph.receive(door);
					handleMessage(msg,door);
					Thread.sleep(MSG_HANDLER_DELAY);
				} catch (InterruptedException e) {
					break;
				}							
			}
		}		
		
		private void handleMessage(Message msg, Door door) {		
			
			System.out.println("Handling message at "+ph.getId()+" -> "+msg);
			
			if (msg instanceof ReportMsg) {
				ReportMsg reportMsg = (ReportMsg)msg;
				System.out.println("Received report at "+ph.getId()+" -> "+reportMsg);
				Position p = reportMsg.getData();
				int k = reportMsg.from;
				compete_set.add(k);
				imbalance[k]--;
				compete_position[k]=p;
				if (imbalance[k]==0) {
					if (p.level==0 && p.slot==-1) {
						compete_set.remove(k);
					}
					for (boolean a=true;a;) {
						for (int kk : compete_set) {
							if (imbalance[kk]>0) {
								a = false;
							}
						}
						if (position.level==0 && position.slot<=0) {
							a = false;
						}
						if (a) {
							advance();
							rebalance();
							announce();
						}
					}
				}
				else if (imbalance[k]==-1) {
					if (p.level!=position.level || p.slot!=position.slot+1) {
						inform(k);
					}
				}
			}
			else if (msg instanceof InitMsg) {
				InitMsg initMsg = (InitMsg)msg;
				System.out.println("Received init at "+ph.getId()+" -> "+initMsg);
				int i = initMsg.getData();
				ph.doorMap.put(i, door.getNum());
			}									
		}

	}		

	private Map<Integer, Integer> doorMap  = new HashMap<Integer, Integer>();
	private MsgHandler handler;
	
	private Position position = new Position(0, -1);
	private volatile boolean queue_front = false;
	private Set<Integer> compete_set = new HashSet<Integer>();	
	private Position [] compete_position;
	private int [] imbalance;
	
	private void schedule(Set<Integer> compete) {
		System.out.println("Scheduling at "+getId());
		compete_set = new HashSet<Integer>(compete);
		position = new Position(0, 0);
		for (int k : compete_set) {
			compete_position[k] = new Position(bit(getId(),k), Integer.MAX_VALUE);
			position.level = Math.max(position.level, compete_position[k].level+1);
		}
		System.out.println(", start position is -> "+position);		
		announce();
	}

	private void announce() {
		if (position.level==0 && position.slot==0) {
			System.out.println("Execute at -> "+getId());
			execute();
		}
		else {
			for (int k : compete_set) {
				if (obstructs(compete_position[k], position)) {
					inform(k);
				}
			}			
		}
	}	
	
	private void execute() {
		queue_front = true;
	}

	private void done() {
		position = new Position(0, -1);
		queue_front = false;
		rebalance();
	}

	private void advance() {
		System.out.println("Advancing at "+getId()+", old position is -> "+position);
		if (position.slot > 0) {
			position.slot--;
		}
		else {
			position.level--;
			int l = getId();
			for (int i = position.level; i>0; i--) {
				l /= 2;
			}
			for (int t=0;;) {
				// check if proper
				if ((t%4) != (2*l%4)) {
					t++; continue;
				}
				// check if free
				Set<Integer> filled = new HashSet<Integer>();
				for (int k : compete_set) {
					if (compete_position[k].level==position.level) {
						filled.add(compete_position[k].slot);
					}
				}
				if (filled.contains(t)||filled.contains(t+1)) {
					t++; continue;
				}
				position.slot = t;
				break;
			}
		}
		System.out.println(", new position is -> "+position);
	}				
	
	private void rebalance() {
		for (int k : compete_set) {
			if (imbalance[k]==-1) {
				inform(k);
			}
		}		
	}
	
	private void inform(int k) {
		System.out.println("Sending report to "+k);
		sendTo(doorMap.get(k), new ReportMsg(new Position(position.level, position.slot), getId()));
		imbalance[k]++;
	}

	private boolean obstructs(Position p1, Position p2) {
		if ((p2.level==p1.level && p2.slot==p1.slot) || (p2.level==p1.level && p2.slot==p1.slot+1)) {
			return true;
		}
		if (p2.level==p1.level+1 && p2.slot==0) {
			return true;
		}
		if ((p1.level==p2.level+1 && p1.slot==0) && p2.slot>1) {
			return true;
		}
		return false;
	}

	protected int bit(int j, int k) {
		int m = -1;
		for (int i = 0; j!=0 || k!=0; i++) {
			if (j%2!=k%2) {
				m = i;
			}
			j /= 2;
			k /= 2;
		}		
		return m;
	}		
	
	@Override
	public void init() {		
		compete_position = new Position [getNetSize()];
		imbalance = new int [getNetSize()];		
		handler = new MsgHandler();
		handler.start();
		int nbNeighbors = getArity();
		sendAll(new InitMsg(getId()));
		while (doorMap.size()<nbNeighbors) {
			try {
				Thread.sleep(TRY_WAITING_DELAY);
			} catch (InterruptedException e) {
				return;
			}
		}
				
		super.init();
	}
	
	@Override
	public Object clone() {
		return new SaksAwerbuchDph();
	}
	
	@Override
	public void trySection() throws InterruptedException {
		super.trySection();
		
		schedule(doorMap.keySet()); // TODO too strong
		
		// wait for queue front
		System.out.println("Waiting for queue front at "+getId());
		while (!queue_front) {
			Thread.sleep(TRY_WAITING_DELAY);
		}						
	}

	@Override
	protected void exitSection() throws InterruptedException {
		super.exitSection();
		
		done();
	}	

	@Override
	protected void checkCritical() {
		for (int nbId : doorMap.keySet()) {
			if (critical_phs.contains(nbId)) {
				putProperty("label", "err");
				throw new IllegalStateException();				
			}
		}		
	}		
	
}
