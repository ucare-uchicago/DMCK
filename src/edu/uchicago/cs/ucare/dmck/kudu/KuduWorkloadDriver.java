package edu.uchicago.cs.ucare.dmck.kudu;

import edu.uchicago.cs.ucare.dmck.server.WorkloadDriver;

public class KuduWorkloadDriver extends WorkloadDriver {

  public KuduWorkloadDriver(int numNode, String workingDir, String ipcDir, String samcDir, String targetSysDir) {
    super(numNode, workingDir, ipcDir, samcDir, targetSysDir);
  }

  @Override
  public void startWorkload() {
    
  }

  @Override
  public void stopWorkload() {
    
  }

}
