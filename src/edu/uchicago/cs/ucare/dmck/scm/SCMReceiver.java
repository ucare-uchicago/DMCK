package edu.uchicago.cs.ucare.dmck.scm;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SCMReceiver {

  private static final Logger LOG = LoggerFactory.getLogger(SCMReceiver.class);

  static String ipcDmckDir;
  static String messageLocation;
  static int vote;
  static final int nodeId = 0;

  public static void main(String[] args) throws IOException {

    if (args.length != 3) {
      System.err.println("usage: SCMReceiver <msgDir> <dmckDir> <totalPeerNodes>");
      System.exit(1);
    }

    messageLocation = args[0];
    ipcDmckDir = args[1];
    int peers = Integer.parseInt(args[2]);
    vote = peers + 1;

    LOG.info("Receiver going to start " + peers + " threads");

    updateState();

    for (int i = 1; i <= peers; i++) {
      Receiver receiverThread = new Receiver(i);
      receiverThread.start();
    }

  }

  public static void updateState() {
    PrintWriter updateStateWriter;
    try {
      updateStateWriter = new PrintWriter(ipcDmckDir + "/new/updatescm-receiver", "UTF-8");
      updateStateWriter.println("sendNode=" + nodeId);
      updateStateWriter.println("vote=" + vote);
      updateStateWriter.close();

      // commit msg order update
      Runtime.getRuntime()
          .exec("mv " + ipcDmckDir + "/new/updatescm-receiver " + ipcDmckDir + "/send/updatescm-receiver");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static int getHash(int fromId) {
    final int prime = 31;
    int result = 1;
    result = prime * result + fromId;
    result = prime * result + nodeId;
    return result;
  }

  public static class Receiver extends Thread {

    int fromId;
    String msgName;

    public Receiver(int fromId) {
      this.fromId = fromId;
      this.msgName = "scm-" + getHash(fromId);
    }

    public void getMessage() {
      LOG.info("receiver-" + fromId + " has started.");
      File msgFile = new File(messageLocation + "/send/" + msgName);
      while (!msgFile.exists()) {
        // wait
      }

      try {
        Properties ev = new Properties();
        FileInputStream evInputStream = new FileInputStream(messageLocation + "/send/" + msgName);
        ev.load(evInputStream);
        int senderVote = Integer.parseInt(ev.getProperty("vote"));

        LOG.info("receiver-" + fromId + " has received message " + msgName + " which vote " + senderVote);

        // reaction on sender message
        if (vote < senderVote) {
          vote = senderVote;
          updateState();
        }
      } catch (Exception e) {
        LOG.error("receiver failed to update state to dmck");
      }
    }

    public void deleteMessage() {
      try {
        Runtime.getRuntime().exec("rm " + messageLocation + "/send/" + msgName);
      } catch (Exception e) {
        LOG.error("receiver-" + fromId + " failed to receive message " + msgName);
      }
    }

    @Override
    public void run() {
      LOG.info("Receiver Thread-" + fromId + " started which waits for msg " + msgName);
      getMessage();
      deleteMessage();
    }
  }

}