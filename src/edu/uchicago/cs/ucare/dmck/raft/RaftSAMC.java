package edu.uchicago.cs.ucare.dmck.raft;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.EvaluationModelChecker;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.LocalState;
import edu.uchicago.cs.ucare.dmck.util.WorkloadDriver;

public class RaftSAMC extends EvaluationModelChecker {

	public RaftSAMC(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash, int numReboot,
			String globalStatePathDir, String packetRecordDir, String cacheDir, WorkloadDriver workloadDriver,
			String ipcDir) {
		super(dmckName, fileWatcher, numNode, numCrash, numReboot, globalStatePathDir, packetRecordDir, cacheDir,
				workloadDriver, ipcDir);
	}

	@Override
	public boolean isLMDependent(LocalState state, Event e1, Event e2) {
		// add LMI policies here (initial policy). return true if it needs to be
		// exercised
		if ((int) e1.getValue("term") >= (int) state.getValue("term")
				|| (int) e2.getValue("term") >= (int) state.getValue("term")) {
			return true;
		}
		return false;
	}

	@Override
	public boolean isCMDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, Event msg,
			NodeCrashTransition crash) {
		return true;
	}

	@Override
	public boolean isCCDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, NodeCrashTransition crash1,
			NodeCrashTransition crash2) {
		return true;
	}

	@Override
	public boolean isCRSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalstate, Transition event) {
		return true;
	}

	@Override
	public boolean isRSSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, Transition event) {
		return true;
	}

	@Override
	public boolean isNotDiscardReorder(boolean[] wasNodeOnline, LocalState[] wasLocalState,
			NodeOperationTransition crashOrRebootEvent, Event msg) {
		return true;
	}

}
