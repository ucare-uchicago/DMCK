package edu.uchicago.cs.ucare.dmck.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class WorkloadDriver {

  protected final static Logger LOG = LoggerFactory.getLogger(WorkloadDriver.class);

  protected int numNode;
  protected String workingDir;
  protected String ipcDir;
  protected String samcDir;
  protected String targetSysDir;
  protected int testId;

  public SpecVerifier verifier;

  public WorkloadDriver(int numNode, String workingDir, String ipcDir, String samcDir, String targetSysDir) {
    this.numNode = numNode;
    this.workingDir = workingDir;
    this.ipcDir = ipcDir;
    this.samcDir = samcDir;
    this.targetSysDir = targetSysDir;
    testId = 0;
  }

  public WorkloadDriver(int numNode, String workingDir, SpecVerifier verifier) {
    this.numNode = numNode;
    this.workingDir = workingDir;
    this.verifier = verifier;
  }

  public void startNode(int id) {
    try {
      Runtime.getRuntime().exec(workingDir + "/startNode.sh " + id + " " + testId);
      Thread.sleep(50);
      LOG.info("Start Node-" + id);
    } catch (Exception e) {
      LOG.error("Error in Starting Node " + id);
    }
  }

  public void stopNode(int id) {
    try {
      Runtime.getRuntime().exec(workingDir + "/killNode.sh " + id + " " + testId);
      Thread.sleep(20);
      LOG.info("Stop Node-" + id);
    } catch (Exception e) {
      LOG.error("Error in Killing Node " + id);
    }
  }

  public void startEnsemble() {
    LOG.debug("Start Ensemble");
    for (int i = 0; i < numNode; ++i) {
      try {
        startNode(i);
      } catch (Exception e) {
        LOG.error("Error in starting ensemble");
      }
    }
  }

  public void stopEnsemble() {
    LOG.debug("Stop Ensemble");
    for (int i = 0; i < numNode; i++) {
      try {
        stopNode(i);
      } catch (Exception e) {
        LOG.error("Error in stopping ensemble");
      }
    }
  }

  public void clearIPCDir() {
    try {
      Runtime.getRuntime().exec(new String[] { "sh", "-c", "rm " + ipcDir + "/new/*" });
      Runtime.getRuntime().exec(new String[] { "sh", "-c", "rm " + ipcDir + "/send/*" });
      Runtime.getRuntime().exec(new String[] { "sh", "-c", "rm " + ipcDir + "/ack/*" });
      LOG.debug("[DEBUG] Remove all files in ipc directory");
    } catch (Exception e) {
      LOG.error("Error in clear IPC Dir");
    }
  }

  public void resetTest(int testId) {
    clearIPCDir();
    this.testId = testId;
    try {
      Runtime.getRuntime().exec(workingDir + "/resettest " + this.testId).waitFor();
    } catch (Exception e) {
      LOG.error("Error in cexecuting resettest script");
    }
  }

  public abstract void startWorkload();

  public abstract void stopWorkload();

  public void setVerifier(SpecVerifier verifier) {
    this.verifier = verifier;
  }

}
