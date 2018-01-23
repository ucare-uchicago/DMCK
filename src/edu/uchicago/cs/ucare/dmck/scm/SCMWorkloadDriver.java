package edu.uchicago.cs.ucare.dmck.scm;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

import edu.uchicago.cs.ucare.dmck.util.WorkloadDriver;

public class SCMWorkloadDriver extends WorkloadDriver {

  private String ipcScmDir;

  private Process[] node;
  private Thread consoleWriter;
  private FileOutputStream[] consoleLog;

  public SCMWorkloadDriver(int numNode, String workingDir, String ipcDir, String samcDir, String targetSysDir) {
    super(numNode, workingDir, ipcDir, samcDir, targetSysDir);
    ipcScmDir = ipcDir + "-scm";
    node = new Process[numNode];
    consoleLog = new FileOutputStream[numNode];
    consoleWriter = new Thread(new LogWriter());
    consoleWriter.start();
  }

  @Override
  public void startNode(int id) {
    try {
      // start receiver first
      if (id == 0) {
        node[id] = Runtime.getRuntime()
            .exec(workingDir + "/startSCMReceiver.sh " + ipcScmDir + " " + ipcDir + " " + (numNode - 1) + " " + testId);
        LOG.info("Start Receiver-" + id);
        Thread.sleep(50);
      } else {
        node[id] = Runtime.getRuntime()
            .exec(workingDir + "/startSCMSender.sh " + ipcScmDir + " " + ipcDir + " " + id + " " + testId);
        LOG.info("Start Sender node-" + id);
      }
      Thread.sleep(50);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void stopNode(int id) {
    LOG.info("Kill node-" + id);
    try {
      node[id] = Runtime.getRuntime().exec(workingDir + "/killNode.sh " + id);
    } catch (IOException e) {
      e.printStackTrace();
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

  @Override
  public void resetTest(int testId) {
    clearIPCDir();
    this.testId = testId;
    try {
      Runtime.getRuntime().exec(workingDir + "/resettest " + this.testId).waitFor();
    } catch (Exception e) {
      LOG.error("Error in cexecuting resettest script");
    }
    for (int i = 0; i < numNode; ++i) {
      if (consoleLog[i] != null) {
        try {
          consoleLog[i].close();
        } catch (IOException e) {
          LOG.error("", e);
        }
      }
      try {
        consoleLog[i] = new FileOutputStream(workingDir + "/console/" + this.testId + "/" + i);
      } catch (FileNotFoundException e) {
        LOG.error("", e);
      }
    }
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
              // LOG.debug("", e);
            }
          }
        }
        try {
          Thread.sleep(300);
        } catch (InterruptedException e) {
          LOG.warn("", e);
        }
      }
    }

  }

}