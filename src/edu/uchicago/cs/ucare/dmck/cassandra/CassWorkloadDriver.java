package edu.uchicago.cs.ucare.dmck.cassandra;

import edu.uchicago.cs.ucare.dmck.util.WorkloadDriver;

public class CassWorkloadDriver extends WorkloadDriver {

  public CassWorkloadDriver(int numNode, String workingDir, String ipcDir, String samcDir, String targetSysDir) {
    super(numNode, workingDir, ipcDir, samcDir, targetSysDir);
  }

  @Override
  public void startWorkload() {
    try {
      LOG.debug("Start Workload in Cass");
      Runtime.getRuntime().exec(workingDir + "/startWorkload-cqlsh.sh " + testId);
    } catch (Exception e) {
      LOG.error("Error in running startWorkload-cqlsh.sh");
    }
  }

  @Override
  public void stopWorkload() {
    try {
      LOG.debug("Stop Workload in Cass");
      Runtime.getRuntime().exec(workingDir + "/killWorkload-cqlsh.sh");
    } catch (Exception e) {
      LOG.error("Error in running killWorkload-cqlsh.sh");
    }
  }
}
