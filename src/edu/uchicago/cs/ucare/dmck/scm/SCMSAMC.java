package edu.uchicago.cs.ucare.dmck.scm;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.EvaluationModelChecker;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.WorkloadDriver;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.LocalState;

public class SCMSAMC extends EvaluationModelChecker {

  public SCMSAMC(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash, int numReboot,
      String globalStatePathDir, String packetRecordDir, String cacheDir,
      WorkloadDriver workloadDriver, String ipcDir) {
    super(dmckName, fileWatcher, numNode, numCrash, numReboot, globalStatePathDir, packetRecordDir,
        cacheDir, workloadDriver, ipcDir);
  }

  @Override
  public boolean isLMDependent(LocalState state, Event e1, Event e2) {
    // return true, if event need to be reordered
    if ((int) state.getValue("vote") < (int) e1.getValue("vote")
        || (int) state.getValue("vote") < (int) e2.getValue("vote")) {
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
