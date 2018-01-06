package edu.uchicago.cs.ucare.dmck.transition;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

@SuppressWarnings("serial")
public abstract class Transition implements Serializable {

	public final String ACTION = "nothing";

	public abstract boolean apply();

	public abstract long getTransitionId();

	public static final Comparator<Transition> COMPARATOR = new Comparator<Transition>() {
		public int compare(Transition o1, Transition o2) {
			Long i1 = o1.getTransitionId();
			Long i2 = o2.getTransitionId();
			return i1.compareTo(i2);
		}
	};

	public static String extract(List<? extends Transition> transitions) {
		StringBuilder strBuilder = new StringBuilder();
		for (Transition transition : transitions) {
			strBuilder.append(transition.toString());
			strBuilder.append("\n");
		}
		return strBuilder.length() > 0 ? strBuilder.substring(0, strBuilder.length() - 1) : "";
	}

	public abstract int[][] getVectorClock();

	public Transition getSerializable(int numNode) {
		if (this instanceof PacketSendTransition) {
			return new PacketSendTransition(null, ((PacketSendTransition) this).getPacket());
		} else if (this instanceof NodeCrashTransition) {
			NodeCrashTransition temp = (NodeCrashTransition) this;
			NodeCrashTransition record = new NodeCrashTransition(null, temp.getId());
			record.setVectorClock(temp.getVectorClock());
			return record;
		} else if (this instanceof NodeStartTransition) {
			NodeStartTransition temp = (NodeStartTransition) this;
			NodeStartTransition record = new NodeStartTransition(null, temp.getId());
			record.setVectorClock(temp.getVectorClock());
			return record;
		} else if (this instanceof AbstractNodeCrashTransition) {
			return new AbstractNodeStartTransition(numNode);
		} else if (this instanceof AbstractNodeStartTransition) {
			return new AbstractNodeStartTransition(numNode);
		} else {
			return null;
		}
	}

	public static Transition getRealTransition(ModelCheckingServerAbstract mc, Transition t) {
		if (t instanceof PacketSendTransition) {
			return new PacketSendTransition(mc, ((PacketSendTransition) t).getPacket());
		} else if (t instanceof NodeCrashTransition) {
			NodeCrashTransition temp = (NodeCrashTransition) t;
			NodeCrashTransition realTransition = new NodeCrashTransition(mc, temp.getId());
			realTransition.setVectorClock(temp.getVectorClock());
			return realTransition;
		} else if (t instanceof NodeStartTransition) {
			NodeStartTransition temp = (NodeStartTransition) t;
			NodeStartTransition realTransition = new NodeStartTransition(mc, temp.getId());
			realTransition.setVectorClock(temp.getVectorClock());
			return realTransition;
		} else if (t instanceof AbstractNodeCrashTransition) {
			AbstractNodeCrashTransition u = new AbstractNodeCrashTransition(mc);
			u.id = ((AbstractNodeStartTransition) t).getId();
			return u;
		} else if (t instanceof AbstractNodeStartTransition) {
			AbstractNodeStartTransition u = new AbstractNodeStartTransition(mc);
			u.id = ((AbstractNodeStartTransition) t).getId();
			return u;
		} else {
			return null;
		}
	}

}
