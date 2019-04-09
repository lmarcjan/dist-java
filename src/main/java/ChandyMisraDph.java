
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import visidia.simulation.process.messages.Door;
import visidia.simulation.process.messages.IntegerMessage;
import visidia.simulation.process.messages.Message;
import visidia.simulation.process.messages.MessageType;


public class ChandyMisraDph extends ForkedDiningPhilosopher<ChandyMisraDph.Fork> {
	
	public static class Fork {
		
		Set<Integer> phs;
		int color;
		
		protected final static int DIRTY = 0;
		protected final static int CLEAN = 1;
		
		public Fork(Set<Integer> phs, int color) {
			this.phs = phs;
			this.color = color;
		}

		public Fork(Set<Integer> phs) {
			this(phs, -1);
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((phs == null) ? 0 : phs.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Fork other = (Fork) obj;
			if (phs == null) {
				if (other.phs != null)
					return false;
			} else if (! phs.equals(other.phs))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Fork [phs=" + phs + ", "+(color==0?"dirty":"clean")+"]";
		}
		
	}
	
	public static class ForkMsg extends Message {

		int from;
		Fork data;

		public ForkMsg(Fork fork, int from) {
			this.data = fork;
			this.from = from;
			setType(new MessageType("fork", true, java.awt.Color.green));
		}

		@Override
		public Object clone() {
			return new ForkMsg(new Fork(data.phs, data.color), this.from);
		}

		@Override
		public Fork getData() {
			return data;
		}

		@Override
		public String toString() {
			return "ForkMsg [data=" + data + ", from=" + from + "]";
		}
		
	}				
	
	private static class RequestForkMsg extends IntegerMessage {
		
		public RequestForkMsg(int from) {
			super(from, new MessageType("request_fork", true, java.awt.Color.red));
		}
		
		@Override
		public Object clone() {
			return new RequestForkMsg(getData());
		}

		@Override
		public String toString() {
			return "RequestForkMsg [from=" + getData()+"]";
		}				
	}	
	
	private static class InitForkMsg extends IntegerMessage {
		
		public InitForkMsg(int from) {
			super(from, new MessageType("init_fork", true, java.awt.Color.gray));
		}
		
		@Override
		public Object clone() {
			return new InitForkMsg(getData());
		}

		@Override
		public String toString() {
			return "InitForkMsg [from=" + getData()+"]";
		}						
	}	
	
	private class MsgHandler extends Thread {

		ChandyMisraDph ph = ChandyMisraDph.this;
		
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

			if (msg instanceof RequestForkMsg) {
				RequestForkMsg requestForkMsg = (RequestForkMsg)msg;
				System.out.println("Received request fork at "+ph.getId()+" -> "+requestForkMsg);
				int i = requestForkMsg.data();
				Set<Integer> phs = new HashSet<Integer>(Arrays.asList(new Integer [] {ph.getId(), i}));
				Fork requestedFork = new Fork(phs);
				if (!ph.collected_resources.contains(requestedFork)) {
					throw new IllegalStateException();
				}
				else {
					for (Fork fork : ph.collected_resources) {
						if (fork.equals(requestedFork)) {
							if (fork.color==Fork.DIRTY) {
								// give up fork
								System.out.println("Sending fork from "+ph.getId()+" to "+i);
								ph.collected_resources.remove(fork);
								ph.sent_fork_requests.remove(i);
								sendTo(doorMap.get(i), new ForkMsg(new Fork(phs, Fork.CLEAN), ph.getId()));
							}
							else if (fork.color==Fork.CLEAN) {
								// keep fork
								System.out.println("Dereffering fork request at "+ph.getId()+" from "+i);								
								ph.deferred_fork_requests.add(requestForkMsg);
							}
							else {
								throw new IllegalStateException();								
							}
							break;
						}										
					}
				}
			}
			else if (msg instanceof InitForkMsg) {
				InitForkMsg initForkMsg = (InitForkMsg)msg;
				System.out.println("Received init fork at "+ph.getId()+" -> "+initForkMsg);
				int i = initForkMsg.getData();
				ph.doorMap.put(i, door.getNum());
			}			
			else if (msg instanceof ForkMsg) {
				ForkMsg forkMsg = (ForkMsg)msg;
				System.out.println("Received fork at "+ph.getId()+" -> "+forkMsg);
				ph.collected_resources.add(forkMsg.data);
			}
		}		
	}		

	private Map<Integer, Integer> doorMap  = new HashMap<Integer, Integer>();
	private List<RequestForkMsg> deferred_fork_requests = Collections.synchronizedList(new ArrayList<RequestForkMsg>());
	private MsgHandler handler;
	private Map<Integer,Boolean> sent_fork_requests = Collections.synchronizedMap(new HashMap<Integer, Boolean>());
	
	@Override
	public void init() {
		collected_resources = Collections.synchronizedSet(new HashSet<Fork>());
		handler = new MsgHandler();
		handler.start();
		int nbNeighbors = getArity();
		sendAll(new InitForkMsg(getId()));
		while (doorMap.size()<nbNeighbors) {
			try {
				Thread.sleep(TRY_WAITING_DELAY);
			} catch (InterruptedException e) {
				return;
			}
		}
		for (int nbId : doorMap.keySet()) {
			if (nbId < getId()) {
				// create a fork and give it to the philosopher with the lower ID
				Set<Integer> phs = new HashSet<Integer>(Arrays.asList(new Integer [] {getId(), nbId}));
				System.out.println("Sending fork from to "+nbId);
				sendTo(doorMap.get(nbId), new ForkMsg(new Fork(phs, Fork.DIRTY), getId()));
			}
		}
		doorMap.clear();
		sendAll(new InitForkMsg(getId()));
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
		return new ChandyMisraDph();
	}
	
	@Override
	public void trySection() throws InterruptedException {
		super.trySection();
		
		// wait for forks
		System.out.println("Waiting for forks at "+getId());
		sent_fork_requests.clear();
		while (collected_resources.size()<getArity()) {
			for (int nbId : doorMap.keySet()) {
				Set<Integer> phs = new HashSet<Integer>(Arrays.asList(new Integer [] {getId(), nbId}));
				if (!collected_resources.contains(new Fork(phs)) && !sent_fork_requests.containsKey(nbId)) {
					sent_fork_requests.put(nbId, false);
				}
			}
			// send request for a fork
			for (int nbId : sent_fork_requests.keySet()) {
				if (!sent_fork_requests.get(nbId)) {
					sendTo(doorMap.get(nbId), new RequestForkMsg(getId()));		
					sent_fork_requests.put(nbId, true);
				}
			}
			Thread.sleep(TRY_WAITING_DELAY);
		}				
	}

	@Override
	protected void exitSection() throws InterruptedException {
		super.exitSection();
		
		// release forks
		for (Iterator<Fork> it = collected_resources.iterator(); it.hasNext();) {
			Fork fork = it.next();
			fork.color = Fork.DIRTY;
		}
		// handle deferred fork requests
		for (Iterator<RequestForkMsg> it = deferred_fork_requests.iterator(); it.hasNext();) {
			RequestForkMsg requestForkMsg = it.next();
			handler.handleMessage(requestForkMsg, new Door(doorMap.get(requestForkMsg.data())));
			it.remove();		
		}
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
