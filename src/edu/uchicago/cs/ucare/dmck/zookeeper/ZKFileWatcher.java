package edu.uchicago.cs.ucare.dmck.zookeeper;

import java.util.HashMap;
import java.util.Properties;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

public class ZKFileWatcher extends FileWatcher {

  private HashMap<Integer, Integer> zkFollowerPortMap;

  public ZKFileWatcher(String sPath, ModelCheckingServerAbstract dmck) {
    super(sPath, dmck);
  }

  @Override
  public void resetExecutionPathStats() {
    clearStats();
    zkFollowerPortMap = new HashMap<Integer, Integer>();
  }

  @Override
  public synchronized void proceedEachFile(String filename, Properties ev) {
    if (filename.startsWith("zkLE-")) {
      int sender = Integer.parseInt(ev.getProperty("sender"));
      int recv = Integer.parseInt(ev.getProperty("recv"));
      if (dmck.isNodeOnline(sender) && dmck.isNodeOnline(recv)) {
        int state = Integer.parseInt(ev.getProperty("state"));
        long leader = Long.parseLong(ev.getProperty("leader"));
        long zxid = Long.parseLong(ev.getProperty("zxid"));
        long epoch = Long.parseLong(ev.getProperty("epoch"));
        int eventType = Integer.parseInt(ev.getProperty("eventType"));
        long eventId = Long.parseLong(ev.getProperty("eventId"));
        long hashId = commonHashId(eventId);

        LOG.debug("DMCK receives ZK LE event with hashId-" + hashId + " sender-" + sender + " recv-" + recv + " state-"
            + state + " leader-" + leader + " zxid-" + zxid + " epoch-" + epoch + " filename-" + filename);
        appendReceivedUpdates("New Event: filename=" + filename + " sender=" + sender + " recv=" + recv + " state="
            + state + " leader=" + leader + " zxid=" + zxid + " epoch=" + epoch + " hashId=" + hashId);

        Event event = new Event(hashId);
        event.addKeyValue(Event.FROM_ID, sender);
        event.addKeyValue(Event.TO_ID, recv);
        event.addKeyValue(Event.FILENAME, filename);
        event.addKeyValue("eventType", eventType);
        event.addKeyValue("state", state);
        event.addKeyValue("leader", leader);
        event.addKeyValue("zxid", zxid);
        event.addKeyValue("epoch", epoch);
        event.setVectorClock(dmck.getVectorClock(sender, recv));

        dmck.offerPacket(event);
      } else {
        ignoreEvent(filename);
      }
    } else if (filename.startsWith("zkSnapshot-")) {
      int sender = Integer.parseInt(ev.getProperty("sender"));
      int recv = Integer.parseInt(ev.getProperty("recv"));
      if (dmck.isNodeOnline(sender) && dmck.isNodeOnline(recv)) {
        int eventType = Integer.parseInt(ev.getProperty("eventType"));
        long eventId = Long.parseLong(ev.getProperty("eventId"));
        long hashId = commonHashId(eventId);

        LOG.debug("DMCK receives ZK Snapshot event with hashId-" + hashId + " sender-" + sender + " recv-" + recv
            + " eventType-" + eventType + " filename-" + filename);
        appendReceivedUpdates("New Event: filename=" + filename + " sender=" + sender + " recv=" + recv + " eventType="
            + eventType + " hashId=" + hashId);

        Event event = new Event(hashId);
        event.addKeyValue(Event.FROM_ID, sender);
        event.addKeyValue(Event.TO_ID, recv);
        event.addKeyValue(Event.FILENAME, filename);
        event.addKeyValue("eventType", eventType);
        event.setVectorClock(dmck.getVectorClock(sender, recv));

        dmck.offerPacket(event);
      } else {
        ignoreEvent(filename);
      }
    } else if (filename.startsWith("zkUpToDate-")) {
      int sender = Integer.parseInt(ev.getProperty("sender"));
      int port = Integer.parseInt(ev.getProperty("port"));
      int recv = mapPortToNodeId(port);
      if (dmck.isNodeOnline(sender) && dmck.isNodeOnline(recv)) {
        int eventType = Integer.parseInt(ev.getProperty("eventType"));
        long eventId = Long.parseLong(ev.getProperty("eventId"));
        long hashId = zkHashIdBasedOnPort(eventId, port);

        LOG.debug("DMCK receives ZK UPTODATE msg with hashId-" + hashId + " sender-" + sender + " recv-" + recv
            + " eventType-" + eventType + " filename-" + filename);
        appendReceivedUpdates("New Event: filename=" + filename + " sender=" + sender + " recv=" + recv + " eventType="
            + eventType + " port=" + port + " hashId=" + hashId);

        Event event = new Event(hashId);
        event.addKeyValue(Event.FROM_ID, sender);
        event.addKeyValue(Event.TO_ID, recv);
        event.addKeyValue(Event.FILENAME, filename);
        event.addKeyValue("eventType", eventType);
        event.setVectorClock(dmck.getVectorClock(sender, recv));

        dmck.offerPacket(event);
      } else {
        ignoreEvent(filename);
      }
    } else if (filename.startsWith("zkZAB-")) {
      int sender = Integer.parseInt(ev.getProperty("sender"));
      int port = Integer.parseInt(ev.getProperty("port"));
      // int recv = mapPortToNodeId(port);
      int recv = sender;
      if (dmck.isNodeOnline(sender) && dmck.isNodeOnline(recv)) {
        int eventType = Integer.parseInt(ev.getProperty("eventType"));
        String key = ev.getProperty("key");
        String value = ev.getProperty("value");
        long eventId = Long.parseLong(ev.getProperty("eventId"));
        long hashId = zkHashIdBasedOnPort(eventId, port);

        LOG.debug(
            "DMCK receives ZK ZAB event with hashId-" + hashId + " sender-" + sender + " recv-" + recv + " eventType-"
                + eventType + " key-" + key + " value-" + value + " port-" + port + " filename-" + filename);
        appendReceivedUpdates("New Event: filename=" + filename + " sender=" + sender + " recv=" + recv + " eventType="
            + eventType + " key=" + key + " value=" + value + " port=" + port + " hashId=" + hashId);

        Event event = new Event(hashId);
        event.addKeyValue(Event.FROM_ID, sender);
        event.addKeyValue(Event.TO_ID, recv);
        event.addKeyValue(Event.FILENAME, filename);
        event.addKeyValue("eventType", eventType);
        event.addKeyValue("key", key);
        event.addKeyValue("value", value);
        event.addKeyValue("port", port);
        event.setVectorClock(dmck.getVectorClock(sender, recv));

        dmck.offerLocalEvent(event);
      } else {
        ignoreEvent(filename);
      }
    } else if (filename.startsWith("zkDiskWrite-")) {
      int nodeId = Integer.parseInt(ev.getProperty("nodeId"));
      if (dmck.isNodeOnline(nodeId)) {
        int eventType = Integer.parseInt(ev.getProperty("eventType"));
        long eventId = Long.parseLong(ev.getProperty("eventId"));
        long hashId = commonHashId(eventId);

        LOG.debug("DMCK receives ZK Disk Write event with hashId-" + hashId + " node-" + nodeId + " eventType-"
            + eventType + " filename-" + filename);
        appendReceivedUpdates(
            "New Event: filename=" + filename + " nodeId=" + nodeId + " eventType=" + eventType + " hashId=" + hashId);

        Event event = new Event(hashId);
        event.addKeyValue(Event.FROM_ID, nodeId);
        event.addKeyValue(Event.TO_ID, nodeId);
        event.addKeyValue(Event.FILENAME, filename);
        event.setVectorClock(dmck.getVectorClock(nodeId, nodeId));

        dmck.offerLocalEvent(event);
      } else {
        ignoreEvent(filename);
      }
    } else if (filename.startsWith("zkSnapshotLocalEvent-")) {
      int nodeId = Integer.parseInt(ev.getProperty("nodeId"));
      if (dmck.isNodeOnline(nodeId)) {
        long hashId = commonHashId(nodeId);

        LOG.debug("DMCK receives ZK Snapshot Local Event node-" + nodeId + " filename-" + filename);
        appendReceivedUpdates("New Event: filename=" + filename + " nodeId=" + nodeId + " hashId=" + hashId);

        Event event = new Event(hashId);
        event.addKeyValue(Event.FROM_ID, nodeId);
        event.addKeyValue(Event.TO_ID, nodeId);
        event.addKeyValue(Event.FILENAME, filename);
        event.setVectorClock(dmck.getVectorClock(nodeId, nodeId));

        dmck.offerLocalEvent(event);
      } else {
        ignoreEvent(filename);
      }
    } else if (filename.startsWith("zkNotifyFollowerPort-")) {
      int nodeId = Integer.parseInt(ev.getProperty("nodeId"));
      int port = Integer.parseInt(ev.getProperty("port"));

      LOG.debug("Update Follower port state node-" + nodeId + " port-" + port);
      appendReceivedUpdates("New Update: filename=" + filename + " nodeId=" + nodeId + " port=" + port);

      zkFollowerPortMap.put(nodeId, port);
    } else if (filename.startsWith("zkPersistentState-")) {
      int nodeId = Integer.parseInt(ev.getProperty("nodeId"));
      long hashcode = Long.parseLong(ev.getProperty("hashcode"));

      String persistentState = (String) dmck.localStates[nodeId].getValue("persistentState");
      if (persistentState == null) {
        persistentState = Long.toString(hashcode);
      } else {
        persistentState += "," + hashcode;
      }

      LOG.debug("Update persistent state at node-" + nodeId + " persistentState-" + persistentState);
      appendReceivedUpdates(
          "New Update: filename=" + filename + " nodeId=" + nodeId + " persistentState=" + persistentState);

      dmck.localStates[nodeId].setKeyValue("persistentState", persistentState);
    } else if (filename.startsWith("zkWorkloadUpdate-")) {

      dmck.numQueueInitWorkload--;

      LOG.debug("DMCK receives ZK Workload Accomplishment Update filename-" + filename + " totalWorkloadLeft-"
          + dmck.numQueueInitWorkload);
      appendReceivedUpdates(
          "New Workload: filename=" + filename + " currentQueueClientCreateZNode=" + dmck.numQueueInitWorkload);

    } else if (filename.startsWith("zkUpdate-")) {
      int sender = Integer.parseInt(ev.getProperty("sender"));
      int state = Integer.parseInt(ev.getProperty("state"));
      long proposedLeader = Long.parseLong(ev.getProperty("proposedLeader"));
      long proposedZxid = Long.parseLong(ev.getProperty("proposedZxid"));
      long logicalclock = Long.parseLong(ev.getProperty("logicalclock"));

      LOG.debug("Update state node-" + sender + " state-" + state + " proposedLeader-" + proposedLeader
          + " proposedZxid-" + proposedZxid + " logicalclock-" + logicalclock);
      appendReceivedUpdates("New Update: filename=" + filename + " nodeId=" + sender + " state=" + state
          + " proposedLeader=" + proposedLeader + " proposedZxid=" + proposedZxid + " logicalclock=" + logicalclock);

      dmck.localStates[sender].setKeyValue("state", state);
      dmck.localStates[sender].setKeyValue("proposedLeader", proposedLeader);
      dmck.localStates[sender].setKeyValue("proposedZxid", proposedZxid);
      dmck.localStates[sender].setKeyValue("logicalclock", logicalclock);
    } else if (filename.startsWith("zkVotesTable-")) {
      int sender = Integer.parseInt(ev.getProperty("sender"));
      String votesTable = ev.getProperty("votesTable");

      HashMap<Long, String> votesHash = new HashMap<Long, String>();
      if (votesTable.isEmpty()) {
        votesHash = null;
        LOG.debug("Update state node=" + sender + " votesTable=null");
        appendReceivedUpdates("New Update: filename=" + filename + " nodeId=" + sender + " votesTable=null");
      } else {
        String[] votes = votesTable.split(";");
        for (String vote : votes) {
          String[] element = vote.split("=");
          votesHash.put(Long.parseLong(element[0]), element[1]);
        }

        LOG.debug("Update state node=" + sender + " votesTable=" + votesHash.toString());
        appendReceivedUpdates(
            "New Update: filename=" + filename + " nodeId=" + sender + " votesTable=" + votesHash.toString());
      }

      if (votesTable.isEmpty()) {
        dmck.localStates[sender].deleteKey("votesTable");
      } else {
        dmck.localStates[sender].setKeyValue("votesTable", votesHash);
      }
    }

    removeProceedFile(filename);

  }

  private int mapPortToNodeId(int port) {
    int nodeId = -1;
    for (int i = 0; i < dmck.numNode; i++) {
      Object p = zkFollowerPortMap.get(i);
      if (p != null && (int) p == port) {
        nodeId = i;
        break;
      }
    }
    return nodeId;
  }

  private long zkHashIdBasedOnPort(long eventId, int port) {
    int nodeId = mapPortToNodeId(port);
    eventId = 31 * eventId + nodeId;
    Integer count = packetCount.get(eventId);
    if (count == null) {
      count = 0;
    }
    count++;
    packetCount.put(eventId, count);
    return 31 * eventId + count;
  }

}
