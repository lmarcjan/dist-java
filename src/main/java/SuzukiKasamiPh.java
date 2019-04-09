
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import visidia.simulation.process.messages.Door;
import visidia.simulation.process.messages.IntegerMessage;
import visidia.simulation.process.messages.Message;
import visidia.simulation.process.messages.MessageType;

public class SuzukiKasamiPh extends Philosopher {
	
	public static class Token {
		boolean idle;
		Queue<Integer> Q;
		int [] LN;
		
		public Token(boolean idle, Queue<Integer> q, int[] LN) {
			super();
			this.idle = idle;
			this.Q = q;
			this.LN = LN;
		}

		@Override
		public String toString() {
			return "Token [LN=" + Arrays.toString(LN) + ", Q=" + Q + ", idle=" + idle + "]";
		}		
	}
	
	public static class TokenMsg extends Message {

		int from;
		Token data;

		public TokenMsg(Token token, int from) {
			this.data = token;
			this.from = from;
			setType(new MessageType("token", true, java.awt.Color.green));
		}

		@Override
		public Object clone() {
			return new TokenMsg(new Token(data.idle,new LinkedList<Integer>(data.Q), Arrays.copyOf(data.LN, data.LN.length)), this.from);
		}

		@Override
		public Token getData() {
			return data;
		}

		@Override
		public String toString() {
			return "TokenMsg [from=" + from + ", token="+getData()+"]";
		}
		
	}
	
	private static class RequestMsg extends IntegerMessage {
				
		int from;
		
		public RequestMsg(int sn, int from) {
			super(sn, new MessageType("request", true, java.awt.Color.red));
			this.from = from;
		}
		
		@Override
		public Object clone() {
			return new RequestMsg(getData(), from);
		}

		@Override
		public String toString() {
			return "RequestMsg [from=" + from + ", sn="+getData()+"]";
		}				
	}
			
	private class MsgHandler extends Thread {

		SuzukiKasamiPh ph = SuzukiKasamiPh.this;
		
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

			if (msg instanceof RequestMsg) {
				RequestMsg request = (RequestMsg)msg;
				System.out.println("Received request at "+ph.getId()+" -> "+request);
				int sn = request.getData();
				int i = request.from;
				ph.doorMap.put(i, door.getNum());
				RN[i] = Math.max(RN[i], sn);
				if (ph.token!=null && ph.token.idle
						&& RN[i]==token.LN[i]+1) {
					System.out.println("1Sending token from "+ph.getId()+" to -> "+i+", token ->"+token);
					sendTo(doorMap.get(i), new TokenMsg(ph.token, ph.getId()));
					ph.token = null;
				}
			}		
			else if (msg instanceof TokenMsg) {
				TokenMsg reply = (TokenMsg)msg;
				System.out.println("Received token at "+ph.getId()+" -> "+reply);
				ph.token = reply.getData();
			}				
		}		
	}	
	
	private Map<Integer, Integer> doorMap = new HashMap<Integer, Integer>();
	private Token token;
	private int [] RN;
	private MsgHandler handler;
	
	@Override
	public void init() {
		if (getId()==0) {
			token = new Token(true, new LinkedList<Integer>(), new int[getNetSize()]);
		}
		RN = new int[getNetSize()];
		handler = new MsgHandler();
		handler.start();
		super.init();
	}
	
	@Override
	public void trySection() throws InterruptedException {
		super.trySection();

		RN[getId()]++;
		
		// send request		
		RequestMsg request = new RequestMsg(RN[getId()], getId());
		System.out.println("Sending request to all -> "+request);
		sendAll(request);

		// wait for token
		System.out.println("Waiting for token at "+getId());
		while (token == null) {
			Thread.sleep(TRY_WAITING_DELAY);
		}
		token.idle = false;
	}
	
	@Override
	public void exitSection() throws InterruptedException {
		super.exitSection();

		// release
		token.idle = true;
		token.LN[getId()] = RN[getId()];
		for (int i=0; i<getNetSize(); i++) {
			if (!token.Q.contains(i) && RN[i]==token.LN[i]+1) {
				token.Q.add(i);
			}
		}
		if (!token.Q.isEmpty()) {
			int i = token.Q.poll();
			System.out.println("2Sending token from "+getId()+" to -> "+i+", token -> "+token);
			sendTo(doorMap.get(i), new TokenMsg(token, getId()));				
			token = null;
		}		
	}

	@Override
	public Object clone() {
		return new SuzukiKasamiPh();
	}

}
