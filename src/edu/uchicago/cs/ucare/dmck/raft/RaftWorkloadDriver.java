package edu.uchicago.cs.ucare.dmck.raft;

import edu.uchicago.cs.ucare.dmck.util.WorkloadDriver;

public class RaftWorkloadDriver extends WorkloadDriver {

  private Process[] node;

  public RaftWorkloadDriver(int numNode, String workingDir, String ipcDir, String samcDir, String targetSysDir) {
    super(numNode, workingDir, ipcDir, samcDir, targetSysDir);
    node = new Process[numNode];
  }

  @Override
  public void startNode(int id) {
    try {
      Thread.sleep(200);
      node[id] = Runtime.getRuntime().exec(workingDir + "/startNode.sh " + " " + id + " " + testId);
      LOG.info("Start Node " + id);
    } catch (Exception e) {
      LOG.error("Error in Starting Node " + id);
    }
  }

  @Override
  public void stopNode(int id) {
    try {
      Runtime.getRuntime().exec(workingDir + "/killNode.sh " + id);
      Thread.sleep(100);
      LOG.info("Stop Node " + id);
    } catch (Exception e) {
      LOG.error("Error in Killing Node " + id);
    }
  }

  @Override
  public void stopEnsemble() {
    LOG.info("Stop Ensemble");
    for (int i = 0; i < numNode; i++) {
      try {
        stopNode(i);
        refreshRaftNodeStorage(i);
      } catch (Exception e) {
        LOG.error("Error in stopping ensemble");
      }
    }
  }

  @Override
  public void startWorkload() {
    // nothing
  }

  @Override
  public void stopWorkload() {
    // nothing
  }

  private void refreshRaftNodeStorage(int id) {
    try {
      Runtime.getRuntime().exec(workingDir + "/refreshStorageNode.sh " + id);
    } catch (Exception e) {
      LOG.error("Error in refresh raft storage dir in node " + id);
    }
  }

  public void raftSnapshot(int leaderId) {
    try {
      LOG.debug("Executing Snapshot in node-" + leaderId);
      Runtime.getRuntime().exec(workingDir + "/snapshot.sh " + leaderId + " " + testId);
    } catch (Exception e) {
      LOG.error("Error in Take Snapshot of " + leaderId);
    }
  }
}
