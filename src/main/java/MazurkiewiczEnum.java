
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import visidia.simulation.process.edgestate.MarkedState;
import visidia.simulation.process.messages.IntegerMessage;
import visidia.simulation.process.messages.MessageType;

public class MazurkiewiczEnum extends CellularAlgorithm {

	private Knowledge K = new Knowledge();
	private int N;
	
	private static class Knowledge {
		
		Map<Integer,Integer> K_V = new HashMap<Integer, Integer>();		
		Map<Set<Integer>,Integer> K_E = new HashMap<Set<Integer>, Integer>();
				
		public static Knowledge max(Knowledge k1, Knowledge k2) {
			Knowledge k = new Knowledge();
			for (Integer v : k1.K_V.keySet()) {
				if (!k2.K_V.containsKey(v)) {
					k.K_V.put(v, k1.K_V.get(v));
				}
				else {
					k.K_V.put(v, Math.max(k1.K_V.get(v),k2.K_V.get(v)));
				}
			}
			for (Integer v : k2.K_V.keySet()) {
				if (!k1.K_V.containsKey(v)) {
					k.K_V.put(v, k2.K_V.get(v));
				}
			}			
			for (Set<Integer> v : k1.K_E.keySet()) {
				if (!k2.K_E.containsKey(v)) {
					k.K_E.put(v, k1.K_E.get(v));
				}
				else {
					k.K_E.put(v, Math.max(k1.K_E.get(v),k2.K_E.get(v)));
				}
			}
			for (Set<Integer> v : k2.K_E.keySet()) {
				if (!k1.K_E.containsKey(v)) {
					k.K_E.put(v, k2.K_E.get(v));
				}
			}						
			return k;
		}
		
		public int compareEdgeStrength(Set<Integer> e1, Set<Integer> e2) {
			int m1=0;
			int m2=0;
			for (Integer v : K_V.keySet()) {
				if (e1.contains(v)) {
					m1=Math.max(m1,K_V.get(v));
				}
				if (e2.contains(v)) {
					m2=Math.max(m2,K_V.get(v));
				}
			}
			return m1-m2;
		}

		public int compareVertexStrength(Integer v1, Integer v2) {
			int m1=0;
			int m2=0;
			for (Set<Integer> e : K_E.keySet()) {
				if (e.contains(v1)) {
					m1=Math.max(m1,K_E.get(e));
				}
				if (e.contains(v2)) {
					m2=Math.max(m2,K_E.get(e));
				}
			}
			return m1-m2;
		}		

		public boolean isEdgeAdjacent(Set<Integer> e1, Set<Integer> e2) {
			for (int v : e1) {
				if (e2.contains(v)) {
					return true;
				}
			}
			return false;
		}

		public boolean isVertexAdjacent(Integer v1, Integer v2) {
			for (Set<Integer> e : K_E.keySet()) {
				if (e.contains(v1) && e.contains(v2)) {
					return true;
				}
			}
			return false;
		}
	}
		
	private static class KnowledgeMsg extends IntegerMessage {
		
		Knowledge k;
		
		public KnowledgeMsg(int from, Knowledge k) {
			super(from, new MessageType("init", true, java.awt.Color.yellow));
			this.k = k;
		}
		
		@Override
		public Object clone() {
			Knowledge k = Knowledge.max(new Knowledge(), this.k);
			return new KnowledgeMsg(getData(),k);
		}

		@Override
		public String toString() {
			return "KnowledgeMsg [from=" + getData()+"]";
		}						
	}	
	
	
	@Override
	public Object clone() {
		return new MazurkiewiczEnum();
	}

	@Override
	protected void communicate(int neighborDoor) {
		setDoorState(new MarkedState(true), neighborDoor);
		sendTo(neighborDoor, new KnowledgeMsg(getId(),K));
		KnowledgeMsg neighborMsg = (KnowledgeMsg) receiveFrom(neighborDoor);
		Integer neighborId = neighborMsg.getData();
		if (!K.K_V.containsKey(neighborId)) {
			K.K_V.put(neighborId,0);
		}
		Set<Integer> e = new TreeSet<Integer>(Arrays.asList(new Integer [] {getId(),neighborId}));
		if (!K.K_E.containsKey(e)) {
			K.K_E.put(e,0);
		}		
		Knowledge neighborK = neighborMsg.k;
		
		// knowledge unification
		Knowledge M = Knowledge.max(K, neighborK);
		// edge updating
		for (Set<Integer> f : M.K_E.keySet()) {
			if (!f.equals(e) && M.K_E.get(f).equals(M.K_E.get(e))) {
				if (M.isEdgeAdjacent(e,f) || M.compareEdgeStrength(e,f)<0) {
					M.K_E.put(e,M.K_E.get(e)+1);
				}
			}
		}
		// node updating
		for (Integer v : e) {
			for (Integer u : M.K_V.keySet()) {
				if (!u.equals(v) && M.K_V.get(u).equals(M.K_V.get(v))) {
					if (M.isVertexAdjacent(v,u) || M.compareVertexStrength(v,u)<0) {
						M.K_V.put(v,M.K_V.get(v)+1);
					}
				}
			}
			if (v.equals(getId())) {
				K = M;
				N = K.K_V.get(getId());
				putProperty("label", ""+N);
			}
		}
		setDoorState(new MarkedState(false), neighborDoor);		
	}

	@Override
	public void init() {
		K.K_V.put(getId(),0);
		N = K.K_V.get(getId());
		putProperty("label", ""+N);
		super.init();
	}

}
