package edu.uchicago.cs.ucare.dmck.server;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Random;
import com.almworks.sqlite4java.SQLiteException;

import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.ExploredBranchRecorder;
import edu.uchicago.cs.ucare.dmck.util.SqliteExploredBranchRecorder;
import edu.uchicago.cs.ucare.dmck.util.WorkloadDriver;

public class RandomModelChecker extends ModelCheckingServerAbstract {

	ExploredBranchRecorder exploredBranchRecorder;
	int numCrash;
	int numReboot;
	int currentCrash;
	int currentReboot;
	String stateDir;
	Random random;

	public RandomModelChecker(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash, int numReboot,
			String globalStatePathDir, String packetRecordDir, String workingDir, WorkloadDriver workloadDriver,
			String ipcDir) {
		super(dmckName, fileWatcher, numNode, globalStatePathDir, workingDir, workloadDriver, ipcDir);
		this.numCrash = numCrash;
		this.numReboot = numReboot;
		stateDir = packetRecordDir;
		try {
			exploredBranchRecorder = new SqliteExploredBranchRecorder(packetRecordDir);
		} catch (SQLiteException e) {
			LOG.error("", e);
		}
		random = new Random(System.currentTimeMillis());
		resetTest();
	}

	@Override
	public void resetTest() {
		if (exploredBranchRecorder == null) {
			return;
		}
		super.resetTest();
		currentCrash = 0;
		currentReboot = 0;
		modelChecking = new PathTraversalWorker();
		currentEnabledTransitions = new LinkedList<Transition>();
		exploredBranchRecorder.resetTraversal();
		File waiting = new File(stateDir + "/.waiting");
		try {
			waiting.createNewFile();
		} catch (IOException e) {
			LOG.error("", e);
		}
	}

	@SuppressWarnings("unchecked")
	public Transition nextTransition(LinkedList<Transition> transitions) {
		LinkedList<Transition> cloneQueue = (LinkedList<Transition>) transitions.clone();
		while (cloneQueue.size() > 0) {
			int i = random.nextInt(cloneQueue.size());
			Transition cloneTransition = cloneQueue.remove(i);
			if (!exploredBranchRecorder.isSubtreeBelowChildFinished(cloneTransition.getTransitionId())) {
				for (int j = 0; j < transitions.size(); j++) {
					Transition transition = transitions.get(j);
					if (cloneTransition.getTransitionId() == transition.getTransitionId()) {
						return transitions.remove(j);
					}
				}
			}
		}
		return null;
	}

	protected void recordTestId() {
		exploredBranchRecorder.noteThisNode(".test_id", testId + "");
	}

	protected void adjustCrashAndReboot(LinkedList<Transition> enabledTransitions) {
		int numOnline = 0;
		for (int i = 0; i < numNode; ++i) {
			if (isNodeOnline(i)) {
				numOnline++;
			}
		}
		int numOffline = numNode - numOnline;
		int tmp = numOnline < numCrash - currentCrash ? numOnline : numCrash - currentCrash;
		for (int i = 0; i < tmp; ++i) {
			LOG.debug("DMCK add crash event");
			AbstractNodeCrashTransition crash = new AbstractNodeCrashTransition(this, true);
			for (int j = 0; j < numNode; ++j) {
				crash.setPossibleVectorClock(j, vectorClocks[j][numNode]);
			}
			enabledTransitions.add(crash);
			currentCrash++;
			numOffline++;
		}
		tmp = numOffline < numReboot - currentReboot ? numOffline : numReboot - currentReboot;
		for (int i = 0; i < tmp; ++i) {
			LOG.debug("DMCK add start event");
			AbstractNodeStartTransition start = new AbstractNodeStartTransition(this);
			for (int j = 0; j < numNode; ++j) {
				start.setPossibleVectorClock(j, vectorClocks[j][numNode]);
			}
			enabledTransitions.add(start);
			currentReboot++;
		}
	}

	protected void saveNextTransition(String nextTransition) {
		try {
			localRecordFile.write(("Next Execute: " + nextTransition + "\n").getBytes());
		} catch (IOException e) {
			LOG.error("", e);
		}
	}

	class PathTraversalWorker extends Thread {

		@Override
		public void run() {
			boolean hasWaited = waitEndExploration == 0;
			while (true) {
				executeMidWorkload();
				updateSAMCQueue();
				boolean terminationPoint = checkTerminationPoint(currentEnabledTransitions);
				if (terminationPoint && hasWaited) {
					boolean verifiedResult = verifier.verify();
					String detail = verifier.verificationDetail();
					saveResult(verifiedResult + " ; " + detail + "\n");
					recordTestId();
					exploredBranchRecorder.markBelowSubtreeFinished();
					LOG.info("---- End of Path Execution ----");
					resetTest();
					break;
				} else if (terminationPoint) {
					try {
						if (dmckName.equals("raftModelChecker") && waitForNextLE
								&& waitedForNextLEInDiffTermCounter < 20) {
							Thread.sleep(leaderElectionTimeout);
						} else {
							hasWaited = true;
							LOG.debug("Wait for any long process");
							Thread.sleep(waitEndExploration);
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					continue;
				}
				hasWaited = waitEndExploration == 0;
				Transition transition;
				// take next path based on initial path or current policy
				boolean recordPath = true;
				if (hasDirectedInitialPath && !hasFinishedDirectedInitialPath) {
					transition = nextInitialTransition();
					recordPath = false;
				} else {
					transition = nextTransition(currentEnabledTransitions);
				}
				collectDebugNextTransition(transition);
				if (transition != null) {
					if (recordPath) {
						exploredBranchRecorder.createChild(transition.getTransitionId());
						exploredBranchRecorder.traverseDownTo(transition.getTransitionId());
						exploredBranchRecorder.noteThisNode(".packets", transition.toString(), false);
					}
					try {
						if (transition instanceof AbstractNodeOperationTransition) {
							AbstractNodeOperationTransition nodeOperationTransition = (AbstractNodeOperationTransition) transition;
							transition = ((AbstractNodeOperationTransition) transition)
									.getRealNodeOperationTransition();
							if (transition == null) {
								currentEnabledTransitions.add(nodeOperationTransition);
								continue;
							}
							nodeOperationTransition.id = ((NodeOperationTransition) transition).getId();
						}
						saveNextTransition(transition.toString());
						LOG.info("[NEXT TRANSITION] " + transition.toString());
						if (transition.apply()) {
							pathRecordFile.write((transition.toString() + "\n").getBytes());
							updateGlobalState();
							updateSAMCQueueAfterEventExecution(transition);
						}
					} catch (IOException e) {
						LOG.error("", e);
					}
				} else if (exploredBranchRecorder.getCurrentDepth() == 0) {
					hasFinishedAllExploration = true;
				} else {
					try {
						pathRecordFile.write("duplicated\n".getBytes());
					} catch (IOException e) {
						LOG.error("", e);
					}
					resetTest();
					break;
				}
			}
		}

	}
}
