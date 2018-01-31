package edu.uchicago.cs.ucare.dmck.kudu;

import edu.uchicago.cs.ucare.dmck.server.WorkloadDriver;

public class KuduWorkloadDriver extends WorkloadDriver {

  public KuduWorkloadDriver(int numNode, String workingDir, String ipcDir, String samcDir, String targetSysDir) {
    super(numNode, workingDir, ipcDir, samcDir, targetSysDir);
  }

  @Override
  public void startWorkload() {
    try {
      LOG.debug("Start Kudu Workload");
      Runtime.getRuntime().exec(workingDir + "/startWorkload.sh " + testId);
    } catch (Exception e) {
      LOG.error("Error in executing startWorkload.sh");
    }
  }

  @Override
  public void stopWorkload() {
    // Nothing
  }

}
