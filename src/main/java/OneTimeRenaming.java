import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import visidia.simulation.process.messages.Door;
import visidia.simulation.process.messages.Message;
import visidia.simulation.process.messages.MessageType;

public class OneTimeRenaming extends RenamingAlgorithm {

	private static class Bid {

		int P;
		int x;
		int attempt;
		boolean decide;

		public Bid(int P, int x, int attempt, boolean decide) {
			this.P = P;
			this.x = x;
			this.attempt = attempt;
			this.decide = decide;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + P;
			result = prime * result + attempt;
			result = prime * result + (decide ? 1231 : 1237);
			result = prime * result + x;
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
			Bid other = (Bid) obj;
			if (P != other.P)
				return false;
			if (attempt != other.attempt)
				return false;
			if (decide != other.decide)
				return false;
			if (x != other.x)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Bid [P=" + P + ", attempt=" + attempt + ", decide=" + decide + ", x=" + x + "]";
		}
	}

	@Override
	public Object clone() {
		return new OneTimeRenaming();
	}

	public static class ViewMsg extends Message {

		int from;
		List<Bid> data;

		public ViewMsg(List<Bid> view, int from) {
			this.data = view;
			this.from = from;
			setType(new MessageType("view", true, java.awt.Color.green));
		}

		@Override
		public Object clone() {
			return new ViewMsg(new ArrayList<Bid>(data), this.from);
		}

		@Override
		public List<Bid> getData() {
			return data;
		}

		@Override
		public String toString() {
			return "ViewMsg [data=" + Arrays.toString(data.toArray(new Bid[data.size()])) + ", from=" + from + "]";
		}

	}

	private List<Bid> View = new ArrayList<Bid>();
	private int n;
	private int f;
	private int M;

	@Override
	public void init() {
		n = getNetSize();
		f = 0;
		M = n + f;
		decide(getId());
		View.add(new Bid(getId(), -1, 0, false));
		boolean restart = false, stable = false, no_choose = false;
		do {
			if (restart) {
				System.out.println("Restart detected at -> " + getId());
			}
			restart = false;
			sendAll(new ViewMsg(View, getId()));
			do {
				int count = 1;
				if (no_choose) {
					System.out.println("No choose detected at -> " + getId());
				}
				no_choose = false;
				do {
					Door door = new Door();
					ViewMsg viewMsg = (ViewMsg) receive(door);
					System.out.println("Handling message at " + getId() + " -> " + viewMsg);
					stable = false;
					if (isEqual(viewMsg.data, View)) {
						count++;
						if (count >= n - f) {
							stable = true;
						}
					} else if (!isLess(viewMsg.data, View)) {
						update(View, viewMsg.data);
						restart = true;
					}
				} while (!stable && !restart);

				if (!restart) {
					System.out.println("Stable detected at -> " + getId());
					Bid b1 = View.get(0);
					boolean decide = (b1.x != -1);
					for (int j = 1; j < View.size(); j++) {
						Bid b = View.get(j);
						decide &= (b1.x != b.x);
					}
					if (decide) {
						decide(b1.x);
						b1.decide = true;
						sendAll(new ViewMsg(View, getId()));
					} else {
						int r = undecided(getId(), View);
						if (r <= f + 1) {
							b1.x = free(View, r);
							b1.attempt++;
							restart = true;
						} else {
							no_choose = true;
						}
					}
				}
			} while (no_choose);
		} while (restart);

		do {
			Door door = new Door();
			ViewMsg viewMsg = (ViewMsg) receive(door);
			System.out.println("Handling message at " + getId() + " -> " + viewMsg);
			if (!isEqual(viewMsg.data, View) && !isLess(viewMsg.data, View)) {
				update(View, viewMsg.data);
			}
			sendAll(new ViewMsg(View, getId()));
		} while (true);
	}

	private int undecided(int p, List<Bid> view) {
		int r = 1;
		for (Bid b : view) {
			if (!b.decide && b.P < p) {
				r++;
			}
		}
		System.out.println("Returning undecided rank -> "+r);
		return r;
	}

	private int free(List<Bid> view, int r) {
		List<Integer> busy = new ArrayList<Integer>();
		for (Bid b : view) {
			if (b.decide) {
				busy.add(b.x);
			}
		}
		Integer f = null;
		for (int i = 0, c = 0; i < M; i++) {
			if (!busy.contains(i)) {
				c++;
				if (c == r) {
					f = i;
				}
			}
		}
		System.out.println("Returning free name -> "+f);
		return f;
	}

	private boolean isEqual(List<Bid> view1, List<Bid> view2) {
		return new HashSet<Bid>(view1).equals(new HashSet<Bid>(view2));
	}

	private boolean isLess(List<Bid> view1, List<Bid> view2) {
		Map<Integer, Integer> attempt2 = new HashMap<Integer, Integer>();
		for (Bid b : view2) {
			attempt2.put(b.P, b.attempt);
		}
		for (Bid b : view1) {
			if (attempt2.get(b.P) == null || attempt2.get(b.P) < b.attempt) {
				return false;
			}
		}
		return true;
	}

	private void update(List<Bid> view1, List<Bid> view2) {
		System.out.println("Updating view1 -> " + Arrays.toString(view1.toArray(new Bid[view1.size()])) + " by view2 -> "
				+ Arrays.toString(view2.toArray(new Bid[view2.size()])));
		Map<Integer, Integer> attempt1 = new HashMap<Integer, Integer>();
		Map<Integer, Integer> pos1 = new HashMap<Integer, Integer>();
		int i = 0;
		for (Bid b : view1) {
			attempt1.put(b.P, b.attempt);
			pos1.put(b.P, i++);
		}
		for (Bid b : view2) {
			if (attempt1.get(b.P) == null) {
				view1.add(b);
			} else if (b.attempt > attempt1.get(b.P)) {
				int p = pos1.get(b.P);
				view1.remove(p);
				view1.add(p, b);
			}
		}
		System.out.println("Updated view1 -> " + Arrays.toString(view1.toArray(new Bid[view1.size()])));
	}

}
