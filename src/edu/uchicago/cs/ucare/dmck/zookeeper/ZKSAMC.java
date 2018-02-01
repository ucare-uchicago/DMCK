package edu.uchicago.cs.ucare.dmck.zookeeper;

import java.util.HashMap;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.EvaluationModelChecker;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.WorkloadDriver;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.LocalState;

public class ZKSAMC extends EvaluationModelChecker {

  public ZKSAMC(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash, int numReboot,
      String globalStatePathDir, String packetRecordDir, String cacheDir, WorkloadDriver workloadDriver,
      String ipcDir) {
    super(dmckName, fileWatcher, numNode, numCrash, numReboot, globalStatePathDir, packetRecordDir, cacheDir,
        workloadDriver, ipcDir);
  }

  @Override
  public boolean isLMDependent(LocalState state, Event e1, Event e2) {
    // only if both msgs are LeaderElection msgs
    if (e1.getFromId() != e1.getToId() && e2.getFromId() != e2.getToId() && e1.getValue("epoch") != null
        && e2.getValue("epoch") != null) {
      if (isDiscardMsg(state, e1) && isDiscardMsg(state, e2)) {
        recordPolicyEffectiveness("lmi");
        return false;
      }
    }

    // if one msg is zkUpToDate and the other msg is zkLE, do not reorder
    if (reductionAlgorithms.contains("msg_ww_disjoint")) {
      if ((e1.getValue("filename").toString().startsWith("zkUpToDate")
          && e2.getValue("filename").toString().startsWith("zkLE"))
          || (e2.getValue("filename").toString().startsWith("zkUpToDate")
              && e1.getValue("filename").toString().startsWith("zkLE"))) {
        recordPolicyEffectiveness("msgWWDisjoint");
        return false;
      }
    }

    // if one of it is msg and the other is local disk write event
    if (reductionAlgorithms.contains("disk_rw")) {
      if ((e1.getFromId() == e1.getToId() && e2.getFromId() != e2.getToId())
          || (e2.getFromId() == e2.getToId() && e1.getFromId() != e1.getToId())) {
        recordPolicyEffectiveness("diskRW");
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean isCMDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, Event msg,
      NodeCrashTransition crash) {
    int nodesOnline = 0;
    for (boolean wasOnline : wasNodeOnline) {
      if (wasOnline) {
        nodesOnline++;
      }
    }

    // if after the crash event, majority nodes are still alive AND the node
    // that we will crash is a Follower
    if (nodesOnline - 1 > numNode / 2 && (int) wasLocalState[crash.getId()].getValue("state") == 1) {
      recordPolicyEffectiveness("cmi");
      return false;
    }

    return true;
  }

  @Override
  public boolean isCCDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, NodeCrashTransition crash1,
      NodeCrashTransition crash2) {
    if (reductionAlgorithms.contains("crash_2_noimpact")) {
      for (int i = 0; i < numNode; i++) {
        if (wasNodeOnline[i] && i != crash1.getId() && i != crash2.getId()) {
          if ((int) wasLocalState[i].getValue("state") != 0) {
            return true;
          }
        }
      }

      recordPolicyEffectiveness("crash2NoImpact");
      return false;
    } else {
      return true;
    }
  }

  @Override
  public boolean isCRSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, Transition event) {
    NodeCrashTransition crashEvent = (NodeCrashTransition) event;
    if (wasNodeOnline[crashEvent.getId()] && isSymmetric(wasLocalState, crashEvent)) {
      recordPolicyEffectiveness("crs");
      return false;
    }
    return true;
  }

  @Override
  public boolean isRSSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, Transition event) {
    NodeStartTransition rebootEvent = (NodeStartTransition) event;
    if (!wasNodeOnline[rebootEvent.getId()] && isSymmetric(wasLocalState, rebootEvent)) {
      recordPolicyEffectiveness("rss");
      return false;
    }
    return true;
  }

  @Override
  public boolean isNotDiscardReorder(boolean[] wasNodeOnline, LocalState[] wasLocalState,
      NodeOperationTransition crashOrRebootEvent, Event msg) {
    if (reductionAlgorithms.contains("crash_reboot_dis")) {
      if (isDiscardMsg(wasLocalState[msg.getToId()], msg)) {
        recordPolicyEffectiveness("crDis");
        return false;
      }
    }
    return true;
  }

  private boolean isDiscardMsg(LocalState state, Event e) {
    boolean isAssignment = false;
    if (e.getFromId() != e.getToId() && e.getValue("epoch") != null) {
      if ((int) e.getValue("state") == 0 && (int) state.getValue("state") == 0
          && (long) e.getValue("epoch") > (long) state.getValue("logicalclock")) {
        isAssignment = true;
      }

      if ((int) e.getValue("state") == 0 && (int) state.getValue("state") == 0
          && (long) e.getValue("epoch") == (long) state.getValue("logicalclock")
          && ((long) e.getValue("zxid") > (long) state.getValue("proposedZxid")
              || ((long) e.getValue("zxid") == (long) state.getValue("proposedZxid")
                  && (int) e.getFromId() > (int) e.getToId()))) {
        isAssignment = true;
      }

      if ((int) e.getValue("state") == 0 && (int) state.getValue("state") == 0) {
        LocalState tmp = state.clone();
        @SuppressWarnings("unchecked")
        HashMap<Long, String> tmpVotesTable = (HashMap<Long, String>) tmp.getValue("votesTable");
        if (tmpVotesTable == null) {
          tmpVotesTable = new HashMap<Long, String>();
        }
        String newVote = e.getValue("leader") + "," + e.getValue("zxid");
        tmpVotesTable.put(e.getId(), newVote);
        if (tmpVotesTable.size() == numNode) {
          isAssignment = true;
        } else {
          HashMap<String, Integer> majorityVote = new HashMap<String, Integer>();
          for (String value : tmpVotesTable.values()) {
            if (!majorityVote.keySet().contains(value)) {
              majorityVote.put(value, 1);
            } else {
              majorityVote.put(value, majorityVote.get(value) + 1);
            }
          }
          for (int majorityCount : majorityVote.values()) {
            if (majorityCount > numNode / 2) {
              isAssignment = true;
              break;
            }
          }
        }
      }

    } else {
      isAssignment = true;
    }
    return !isAssignment;
  }

}
