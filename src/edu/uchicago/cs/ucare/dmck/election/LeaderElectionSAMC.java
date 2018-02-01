package edu.uchicago.cs.ucare.dmck.election;

import java.util.HashMap;
import java.util.Map;
import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.EvaluationModelChecker;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.WorkloadDriver;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.LocalState;
import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;

public class LeaderElectionSAMC extends EvaluationModelChecker {

  public LeaderElectionSAMC(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash,
      int numReboot, String globalStatePathDir, String packetRecordDir, String workingDir,
      WorkloadDriver workloadDriver, String ipcDir) {
    super(dmckName, fileWatcher, numNode, numCrash, numReboot, globalStatePathDir, packetRecordDir,
        workingDir, workloadDriver, ipcDir);
  }

  public boolean isLMDependent(LocalState state, Event e1, Event e2) {
    if ((int) state.getValue("role") == LeaderElectionMain.LOOKING
        && (((int) e1.getValue("leader") == LeaderElectionMain.LOOKING
            && (int) state.getValue("leader") < (int) e1.getValue("leader"))
            || ((int) e2.getValue("leader") == LeaderElectionMain.LOOKING
                && (int) state.getValue("leader") < (int) e2.getValue("leader")))) {
      return true;
    }
    if ((int) state.getValue("role") == LeaderElectionMain.LOOKING
        && ((int) e1.getValue("leader") == LeaderElectionMain.LOOKING
            || (int) e2.getValue("leader") == LeaderElectionMain.LOOKING)
        && isFinished(state)) {
      return true;
    }

    /*
     * OLD SAMC if ((int)state.getValue("role") == LeaderElectionMain.LOOKING) { if ((int)
     * state.getValue("leader") < (int)e1.getValue("leader") || (int) state.getValue("leader") <
     * (int)e2.getValue("leader")) { return true; } else if (isFinished(state)) { return true; } }
     */
    return false;
  }

  public boolean isFinished(LocalState state) {
    int totalNode = this.numNode;
    Map<Integer, Integer> count = new HashMap<Integer, Integer>();
    String[] electionTable = state.getValue("electionTable").toString().split(",");
    for (String row : electionTable) {
      int electedLeader = Integer.parseInt(row.split(":")[1]);
      count.put(electedLeader, count.containsKey(electedLeader) ? count.get(electedLeader) + 1 : 1);
    }
    for (Integer electedLeader : count.keySet()) {
      int totalElect = count.get(electedLeader);
      if (totalElect > totalNode / 2) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isCMDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, Event msg,
      NodeCrashTransition crash) {
    return true;
  }

  @Override
  public boolean isCCDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState,
      NodeCrashTransition crash1, NodeCrashTransition crash2) {
    return true;
  }

  @Override
  public boolean isCRSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalstate,
      Transition event) {
    return true;
  }

  @Override
  public boolean isRSSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState,
      Transition event) {
    return true;
  }

  @Override
  public boolean isNotDiscardReorder(boolean[] wasNodeOnline, LocalState[] wasLocalState,
      NodeOperationTransition crashOrRebootEvent, Event msg) {
    return true;
  }

}
