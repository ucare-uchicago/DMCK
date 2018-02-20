package edu.uchicago.cs.ucare.dmck.server;

import java.util.LinkedList;
import java.util.ListIterator;
import edu.uchicago.cs.ucare.dmck.transition.Transition;

public class DfsTreeTravelModelChecker extends TreeTravelModelChecker {

  public DfsTreeTravelModelChecker(String interceptorName, FileWatcher fileWatcher, int numNode,
      int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir,
      String workingDir, WorkloadDriver workloadDriver, String ipcDir) {
    super(interceptorName, fileWatcher, numNode, numCrash, numReboot, globalStatePathDir,
        packetRecordDir, workingDir, workloadDriver, ipcDir);
  }

  @Override
  public Transition nextTransition(LinkedList<Transition> transitions) {
    ListIterator<Transition> iter = transitions.listIterator();
    while (iter.hasNext()) {
      Transition transition = iter.next();
      if (!exploredBranchRecorder.isSubtreeBelowChildFinished(transition.getTransitionId())) {
        return transition;
      }
    }
    return null;
  }

}
