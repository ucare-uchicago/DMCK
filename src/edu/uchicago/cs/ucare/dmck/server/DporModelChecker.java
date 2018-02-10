package edu.uchicago.cs.ucare.dmck.server;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.LocalState;

public class DporModelChecker extends EvaluationModelChecker {

  public DporModelChecker(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash,
      int numReboot, String globalStatePathDir, String packetRecordDir, String cacheDir,
      WorkloadDriver workloadDriver, String ipcDir) {
    super(dmckName, fileWatcher, numNode, numCrash, numReboot, globalStatePathDir, packetRecordDir,
        cacheDir, workloadDriver, ipcDir);
    this.isSAMC = false;
  }

  @Override
  public boolean isLMDependent(LocalState state, Event e1, Event e2) {
    // since we have checked the DPOR condition before, we can return true
    return true;
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
