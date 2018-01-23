package edu.uchicago.cs.ucare.dmck.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FileWatcher implements Runnable {

  protected final static Logger LOG = LoggerFactory.getLogger(FileWatcher.class);

  protected volatile String receivedUpdates;
  protected String ipcDir;
  protected File path;
  protected ModelCheckingServerAbstract dmck;
  protected boolean acceptFile;
  protected ArrayList<String> acceptedFiles;
  protected HashMap<Long, Integer> packetCount;

  public FileWatcher(String sPath, ModelCheckingServerAbstract dmck) {
    ipcDir = sPath;
    path = new File(sPath + "/send");
    resetExecutionPathStats();
    acceptFile = true;
    receivedUpdates = "";

    if (!path.isDirectory()) {
      throw new IllegalArgumentException("Path: " + path + " is not a folder");
    }
  }

  public void run() {
    LOG.debug("FileWatcher is looking after: " + path);

    while (!Thread.interrupted()) {
      try {
        if (path.listFiles().length > 0 && acceptFile) {
          for (File file : path.listFiles()) {
            if (acceptedFiles.contains(file.getName())) {
              continue;
            }
            acceptedFiles.add(file.getName());
            Properties ev = new Properties();
            FileInputStream evInputStream = new FileInputStream(path + "/" + file.getName());
            ev.load(evInputStream);
            proceedEachFile(file.getName(), ev);
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public void setDMCK(ModelCheckingServerAbstract dmck) {
    this.dmck = dmck;
  }

  public void resetExecutionPathStats() {
    clearStats();
  }

  public void clearStats() {
    packetCount = new HashMap<Long, Integer>();
    acceptedFiles = new ArrayList<String>();
  }

  public void setAcceptFile(boolean flag) {
    acceptFile = flag;
  }

  protected synchronized void appendReceivedUpdates(String update) {
    receivedUpdates += update + "\n";
  }

  public synchronized String getReceivedUpdates() {
    String result = receivedUpdates;
    receivedUpdates = "";
    return result;
  }

  public abstract void proceedEachFile(String filename, Properties ev);

  protected void removeProceedFile(String filename) {
    try {
      Runtime.getRuntime().exec("rm " + path + "/" + filename).waitFor();
    } catch (Exception e) {
      LOG.error("FileWatcher failed to remove filename: " + filename + "\n" + e.getMessage());
    }
  }

  // if we would like to directly release an event,
  // use this function instead of offering the packet to SAMC
  public void ignoreEvent(String filename) {
    try {
      PrintWriter writer = new PrintWriter(ipcDir + "/new/" + filename, "UTF-8");
      writer.println("filename=" + filename);
      writer.println("execute=false");
      writer.close();

      Runtime.getRuntime().exec("mv " + ipcDir + "/new/" + filename + " " + ipcDir + "/ack/" + filename);
    } catch (Exception e) {
      LOG.error("Error in ignoring event with file : " + filename);
    }
  }

  protected long commonHashId(long eventId) {
    Integer count = packetCount.get(eventId);
    if (count == null) {
      count = 0;
    }
    count++;
    packetCount.put(eventId, count);
    return 31 * eventId + count;
  }

}