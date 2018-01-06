package edu.uchicago.cs.ucare.dmck.transition;

import java.io.Serializable;

import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

public class TransitionTuple implements Serializable {

	private static final long serialVersionUID = -592045758670070432L;

	public int state;
	public Transition transition;

	public TransitionTuple(int state, Transition transition) {
		this.state = state;
		this.transition = transition;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((transition == null) ? 0 : transition.hashCode());
		result = prime * result + state;
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
		TransitionTuple other = (TransitionTuple) obj;
		if (transition == null) {
			if (other.transition != null)
				return false;
		} else if (!transition.equals(other.transition))
			return false;
		if (state != other.state)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return transition.toString();
	}

	public TransitionTuple getSerializable(int numNode) {
		if (transition instanceof PacketSendTransition) {
			return new TransitionTuple(state,
					new PacketSendTransition(null, ((PacketSendTransition) transition).getPacket()));
		} else if (transition instanceof NodeCrashTransition) {
			NodeCrashTransition temp = (NodeCrashTransition) transition;
			NodeCrashTransition record = new NodeCrashTransition(null, temp.getId());
			record.setVectorClock(temp.getVectorClock());
			return new TransitionTuple(state, record);
		} else if (transition instanceof NodeStartTransition) {
			NodeStartTransition temp = (NodeStartTransition) transition;
			NodeStartTransition record = new NodeStartTransition(null, temp.getId());
			record.setVectorClock(temp.getVectorClock());
			return new TransitionTuple(state, record);
		} else if (transition instanceof AbstractNodeCrashTransition) {
			return new TransitionTuple(state, new AbstractNodeStartTransition(numNode));
		} else if (transition instanceof AbstractNodeStartTransition) {
			return new TransitionTuple(state, new AbstractNodeStartTransition(numNode));
		} else {
			return null;
		}
	}

	public static TransitionTuple getRealTransitionTuple(ModelCheckingServerAbstract mc, TransitionTuple t) {
		if (t.transition instanceof PacketSendTransition) {
			return new TransitionTuple(t.state,
					new PacketSendTransition(mc, ((PacketSendTransition) t.transition).getPacket()));
		} else if (t.transition instanceof NodeCrashTransition) {
			NodeCrashTransition temp = (NodeCrashTransition) t.transition;
			NodeCrashTransition realTransition = new NodeCrashTransition(mc, temp.getId());
			realTransition.setVectorClock(temp.getVectorClock());
			return new TransitionTuple(t.state, realTransition);
		} else if (t.transition instanceof NodeStartTransition) {
			NodeStartTransition temp = (NodeStartTransition) t.transition;
			NodeStartTransition realTransition = new NodeStartTransition(mc, temp.getId());
			realTransition.setVectorClock(temp.getVectorClock());
			return new TransitionTuple(t.state, realTransition);
		} else if (t.transition instanceof AbstractNodeCrashTransition) {
			AbstractNodeCrashTransition u = new AbstractNodeCrashTransition(mc);
			u.id = ((AbstractNodeStartTransition) t.transition).getId();
			return new TransitionTuple(t.state, u);
		} else if (t.transition instanceof AbstractNodeStartTransition) {
			AbstractNodeStartTransition u = new AbstractNodeStartTransition(mc);
			u.id = ((AbstractNodeStartTransition) t.transition).getId();
			return new TransitionTuple(t.state, u);
		} else {
			return null;
		}
	}
}
