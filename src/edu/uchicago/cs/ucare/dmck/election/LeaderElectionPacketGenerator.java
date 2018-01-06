package edu.uchicago.cs.ucare.dmck.election;

import java.util.HashMap;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.example.election.ElectionMessage;

public class LeaderElectionPacketGenerator {

	private HashMap<Integer, Integer> packetCount;

	public LeaderElectionPacketGenerator() {
		packetCount = new HashMap<Integer, Integer>();
	}

	public Event createNewLeaderElectionPacket(int fromId, int toId, int role, int leader) {
		int hash = leaderElectionHashCodeWithoutId(fromId, toId, role, leader);
		Integer count = packetCount.get(hash);
		if (count == null) {
			count = 0;
		}
		++count;
		int id = 31 * hash + count;
		packetCount.put(hash, count);
		Event event = new Event(id);
		event.addKeyValue(Event.FROM_ID, fromId);
		event.addKeyValue(Event.TO_ID, toId);
		event.addKeyValue("role", role);
		event.addKeyValue("leader", leader);
		return event;
	}

	private static int leaderElectionHashCodeWithoutId(int fromId, int toId, int role, int leader) {
		final int prime = 31;
		int result = 1;
		result = prime * result + fromId;
		result = prime * result + toId;
		result = prime * result + role;
		result = prime * result + leader;
		return result;
	}

	public int getHash(ElectionMessage msg, int toId) {
		int hash = leaderElectionHashCodeWithoutId(msg.getSender(), toId, msg.getRole(), msg.getLeader());
		Integer count = packetCount.get(hash);
		if (count == null) {
			count = 0;
		}
		++count;
		int id = 31 * hash + count;
		packetCount.put(hash, count);
		return id;
	}

}
