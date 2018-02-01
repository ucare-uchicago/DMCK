package edu.uchicago.cs.ucare.dmck.election;

import java.util.Properties;
import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

public class LeaderElectionFileWatcher extends FileWatcher {

  public LeaderElectionFileWatcher(String sPath, ModelCheckingServerAbstract dmck) {
    super(sPath, dmck);
  }

  @Override
  public synchronized void proceedEachFile(String filename, Properties ev) {
    if (filename.startsWith("le-")) {
      int sendNode = Integer.parseInt(ev.getProperty("sendNode"));
      int recvNode = Integer.parseInt(ev.getProperty("recvNode"));
      int role = Integer.parseInt(ev.getProperty("sendRole"));
      int leader = Integer.parseInt(ev.getProperty("leader"));
      long eventId = Long.parseLong(filename.substring(3));
      long hashId = commonHashId(eventId);

      LOG.debug("Process new File " + filename + " : hashId-" + hashId + " sendNode-" + sendNode
          + " sendRole-" + role + " recvNode-" + recvNode + " leader-" + leader);
      appendReceivedUpdates(
          "New Event: filename=" + filename + " sendNode=" + sendNode + " recvNode=" + recvNode
              + " sendRole=" + role + " leader=" + leader + " hashId=" + hashId);

      // create eventPacket and store it to DMCK queue
      Event packet = new Event(hashId);
      packet.addKeyValue(Event.FROM_ID, sendNode);
      packet.addKeyValue(Event.TO_ID, recvNode);
      packet.addKeyValue(Event.FILENAME, filename);
      packet.addKeyValue("role", role);
      packet.addKeyValue("leader", leader);
      packet.setVectorClock(dmck.getVectorClock(sendNode, recvNode));
      dmck.offerPacket(packet);
    } else if (filename.startsWith("u-")) {
      int sendNode = Integer.parseInt(ev.getProperty("sendNode"));
      int role = Integer.parseInt(ev.getProperty("sendRole"));
      int leader = Integer.parseInt(ev.getProperty("leader"));
      String electionTable = ev.getProperty("electionTable");

      LOG.debug("Update state node-" + sendNode + " role: " + role + " leader: " + leader
          + " electionTable: " + electionTable);
      appendReceivedUpdates("New Update: filename=" + filename + " sendNode=" + sendNode
          + " sendRole=" + role + " leader=" + leader + " electionTable=" + electionTable);

      dmck.localStates[sendNode].setKeyValue("role", role);
      dmck.localStates[sendNode].setKeyValue("leader", leader);
      dmck.localStates[sendNode].setKeyValue("electionTable", electionTable);
    }

    removeProceedFile(filename);
  }

  @Override
  protected void sequencerEnablingSignal(Event packet) {
    // Since current DMCK integration with Sample-LE has not supported sequencer
    // yet,
    // DMCK should just use common enabling signal function for now.
    commonEnablingSignal(packet);
  }

}
