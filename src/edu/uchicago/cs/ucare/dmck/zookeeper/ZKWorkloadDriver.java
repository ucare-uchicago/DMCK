package edu.uchicago.cs.ucare.dmck.zookeeper;

import edu.uchicago.cs.ucare.dmck.server.WorkloadDriver;

public class ZKWorkloadDriver extends WorkloadDriver {

  public ZKWorkloadDriver(int numNode, String workingDir, String ipcDir, String samcDir,
      String targetSysDir) {
    super(numNode, workingDir, ipcDir, samcDir, targetSysDir);
  }

  @Override
  public void startWorkload() {
    try {
      LOG.debug("Start Workload in ZK");
      Runtime.getRuntime().exec(workingDir + "/startWorkload.sh /foo " + testId);
    } catch (Exception e) {
      LOG.error("Error in running startWorkload.sh");
    }
  }

  @Override
  public void stopWorkload() {
    try {
      LOG.debug("Stop Workload in ZK");
      Runtime.getRuntime().exec(workingDir + "/killWorkload.sh");
    } catch (Exception e) {
      LOG.error("Error in running startWorkload.sh");
    }
  }

}
