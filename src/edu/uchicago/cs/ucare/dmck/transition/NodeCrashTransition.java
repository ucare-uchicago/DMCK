package edu.uchicago.cs.ucare.dmck.transition;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

@SuppressWarnings("serial")
public class NodeCrashTransition extends NodeOperationTransition {

	final static Logger LOG = LoggerFactory.getLogger(NodeCrashTransition.class);

	public static final String ACTION = "nodecrash";
	private static final short ACTION_HASH = (short) ACTION.hashCode();

	protected ModelCheckingServerAbstract checker;

	public NodeCrashTransition(ModelCheckingServerAbstract checker, int id) {
		this.checker = checker;
		this.id = id;
	}

	@Override
	public boolean apply() {
		LOG.info("Killing node " + id);
		if (checker.killNode(id, vectorClock)) {
			checker.numCurrentCrash++;
			for (Transition t : checker.currentEnabledTransitions) {
				if (t instanceof AbstractNodeStartTransition) {
					((AbstractNodeStartTransition) t).setPossibleVectorClock(id,
							checker.getVectorClock(id, checker.numNode));
				} else if (t instanceof NodeStartTransition) {
					if (((NodeStartTransition) t).id == id) {
						((NodeStartTransition) t).setVectorClock(checker.getVectorClock(id, checker.numNode));
					}
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public long getTransitionId() {
		final long prime = 31;
		long hash = 1;
		hash = prime * hash + id;
		long tranId = ((long) ACTION_HASH) << 16;
		tranId = tranId | (0x0000FFFF & hash);
		return tranId;
	}

	public String toString() {
		return "nodecrash id=" + id + " " + Arrays.deepToString(vectorClock);
	}

	public int getId() {
		return id;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
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
		NodeCrashTransition other = (NodeCrashTransition) obj;
		if (id != other.id)
			return false;
		return true;
	}

	@Override
	public String toStringForFutureExecution() {
		return "nodecrash id=" + id;
	}

	public NodeCrashTransition clone() {
		return new NodeCrashTransition(this.checker, this.id);
	}

	// protected int[][] vectorClock;
	//
	// @Override
	// public int[][] getVectorClock() {
	// return vectorClock;
	// }
	//
	// public void setVectorClock(int[][] vectorClock) {
	// this.vectorClock = new int[vectorClock.length][vectorClock.length];
	// for (int i = 0; i < vectorClock.length; ++i) {
	// for (int j = 0; j < vectorClock.length; ++j) {
	// this.vectorClock[i][j] = vectorClock[i][j];
	// }
	// }
	// }

}
