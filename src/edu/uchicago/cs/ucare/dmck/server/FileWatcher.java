package edu.uchicago.cs.ucare.dmck.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.uchicago.cs.ucare.dmck.event.Event;

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
    // Performance evaluation: record last time the DMCK receives a new event or a
    // new state.
    dmck.lastTimeNewEventOrStateUpdate = new Timestamp(System.currentTimeMillis());

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

      Runtime.getRuntime()
          .exec("mv " + ipcDir + "/new/" + filename + " " + ipcDir + "/ack/" + filename);
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

  // Sequencer Module
  public void enableEvent(Event packet) {
    if (dmck.useSequencer) {
      sequencerEnablingSignal(packet);
    } else {
      commonEnablingSignal(packet);
    }
  }

  public void commonEnablingSignal(Event packet) {
    try {
      PrintWriter writer =
          new PrintWriter(ipcDir + "/new/" + packet.getValue(Event.FILENAME), "UTF-8");
      writer.println("eventId=" + packet.getId());
      writer.println("execute=true");
      writer.close();

      LOG.info("Enable event with ID : " + packet.getId());

      Runtime.getRuntime().exec("mv " + ipcDir + "/new/" + packet.getValue(Event.FILENAME) + " "
          + ipcDir + "/ack/" + packet.getValue(Event.FILENAME));
    } catch (Exception e) {
      LOG.error("Error when enabling event in common way=" + packet.toString());
    }
  }

  protected abstract void sequencerEnablingSignal(Event packet);

}
