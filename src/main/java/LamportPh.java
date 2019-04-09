
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Set;

import visidia.simulation.process.messages.Door;
import visidia.simulation.process.messages.IntegerMessage;
import visidia.simulation.process.messages.MessageType;

public class LamportPh extends Philosopher {
	
	private static class Timestamp  implements Comparable<Timestamp>{
		int ts, i;

		public Timestamp(int ts, int i) {
			this.ts = ts;
			this.i = i;
		}

		@Override
		public int compareTo(Timestamp t) {
			if (ts!=t.ts) {
				return ts - t.ts;
			}
			else {
				return i - t.i;
			}			
		}

	}
	
	private static class RequestMsg extends IntegerMessage implements Comparable<RequestMsg>{
				
		int from;
		
		public RequestMsg(int ts, int from) {
			super(ts, new MessageType("request", true, java.awt.Color.red));
			this.from = from;
		}
		
		@Override
		public int compareTo(RequestMsg o) {
			return new Timestamp(getData(), from).compareTo(new Timestamp(o.getData(), o.from));
		}

		@Override
		public Object clone() {
			return new RequestMsg(getData(), from);
		}

		@Override
		public String toString() {
			return "RequestMsg [from=" + from + ", ts="+getData()+"]";
		}				
	}

	private static class ReplyMsg extends IntegerMessage {

		int from;
		
		public ReplyMsg(int ts, int from) {
			super(ts, new MessageType("reply", true, java.awt.Color.blue));
			this.from = from;
		}
		
		@Override
		public Object clone() {
			return new ReplyMsg(getData(), from);
		}				
		
		@Override
		public String toString() {
			return "ReplyMsg [from=" + from + ", ts="+getData()+"]";
		}						
	}
	
	private static class ReleaseMsg extends IntegerMessage {

		int from;

		public ReleaseMsg(int ts, int from) {
			super(ts, new MessageType("release", true, java.awt.Color.green));
			this.from = from;
		}
		
		@Override
		public Object clone() {
			return new ReleaseMsg(getData(), from);
		}						
		
		@Override
		public String toString() {
			return "ReleaseMsg [from=" + from + ", ts="+getData()+"]";
		}						
	}
		
	private class MsgHandler extends Thread {

		LamportPh ph = LamportPh.this;
		
		@Override
		public void run() {
			while (true) {
				try {
					Door door = new Door();
					IntegerMessage msg = (IntegerMessage) ph.receive(door);
					if (handleMessage(msg,door)) {
						synchSet.add(door.getNum());
					}
					Thread.sleep(MSG_HANDLER_DELAY);
				}
				catch (InterruptedException e) {
					break;
				}
			}
		}		
		
		private boolean handleMessage(IntegerMessage msg, Door door) {		
			System.out.println("Handling message at "+ph.getId()+" -> "+msg);

			// synch iff msg is newer than current
			boolean synch = false;
			if (current_request!=null && new Timestamp(msg.data(), door.getNum()).compareTo(new Timestamp(current_request.data(), ph.getId())) > 0) {
				synch = true;			
			}
			System.out.println("Synch is -> "+synch);
			
			// increment logical clock
			logical_clock = Math.max(logical_clock, msg.getData());
			int ts = logical_clock++;

			if (msg instanceof RequestMsg) {
				RequestMsg request = (RequestMsg)msg;
				System.out.println("Received request at "+ph.getId()+" -> "+request);
				// add to request queue
				request_queue.add(request);
				ReplyMsg reply = new ReplyMsg(ts, ph.getId());
				System.out.println("Sending reply to "+request.from+" -> "+reply);
				sendTo(door.getNum(), reply);					
			}		
			else if (msg instanceof ReplyMsg) {
				ReplyMsg reply = (ReplyMsg)msg;
				System.out.println("Received reply at "+ph.getId()+" -> "+reply);
			}				
			else if (msg instanceof ReleaseMsg) {
				ReleaseMsg release = (ReleaseMsg)msg;
				System.out.println("Received release at "+ph.getId()+" -> "+release);
				System.out.println("Rq at "+ph.getId()+" -> "+request_queue);
				// remove from request queue
				for (Iterator<RequestMsg> it = request_queue.iterator(); it.hasNext();) {
					RequestMsg request = it.next();
					if (request.from == release.from) {
						it.remove();
					}				
				}
				System.out.println("Rq at "+ph.getId()+" -> "+request_queue);			
			}

			return synch;
		}		
	}
		
	private int logical_clock = 0;
	private PriorityQueue<RequestMsg> request_queue = new PriorityQueue<RequestMsg>();
	private RequestMsg current_request = null;
	private Set<Integer> synchSet = new HashSet<Integer>();
	private MsgHandler handler;
	
	@Override
	public void init() {
		handler = new MsgHandler();
		handler.start();
		super.init();
	}
	
	@Override
	public void trySection() throws InterruptedException {
		super.trySection();

		// increment logical clock
		int ts = logical_clock++;
		
		current_request = new RequestMsg(ts, getId());
		request_queue.add(current_request);		
		
		// send request		
		RequestMsg request = new RequestMsg(ts, getId());
		System.out.println("Sending request to all -> "+request);
		sendAll(request);

		// wait for synch response
		System.out.println("Waiting for synch response at "+getId());
		synchSet.clear();
		int nbNeighbors = getArity();
		while (synchSet.size()<nbNeighbors) {
			Thread.sleep(TRY_WAITING_DELAY);
		}
		
		// wait for priority
		System.out.println("Waiting at "+getId()+" for priority, rq -> "+request_queue);
		while (true) {
			RequestMsg top = request_queue.peek();
			if (top.from == getId()) {
				break;
			}
			Thread.sleep(TRY_WAITING_DELAY);
		}
	}
	
	@Override
	public void exitSection() throws InterruptedException {
		super.exitSection();

		// release
		request_queue.poll();
		current_request = null;
		int ts = logical_clock++;
		ReleaseMsg release = new ReleaseMsg(ts, getId());
		System.out.println("Sending release to all -> "+release);
		sendAll(release);
	}

	@Override
	public Object clone() {
		return new LamportPh();
	}

}
