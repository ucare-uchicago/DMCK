package edu.uchicago.cs.ucare.dmck.scm;

import java.util.Properties;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

public class SCMFileWatcher extends FileWatcher {

  public SCMFileWatcher(String sPath, ModelCheckingServerAbstract dmck) {
    super(sPath, dmck);
  }

  @Override
  public synchronized void proceedEachFile(String filename, Properties ev) {
    if (filename.startsWith("scm-")) {
      int sendNode = Integer.parseInt(ev.getProperty("sendNode"));
      long eventId = Long.parseLong(filename.substring(4));
      int recvNode = Integer.parseInt(ev.getProperty("recvNode"));
      int vote = Integer.parseInt(ev.getProperty("vote"));
      long hashId = commonHashId(eventId);

      LOG.info("Receive msg " + filename + " : hashId-" + hashId + " from node-" + sendNode + " to node-" + recvNode
          + " vote-" + vote);
      appendReceivedUpdates("New Event: filename=" + filename + " sendNode=" + sendNode + " recvNode=" + recvNode
          + " vote=" + vote + " eventId=" + eventId);

      Event event = new Event(hashId);
      event.addKeyValue(Event.FROM_ID, sendNode);
      event.addKeyValue(Event.TO_ID, recvNode);
      event.addKeyValue(Event.FILENAME, filename);
      event.addKeyValue("vote", vote);
      event.setVectorClock(dmck.getVectorClock(sendNode, recvNode));
      dmck.offerPacket(event);
    } else if (filename.startsWith("updatescm-")) {
      int vote = Integer.parseInt(ev.getProperty("vote"));

      LOG.info("Update receiver node-0 with vote-" + vote);
      appendReceivedUpdates("New Update: filename=" + filename + " vote=" + vote);

      dmck.localStates[0].setKeyValue("vote", vote);
    }

    removeProceedFile(filename);
  }

  @Override
  protected void sequencerEnablingSignal(Event packet) {
    // Since current DMCK integration with SCM has not supported sequencer yet,
    // DMCK should just use common enabling signal function for now.
    commonEnablingSignal(packet);
  }

}
