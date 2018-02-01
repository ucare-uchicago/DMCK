package edu.uchicago.cs.ucare.dmck.cassandra;

import java.util.HashMap;

import org.apache.log4j.Logger;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.EvaluationModelChecker;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.WorkloadDriver;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.LocalState;

public class CassSAMC extends EvaluationModelChecker {

  private static final Logger LOG = Logger.getLogger(CassSAMC.class);

  public CassSAMC(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash, int numReboot,
      String globalStatePathDir, String packetRecordDir, String cacheDir, WorkloadDriver workloadDriver,
      String ipcDir) {
    super(dmckName, fileWatcher, numNode, numCrash, numReboot, globalStatePathDir, packetRecordDir, cacheDir,
        workloadDriver, ipcDir);
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean isLMDependent(LocalState state, Event e1, Event e2) {
    String e1Verb = (String) e1.getValue("verb");
    String e2Verb = (String) e2.getValue("verb");
    HashMap<String, String> e1Payload = (HashMap<String, String>) e1.getValue("payload");
    HashMap<String, String> e2Payload = (HashMap<String, String>) e2.getValue("payload");
    String e1Key = e1Payload.get("key");
    String e2Key = e2Payload.get("key");
    String e1Ballot = e1Payload.get("ballot");
    String e2Ballot = e2Payload.get("ballot");

    // SAMC1 : LMI-Increment
    if (isPaxosResponse(e1Verb) && e1Verb.equals(e2Verb) && (e1.getToId() == e2.getToId())
        && hasSameContent(e1Payload, e2Payload)) {
      recordPolicyEffectiveness("lmi");
      return false;
    }

    // SAMC2 : Msg-alwaysdis
    if (reductionAlgorithms.contains("msg_always_dis")) {
      if (e1Verb.equals("PAXOS_COMMIT_RESPONSE")
          || e2Verb.equals("PAXOS_COMMIT_RESPONSE") && (e1.getToId() == e2.getToId())) {
        recordPolicyEffectiveness("msgAlwaysDis");
        return false;
      }
    }

    // SAMC3 : Msg-alwaysdis
    if (reductionAlgorithms.contains("msg_always_dis")) {
      if (isPaxosCommand(e1Verb) && e1Verb.equals(e2Verb) && (e1.getToId() == e2.getToId()) && (e1Key == e2Key)
          && (e1Ballot.compareTo(e2Ballot) > 0)) {
        recordPolicyEffectiveness("msgAlwaysDis");
        return false;
      }
    }

    // SAMC4 : LMI-discard && Msg-alwaysdis
    if (reductionAlgorithms.contains("msg_always_dis")) {
      if (e1Verb.equals("PAXOS_PREPARE_RESPONSE") && e2Verb.equals("PAXOS_PREPARE_RESPONSE")
          && (e1.getToId() == e2.getToId()) && (e1Key == e2Key)) {
        String e1Response = e1Payload.get("response");
        String e2Response = e2Payload.get("response");
        if (e1Response.equals("false") || e2Response.equals("false")) {
          recordPolicyEffectiveness("msgAlwaysDis");
          return false;
        }
      }
    }

    // SAMC5 : Msg-ww-disjoint
    if (reductionAlgorithms.contains("msg_ww_disjoint")) {
      if (e1Verb.equals("PAXOS_PREPARE") && e2Verb.equals("PAXOS_PREPARE_RESPONSE") && (e1.getToId() == e2.getToId())) {
        recordPolicyEffectiveness("msgWWDisjoint");
        return false;
      }
      if (e1Verb.equals("PAXOS_PREPARE_RESPONSE") && e2Verb.equals("PAXOS_PREPARE") && (e1.getToId() == e2.getToId())) {
        recordPolicyEffectiveness("msgWWDisjoint");
        return false;
      }
      if (e1Verb.equals("PAXOS_PROPOSE") && e2Verb.equals("PAXOS_PROPOSE_RESPONSE") && (e1.getToId() == e2.getToId())) {
        recordPolicyEffectiveness("msgWWDisjoint");
        return false;
      }
      if (e1Verb.equals("PAXOS_PROPOSE_RESPONSE") && e2Verb.equals("PAXOS_PROPOSE") && (e1.getToId() == e2.getToId())) {
        recordPolicyEffectiveness("msgWWDisjoint");
        return false;
      }
      if (e1Verb.equals("PAXOS_COMMIT") && e2Verb.equals("PAXOS_PROPOSE_RESPONSE") && (e1.getToId() == e2.getToId())) {
        recordPolicyEffectiveness("msgWWDisjoint");
        return false;
      }
      if (e1Verb.equals("PAXOS_PROPOSE_RESPONSE") && e2Verb.equals("PAXOS_COMMIT") && (e1.getToId() == e2.getToId())) {
        recordPolicyEffectiveness("msgWWDisjoint");
        return false;
      }
    }

    // SAMC6 : Msg-ww-disjoint
    if (reductionAlgorithms.contains("msg_ww_disjoint")) {
      if (e1Verb.equals("PAXOS_PREPARE_RESPONSE") && e2Verb.equals("PAXOS_PROPOSE_RESPONSE")
          && (e1.getToId() == e2.getToId())) {
        recordPolicyEffectiveness("msgWWDisjoint");
        return false;
      }
      if (e1Verb.equals("PAXOS_PROPOSE_RESPONSE") && e2Verb.equals("PAXOS_PREPARE_RESPONSE")
          && (e1.getToId() == e2.getToId())) {
        recordPolicyEffectiveness("msgWWDisjoint");
        return false;
      }
    }

    // SAMC7 : LMI && Msg-alwaysdis
    if (reductionAlgorithms.contains("msg_always_dis")) {
      if (e1Verb.equals("PAXOS_PROPOSE_RESPONSE") && e2Verb.equals("PAXOS_PROPOSE_RESPONSE")
          && (e1.getToId() == e2.getToId())) {
        recordPolicyEffectiveness("msgAlwaysDis");
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean isCMDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, Event msg,
      NodeCrashTransition crash) {
    return true;
  }

  @Override
  public boolean isCRSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, Transition event) {
    return true;
  }

  @Override
  public boolean isRSSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, Transition event) {
    return true;
  }

  @Override
  public boolean isCCDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, NodeCrashTransition crash1,
      NodeCrashTransition crash2) {
    return true;
  }

  @Override
  public boolean isNotDiscardReorder(boolean[] wasNodeOnline, LocalState[] wasLocalState,
      NodeOperationTransition crashOrRebootEvent, Event msg) {
    return true;
  }

  private boolean hasSameContent(HashMap<String, String> p1, HashMap<String, String> p2) {
    for (String key : p1.keySet()) {
      if (!p1.get(key).equals(p2.get(key)))
        return false;
    }
    return true;
  }

  private boolean isPaxosCommand(String verb) {
    return verb.equals("PAXOS_PREPARE") || verb.equals("PAXOS_PROPOSE") || verb.equals("PAXOS_COMMIT");
  }

  private boolean isPaxosResponse(String verb) {
    return verb.equals("PAXOS_PREPARE_RESPONSE") || verb.equals("PAXOS_PROPOSE_RESPONSE")
        || verb.equals("PAXOS_COMMIT_RESPONSE");
  }

}
