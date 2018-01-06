package edu.uchicago.cs.ucare.example.election;

import java.nio.ByteBuffer;

public class ElectionMessage {

	public static final int SIZE = Integer.SIZE * 3;

	int sender;
	int role;
	int leader;

	public ElectionMessage(int sender, int role, int leader) {
		this.sender = sender;
		this.role = role;
		this.leader = leader;
	}

	public ElectionMessage(int sender, byte[] content) {
		this.sender = sender;
		setContent(content);
	}

	public int getSender() {
		return sender;
	}

	public void setSender(int sender) {
		this.sender = sender;
	}

	public int getRole() {
		return role;
	}

	public void setRole(int role) {
		this.role = role;
	}

	public int getLeader() {
		return leader;
	}

	public void setLeader(int leader) {
		this.leader = leader;
	}

	public String toString() {
		String roleName = LeaderElectionMain.getRoleName(role);
		return "sender=" + sender + ":role=" + roleName + ":leader=" + leader;
	}

	public byte[] toBytes() {
		ByteBuffer buffer = ByteBuffer.allocate(SIZE);
		buffer.putInt(sender);
		buffer.putInt(role);
		buffer.putInt(leader);
		return buffer.array();
	}

	public void setContent(byte[] content) {
		assert content.length == SIZE;
		ByteBuffer buffer = ByteBuffer.wrap(content);
		sender = buffer.getInt();
		role = buffer.getInt();
		leader = buffer.getInt();
	}

}
