package edu.uchicago.cs.ucare.dmck.raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.LocalState;
import edu.uchicago.cs.ucare.dmck.util.SpecVerifier;

public class RaftVerifier extends SpecVerifier {

	private final static Logger LOG = LoggerFactory.getLogger(RaftVerifier.class);

	private static final String HARD_CRASH = "A node experienced hard crash";
	private static final String MORE_THAN_ONE_LEADER = "More than one node is a leader";
	private static final String NO_LEADER = "No leader is in place, but it should be resolved with next LE round. So we can ignore this bug for now.";
	private static final String DIFF_TERM = "There is a leader, but there is a node with different term. This event should be handled by heartbeat message.";

	private int numLeader;
	private int numCrash;
	private boolean diffTerm;
	private String errorType;

	public RaftVerifier() {
		// nothing
	}

	public RaftVerifier(ModelCheckingServerAbstract modelCheckingServer) {
		this.modelCheckingServer = modelCheckingServer;
	}

	@Override
	public boolean verify() {
		updateGlobalState();
		if (numCrash > 0) {
			errorType = HARD_CRASH;
			return false;
		} else if (numLeader < 1) {
			errorType = NO_LEADER;
			return false;
		} else if (numLeader > 1) {
			errorType = MORE_THAN_ONE_LEADER;
			return false;
		} else if (diffTerm) {
			errorType = DIFF_TERM;
			return false;
		}

		return true;
	}

	public boolean verifyNextTransition(Transition transition) {
		updateGlobalState();
		// detect bug #174
		if (numCrash > 0) {
			errorType = HARD_CRASH;
			return false;
		}
		return true;
	}

	@Override
	public String verificationDetail() {
		String result = "";
		for (int node = 0; node < modelCheckingServer.numNode; node++) {
			LocalState state = this.modelCheckingServer.localStates[node];
			result += "(" + node + ":" + state.getRaftStateName() + " with term:" + state.getValue("term") + ") ";
		}

		result += errorType;

		return result;
	}

	private void updateGlobalState() {
		numLeader = 0;
		numCrash = 0;
		diffTerm = false;
		errorType = "";
		int anchorTerm = -1;
		for (int node = 0; node < modelCheckingServer.numNode; node++) {
			try {
				LocalState state = this.modelCheckingServer.localStates[node];
				switch ((int) state.getValue("state")) {
				case 2:
					numLeader++;
					break;
				case 3:
					numCrash++;
					break;
				}
				if (node == 0) {
					anchorTerm = (int) state.getValue("term");
				} else if (node > 0 && anchorTerm != (int) state.getValue("term")) {
					diffTerm = true;
				}

			} catch (Exception e) {
				LOG.warn("[WARN] Global state is null.");
			}
		}
	}
}
