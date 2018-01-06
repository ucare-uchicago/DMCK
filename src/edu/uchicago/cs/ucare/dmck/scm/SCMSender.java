package edu.uchicago.cs.ucare.dmck.scm;

import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SCMSender {

	private static final Logger LOG = LoggerFactory.getLogger(SCMSender.class);

	static String ipcDmckDir;
	static String messageLocation;

	static int msgId;
	static int nodeId;
	static int vote;
	static String msgName;

	public static void main(String[] args) throws IOException {

		if (args.length != 3) {
			LOG.error("usage: SCMSender <msgDir> <ipcDmckDir> <nodeId>");
			System.exit(1);
		}

		messageLocation = args[0];
		ipcDmckDir = args[1];
		nodeId = Integer.parseInt(args[2]);
		msgId = getHash(nodeId, 0);
		msgName = "scm-" + msgId;
		vote = nodeId;

		createMessage();
		sendMessage();

	}

	public static void createMessage() {
		interceptMessage(msgId, msgName);
		try {
			PrintWriter writer = new PrintWriter(messageLocation + "/new/" + msgName, "UTF-8");
			writer.println("msgId=" + msgId);
			writer.println("vote=" + vote);
			writer.close();

			LOG.info("sender-" + msgId + " has successfully created its message " + msgName);
		} catch (Exception e) {
			LOG.error("sender-" + msgId + " has not created its message " + msgName);
		}
	}

	public static void sendMessage() {
		try {
			Runtime.getRuntime()
					.exec("mv " + messageLocation + "/new/" + msgName + " " + messageLocation + "/send/" + msgName);
			LOG.info("sender-" + msgId + " has sent its message " + msgName);
		} catch (Exception e) {
			LOG.error("sender-" + msgId + " has not sent its message " + msgName);
		}
	}

	public static void interceptMessage(int msgId, String msgName) {
		try {
			PrintWriter writer = new PrintWriter(ipcDmckDir + "/new/" + msgName, "UTF-8");
			writer.println("msgId=" + msgId);
			writer.println("sendNode=" + nodeId);
			writer.println("recvNode=" + 0);
			writer.println("vote=" + vote);
			writer.close();

			Runtime.getRuntime().exec("mv " + ipcDmckDir + "/new/" + msgName + " " + ipcDmckDir + "/send/" + msgName);

			// wait for dmck signal
			File ackFile = new File(ipcDmckDir + "/ack/" + msgName);
			LOG.info("start waiting for file : " + msgName + " at " + ackFile.getPath());
			while (!ackFile.exists()) {
				// wait
			}

			try {
				// receive dmck signal
				LOG.info("DMCK has enabled this file : " + msgName);
				Runtime.getRuntime().exec("rm " + ipcDmckDir + "/ack/" + msgName);
			} catch (Exception e) {
				LOG.error("ack file failed on file : " + msgName);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static int getHash(int fromId, int toId) {
		final int prime = 31;
		int result = 1;
		result = prime * result + fromId;
		result = prime * result + toId;
		return result;
	}
}