package edu.uchicago.cs.ucare.dmck.election;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import edu.uchicago.cs.ucare.dmck.util.WorkloadDriver;

public class LeaderElectionWorkloadDriver extends WorkloadDriver {

  Process[] node;
  Thread consoleWriter;
  FileOutputStream[] consoleLog;

  public LeaderElectionWorkloadDriver(int numNode, String workingDir, String ipcDir, String samcDir,
      String targetSysDir) {
    super(numNode, workingDir, ipcDir, samcDir, targetSysDir);
    node = new Process[numNode];
    consoleLog = new FileOutputStream[numNode];
    consoleWriter = new Thread(new LogWriter());
    consoleWriter.start();
  }

  @Override
  public void startNode(int id) {
    try {
      node[id] = Runtime.getRuntime().exec(workingDir + "/startNode.sh " + id + " " + ipcDir);
      LOG.debug("Starts node-" + id);
      Thread.sleep(50);
    } catch (Exception e) {
      LOG.error("Error when DMCK tries to start node " + id);
    }
  }

  @Override
  public void stopNode(int id) {
    try {
      Runtime.getRuntime().exec(workingDir + "/killNode.sh " + id);
      LOG.debug("Stop node-" + id);
      Thread.sleep(50);
    } catch (Exception e) {
      LOG.error("Error when DMCK tries to kill node " + id);
    }
  }

  @Override
  public void resetTest(int testId) {
    clearIPCDir();
    this.testId = testId;
    for (int i = 0; i < numNode; ++i) {
      if (consoleLog[i] != null) {
        try {
          consoleLog[i].close();
        } catch (IOException e) {
          LOG.error("", e);
        }
      }
      try {
        consoleLog[i] = new FileOutputStream(workingDir + "/console/" + i);
      } catch (Exception e) {
        LOG.error("", e);
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

  class LogWriter implements Runnable {

    public void run() {
      byte[] buff = new byte[256];
      while (true) {
        for (int i = 0; i < numNode; ++i) {
          if (node[i] != null) {
            int r = 0;
            InputStream stdout = node[i].getInputStream();
            InputStream stderr = node[i].getErrorStream();
            try {
              while ((r = stdout.read(buff)) != -1) {
                consoleLog[i].write(buff, 0, r);
                consoleLog[i].flush();
              }
              while ((r = stderr.read(buff)) != -1) {
                consoleLog[i].write(buff, 0, r);
                consoleLog[i].flush();
              }
            } catch (IOException e) {
              LOG.warn("Error in writing log");
            }
          }
        }
        try {
          Thread.sleep(300);
        } catch (Exception e) {
          LOG.warn("Error in LogWriter thread sleep");
        }
      }
    }

  }

}
