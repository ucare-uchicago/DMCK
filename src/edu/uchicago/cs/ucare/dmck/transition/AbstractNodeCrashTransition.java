package edu.uchicago.cs.ucare.dmck.transition;

import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

@SuppressWarnings("serial")
public class AbstractNodeCrashTransition extends AbstractNodeOperationTransition {

	private final static Logger LOG = LoggerFactory.getLogger(AbstractNodeCrashTransition.class);

	public AbstractNodeCrashTransition(ModelCheckingServerAbstract checker) {
		super(checker);
	}

	public AbstractNodeCrashTransition(int numNode) {
		super(numNode);
	}

	public AbstractNodeCrashTransition(ModelCheckingServerAbstract checker, boolean isRandom) {
		super(checker, isRandom);
	}

	@Override
	public boolean apply() {
		NodeCrashTransition t = getRealNodeOperationTransition();
		if (t == null) {
			return false;
		}
		id = t.getId();
		return t.apply();
	}

	@Override
	public long getTransitionId() {
		return 101;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof AbstractNodeCrashTransition;
	}

	@Override
	public int hashCode() {
		return 101;
	}

	public NodeCrashTransition getRealNodeOperationTransition() {
		if (isRandom) {
			LinkedList<NodeOperationTransition> allPossible = getAllRealNodeOperationTransitions(checker.isNodeOnline);
			if (allPossible.isEmpty()) {
				LOG.debug("Try to execute crash node event, but currently there is no online node");
				return null;
			}
			int i = RANDOM.nextInt(allPossible.size());
			return (NodeCrashTransition) allPossible.get(i);
		} else {
			for (int i = 0; i < checker.numNode; ++i) {
				if (checker.isNodeOnline(i)) {
					NodeCrashTransition realCrash = new NodeCrashTransition(checker, i);
					realCrash.setVectorClock(getPossibleVectorClock(i));
					return realCrash;
				}
			}
			LOG.debug("Try to execute crash node event, but currently there is no online node");
			return null;
		}
	}

	@Override
	public NodeOperationTransition getRealNodeOperationTransition(int suggestExecuteNodeId) {
		if (checker.isNodeOnline(suggestExecuteNodeId)) {
			NodeCrashTransition realCrash = new NodeCrashTransition(checker, suggestExecuteNodeId);
			realCrash.setVectorClock(getPossibleVectorClock(suggestExecuteNodeId));
			return realCrash;
		}
		LOG.debug("Try to execute crash node event based on suggestion, but currently suggested node is not online");
		return null;
	}

	@Override
	public LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions(boolean[] onlineStatus) {
		LinkedList<NodeOperationTransition> result = new LinkedList<NodeOperationTransition>();
		for (int i = 0; i < onlineStatus.length; ++i) {
			if (onlineStatus[i]) {
				NodeCrashTransition realCrash = new NodeCrashTransition(checker, i);
				realCrash.setVectorClock(getPossibleVectorClock(i));
				result.add(realCrash);
			}
		}
		return result;
	}

	@Override
	public LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions() {
		LinkedList<NodeOperationTransition> result = new LinkedList<NodeOperationTransition>();
		for (int i = 0; i < checker.numNode; ++i) {
			NodeCrashTransition realCrash = new NodeCrashTransition(checker, i);
			realCrash.setVectorClock(getPossibleVectorClock(i));
			result.add(realCrash);
		}
		return result;
	}

	public String toString() {
		return "abstract_crash";
	}

	@Override
	public String toStringForFutureExecution() {
		return "abstract_crash";
	}

}
