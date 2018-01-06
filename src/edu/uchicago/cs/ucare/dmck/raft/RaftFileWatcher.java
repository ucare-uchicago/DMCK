package edu.uchicago.cs.ucare.dmck.raft;

import java.util.Properties;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

public class RaftFileWatcher extends FileWatcher {

	public RaftFileWatcher(String sPath, ModelCheckingServerAbstract dmck) {
		super(sPath, dmck);
	}

	@Override
	public synchronized void proceedEachFile(String filename, Properties ev) {
		if (filename.startsWith("raft-")) {
			int sendNode = Integer.parseInt(ev.getProperty("sendNode"));
			int recvNode = Integer.parseInt(ev.getProperty("recvNode"));
			int eventMode = Integer.parseInt(ev.getProperty("eventMode"));
			int eventType = Integer.parseInt(ev.getProperty("eventType"));
			String sendNodeState = ev.getProperty("sendNodeState");
			int senderStateInt = Integer.parseInt(ev.getProperty("sendNodeStateInt"));
			long eventId = Long.parseLong(ev.getProperty("eventId"));
			long hashId = commonHashId(eventId);
			int currentTerm = Integer.parseInt(ev.getProperty("currentTerm"));

			Event event = new Event(hashId);
			event.addKeyValue(Event.FROM_ID, sendNode);
			event.addKeyValue(Event.TO_ID, recvNode);
			event.addKeyValue(Event.FILENAME, filename);
			event.addKeyValue("eventMode", eventMode);
			event.addKeyValue("eventType", eventType);
			event.addKeyValue("state", senderStateInt);
			event.addKeyValue("term", currentTerm);
			event.setVectorClock(dmck.getVectorClock(sendNode, recvNode));

			if (eventMode == 0) {
				// timeout control
				if (eventType == 0 && dmck.initTimeoutEnabling[sendNode]
						&& dmck.timeoutEventCounter[sendNode] < dmck.timeoutEventIterations) {
					dmck.timeoutEventCounter[sendNode]++;
					ignoreEvent(filename);
				} else {
					if (!dmck.initTimeoutEnabling[sendNode]) {
						dmck.initTimeoutEnabling[sendNode] = true;
					}

					LOG.debug("DMCK receives raft local event at node-" + sendNode + " hashId-" + hashId + " eventType-"
							+ eventType + " currentTerm-" + currentTerm + " currentState-" + sendNodeState
							+ " filename-" + filename);
					appendReceivedUpdates("New Event: filename=" + filename + " sendNode=" + sendNode + " recvNode="
							+ recvNode + " sendNodeState=" + senderStateInt + " eventMode=" + eventMode + " eventType="
							+ eventType + " currentTerm=" + currentTerm + " hashId=" + hashId);

					dmck.offerLocalEvent(event);
					dmck.timeoutEventCounter[sendNode] = 0;
				}
			} else {
				if (eventMode == 1 && eventType == 2) {
					dmck.timeoutEventCounter[recvNode] = 0;
					dmck.timeoutEventCounter[sendNode] = 0;
					ignoreEvent(filename);
				} else {
					LOG.debug("DMCK receives raft msg event from sendNode-" + sendNode + " recvNode-" + recvNode
							+ " hashId-" + hashId + " eventMode-" + eventMode + " eventType-" + eventType
							+ " currentTerm-" + currentTerm + " currentState-" + sendNodeState + " filename-"
							+ filename);
					appendReceivedUpdates("New Event: filename=" + filename + " sendNode=" + sendNode + " recvNode="
							+ recvNode + " sendNodeState=" + senderStateInt + " eventMode=" + eventMode + " eventType="
							+ eventType + " currentTerm=" + currentTerm + " hashId=" + hashId);

					dmck.offerPacket(event);
				}
			}
		} else if (filename.startsWith("raftUpdate-")) {
			int sendNode = Integer.parseInt(ev.getProperty("sendNode"));
			String sendNodeState = ev.getProperty("sendNodeState");
			int senderStateInt = Integer.parseInt(ev.getProperty("sendNodeStateInt"));
			int currentTerm = Integer.parseInt(ev.getProperty("currentTerm"));

			LOG.info("Update state at node-" + sendNode + " state-" + sendNodeState + " term-" + currentTerm);
			appendReceivedUpdates("New Update: filename=" + filename + " sendNode=" + sendNode + " sendNodeState="
					+ senderStateInt + " currentTerm=" + currentTerm);

			dmck.localStates[sendNode].setKeyValue("state", senderStateInt);
			dmck.localStates[sendNode].setKeyValue("term", currentTerm);
		}

		removeProceedFile(filename);
	}

}
