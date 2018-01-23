package edu.uchicago.cs.ucare.example.election;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.util.LocalState;
import edu.uchicago.cs.ucare.example.election.interposition.LeaderElectionInterposition;

public class LeaderElectionMain {

  private static final Logger LOG = LoggerFactory.getLogger(LeaderElectionMain.class);

  public static final int LOOKING = 0;
  public static final int FOLLOWING = 1;
  public static final int LEADING = 2;

  public static String ipcDir;
  // public static int msgIntercepted;

  public static String getRoleName(int role) {
    String name;
    switch (role) {
      case LeaderElectionMain.LOOKING:
        name = "looking";
        break;
      case LeaderElectionMain.FOLLOWING:
        name = "following";
        break;
      case LeaderElectionMain.LEADING:
        name = "leading";
        break;
      default:
        name = "unknown";
        break;
    }
    return name;
  }

  public static int id;
  public static int role;
  public static int leader;

  public static Map<Integer, InetSocketAddress> nodeMap;
  public static Map<Integer, Sender> senderMap;
  public static Processor processor;

  public static Map<Integer, Integer> electionTable;

  public static void readConfig(String config, String sIpcDir) throws IOException {
    nodeMap = new HashMap<Integer, InetSocketAddress>();
    BufferedReader br = new BufferedReader(new FileReader(config));
    ipcDir = sIpcDir;
    String line;
    while ((line = br.readLine()) != null) {
      String[] tokens = line.trim().split("=");
      assert tokens.length == 2;
      int nodeId = Integer.parseInt(tokens[0]);
      String[] inetSocketAddress = tokens[1].split(":");
      assert inetSocketAddress.length == 2;
      InetSocketAddress nodeAddress = new InetSocketAddress(inetSocketAddress[0],
          Integer.parseInt(inetSocketAddress[1]));
      nodeMap.put(nodeId, nodeAddress);
      LOG.info("node " + nodeId + " is " + nodeAddress);
    }
    LOG.info("Cluster size = " + nodeMap.size());
    br.close();
  }

  public static void work() throws IOException {
    if (LeaderElectionInterposition.SAMC_ENABLED) {
      LeaderElectionInterposition.numNode = nodeMap.size();
      LeaderElectionInterposition.isReading = new boolean[LeaderElectionInterposition.numNode];
      Arrays.fill(LeaderElectionInterposition.isReading, false);
    } else if (ipcDir != "") {
      LeaderElectionInterposition.numNode = nodeMap.size();
    }
    senderMap = new HashMap<Integer, Sender>();
    InetSocketAddress myAddress = nodeMap.get(id);
    processor = new Processor();
    processor.start();
    final ServerSocket server = new ServerSocket(myAddress.getPort());
    Thread listeningThread = new Thread(new Runnable() {

      public void run() {
        while (true) {
          Socket connection;
          try {
            connection = server.accept();
            DataInputStream dis = new DataInputStream(connection.getInputStream());
            DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
            int otherId = dis.readInt();
            LOG.info("connection from " + otherId);
            boolean isAllowed = otherId > id;
            dos.writeBoolean(isAllowed);
            dos.flush();
            if (!isAllowed) {
              LOG.info("connection from " + otherId + " is not allowed");
              connection.close();
            } else {
              Sender sender = new Sender(otherId, connection);
              senderMap.put(otherId, sender);
              sender.start();
              Receiver receiver = new Receiver(otherId, connection);
              receiver.start();
            }
          } catch (IOException e) {
            // TODO Auto-generated catch block
            LOG.error("", e);
            break;
          }
        }
        try {
          server.close();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          LOG.error("", e);
        }
      }

    });
    listeningThread.start();
    try {
      Thread.sleep(100);
    } catch (InterruptedException e1) {
      // TODO Auto-generated catch block
      LOG.error("", e1);
    }
    // Sleep to make sure that every node is running now
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e1) {
      // TODO Auto-generated catch block
      LOG.error("", e1);
    }
    // TODO Auto-generated method stub
    for (Integer nodeId : nodeMap.keySet()) {
      if (nodeId != id) {
        InetSocketAddress address = nodeMap.get(nodeId);
        try {
          LOG.info("Connecting to " + nodeId);
          Socket connect = new Socket(address.getAddress(), address.getPort());
          DataOutputStream dos = new DataOutputStream(connect.getOutputStream());
          dos.writeInt(id);
          dos.flush();
          DataInputStream dis = new DataInputStream(connect.getInputStream());
          boolean isAllowed = dis.readBoolean();
          if (isAllowed) {
            LOG.info("Connecting to " + nodeId + " is allowed");
            Sender sender = new Sender(nodeId, connect);
            senderMap.put(nodeId, sender);
            sender.start();
            Receiver receiver = new Receiver(nodeId, connect);
            receiver.start();
          } else {
            LOG.info("Connecting to " + nodeId + " is not allowed");
            connect.close();
          }
        } catch (IOException e) {
          // TODO Auto-generated catch block
          LOG.error("", e);
        }
      }
    }
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      LOG.error("", e);
    }
    LOG.info("First send all " + senderMap);
    processor.sendAll(getCurrentMessage());
    if (LeaderElectionInterposition.SAMC_ENABLED) {
      LeaderElectionInterposition.firstSent = true;
    }
  }

  static boolean isBetterThanCurrentLeader(ElectionMessage msg) {
    return leader < msg.leader;
  }

  static int isFinished() {
    LOG.info("Execute isFinished() function");
    int totalNode = nodeMap.size();
    Map<Integer, Integer> count = new HashMap<Integer, Integer>();
    for (Integer electedLeader : electionTable.values()) {
      count.put(electedLeader, count.containsKey(electedLeader) ? count.get(electedLeader) + 1 : 1);
    }
    LOG.info("Election table " + electionTable);
    LOG.info("Count table " + count);
    for (Integer electedLeader : count.keySet()) {
      int totalElect = count.get(electedLeader);
      if (totalElect > totalNode / 2) {
        return electedLeader;
      }
    }
    return -1;
  }

  static ElectionMessage getCurrentMessage() {
    return new ElectionMessage(id, role, leader);
  }

  // update my current state to DMCK
  static void updateStatetoDMCK() {
    // create new file
    LOG.info("[DEBUG] update state id-" + id + " role-" + role + " leader-" + leader);
    try {
      PrintWriter writer = new PrintWriter(ipcDir + "/new/u-" + id);
      writer.println("sendNode=" + id);
      writer.println("sendRole=" + role);
      writer.println("leader=" + leader);
      writer.print("electionTable=");
      for (int node : electionTable.keySet()) {
        writer.print(node + ":" + electionTable.get(node) + ",");
      }
      writer.close();
    } catch (Exception e) {
      LOG.error("[DEBUG] error in creating new file : u-" + id);
    }

    // move new file to send folder - commit message
    try {
      Runtime.getRuntime().exec("mv " + ipcDir + "/new/u-" + id + " " + ipcDir + "/send/u-" + id);
    } catch (Exception e) {
      LOG.error("[DEBUG] error in moving file to send folder : u-" + id);
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.println("usage: LeaderElectionMain <id> <config> <ipcdir>");
      System.exit(1);
    }

    if (LeaderElectionInterposition.SAMC_ENABLED) {
      LOG.info("Enable SAMC without IPC");
    }
    LeaderElectionInterposition.localState = new LocalState();

    id = Integer.parseInt(args[0]);
    role = LOOKING;
    leader = id;

    LOG.info("Just started a node with id = " + id + " role = " + getRoleName(role) + " leader = " + leader);

    electionTable = new HashMap<Integer, Integer>();
    electionTable.put(id, leader);

    readConfig(args[1], args[2]);

    if (ipcDir != "") {
      updateStatetoDMCK();
    }

    work();
  }

  public static class Receiver extends Thread {

    public int otherId;
    public Socket connection;

    public Receiver(int otherId, Socket connection) {
      this.otherId = otherId;
      this.connection = connection;
    }

    void read(DataInputStream dis, byte[] buffer) throws IOException {
      int alreadyRead = 0;
      while (alreadyRead != ElectionMessage.SIZE) {
        alreadyRead = dis.read(buffer, alreadyRead, ElectionMessage.SIZE - alreadyRead);
      }
    }

    @Override
    public void run() {
      LOG.info("Start receiver for " + otherId);
      try {
        DataInputStream dis = new DataInputStream(connection.getInputStream());
        byte[] buffer = new byte[ElectionMessage.SIZE];
        while (!connection.isClosed()) {
          LOG.info("Reading message for " + otherId);
          read(dis, buffer);
          ElectionMessage msg = new ElectionMessage(otherId, buffer);
          LOG.info("Get message : " + msg.toString());
          processor.process(msg);
        }
      } catch (IOException e) {
        // TODO Auto-generated catch block
        LOG.error("", e);
      }
    }

  }

  public static class Sender extends Thread {

    public int otherId;
    public Socket connection;
    public LinkedBlockingQueue<ElectionMessage> queue;
    public OutputStream os;

    public Sender(int otherId, Socket connection) {
      this.otherId = otherId;
      this.connection = connection;
      try {
        os = connection.getOutputStream();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        LOG.error("", e);
      }
      queue = new LinkedBlockingQueue<ElectionMessage>();
      // msgIntercepted = 0;
    }

    public void send(ElectionMessage msg) {
      try {
        queue.put(msg);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        LOG.error("", e);
      }
    }

    public synchronized void write(ElectionMessage msg) {
      try {
        os.write(msg.toBytes());
        os.flush();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        LOG.error("", e);
      }
    }

    @Override
    public void run() {
      LOG.info("Start sender for " + otherId);
      while (!connection.isClosed()) {
        try {
          ElectionMessage msg = queue.take();
          LOG.info("Send message : " + msg.toString() + " to " + otherId);
          if (LeaderElectionInterposition.SAMC_ENABLED) {
            try {
              Event packet = new Event(LeaderElectionInterposition.hash(msg, this.otherId));
              packet.addKeyValue(Event.FROM_ID, id);
              packet.addKeyValue(Event.TO_ID, this.otherId);
              packet.addKeyValue("leader", msg.getLeader());
              packet.addKeyValue("role", msg.getRole());
              LeaderElectionInterposition.nodeSenderMap.put(packet.getId(), packet);
              LeaderElectionInterposition.modelCheckingServer.offerPacket(packet);
            } catch (Exception e) {
              LOG.error("", e);
            }
          } else if (ipcDir != "") {
            interceptMessage(msg, id, msg.getRole(), this.otherId, msg.getLeader());
            write(msg);
          } else {
            write(msg);
          }
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          LOG.error("", e);
        }
      }
    }

    public int getOtherId() {
      return otherId;
    }

    public void setOtherId(int otherId) {
      this.otherId = otherId;
    }

    // ipc interceptor
    public void interceptMessage(ElectionMessage msg, int sender, int senderRole, int receiver, int leader) {

      LOG.info("[DEBUG] before hash in node " + id);
      int eventId = LeaderElectionInterposition.hash(msg, receiver);
      LOG.info("[DEBUG] eventId : " + eventId);

      // create new file
      try {
        PrintWriter writer = new PrintWriter(ipcDir + "/new/le-" + eventId, "UTF-8");
        writer.println("sendNode=" + sender);
        writer.println("recvNode=" + receiver);
        writer.println("sendRole=" + senderRole);
        writer.println("leader=" + leader);
        writer.close();
      } catch (Exception e) {
        LOG.error("[DEBUG] error in creating new file : le-" + eventId);
      }

      // move new file to send folder - commit message
      try {
        Runtime.getRuntime().exec("mv " + ipcDir + "/new/le-" + eventId + " " + ipcDir + "/send/le-" + eventId);
      } catch (Exception e) {
        LOG.error("[ERROR] error in moving file to send folder : le-" + eventId);
      }

      // msgIntercepted++;
      // inform steady state
      /*
       * if(msgIntercepted == nodeMap.size() - 1){
       * LOG.info("[DEBUG] Inform steady state from node " + sender); try{ PrintWriter
       * writer = new PrintWriter(ipcDir + "/new/s-" + sender, "UTF-8");
       * writer.println("sendNode=" + sender); writer.close(); } catch (Exception e){
       * e.printStackTrace(); }
       * 
       * // move new file to send folder - commit message try{
       * Runtime.getRuntime().exec("mv " + ipcDir + "/new/s-" + sender + " " + ipcDir
       * + "/send/s-" + sender); } catch (Exception e){
       * LOG.error("[ERROR] error in moving file to send folder : s-" + sender); } }
       */

      // wait for dmck signal
      File ackFile = new File(ipcDir + "/ack/le-" + eventId);
      LOG.info("[DEBUG] start waiting for file : le-" + eventId);
      while (!ackFile.exists()) {
        // wait
      }

      // receive dmck signal
      // msgIntercepted--;
      // LOG.info("[DEBUG] ack file : " + eventId);
      try {
        Runtime.getRuntime().exec("rm " + ipcDir + "/ack/le-" + eventId);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

  }

  public static class Processor extends Thread {

    LinkedBlockingQueue<ElectionMessage> queue;

    public Processor() {
      queue = new LinkedBlockingQueue<ElectionMessage>();
    }

    public void process(ElectionMessage msg) {
      queue.add(msg);
    }

    public void sendAll(ElectionMessage msg) {
      LOG.info("Sender map " + senderMap);
      for (Integer nodeId : senderMap.keySet()) {
        if (nodeId != id) {
          senderMap.get(nodeId).send(msg);
        }
      }
    }

    @Override
    public void run() {
      LOG.info("Start processor");
      ElectionMessage msg;
      while (true) {
        try {
          LOG.info("Current role is " + (role == LEADING ? "Leading" : role == FOLLOWING ? "Following" : "Looking")
              + "; current leader is " + leader);
          msg = queue.take();
          LOG.info("Process message : " + msg.toString());
          electionTable.put(msg.getSender(), msg.getLeader());
          LOG.info("[DEBUG] Election Table : " + electionTable);
          switch (role) {
            case LOOKING:
              switch (msg.getRole()) {
                case LOOKING:
                  if (isBetterThanCurrentLeader(msg)) {
                    LOG.info("Message " + msg + " is better");
                    leader = msg.getLeader();
                    electionTable.put(id, leader);
                    int newLeader = isFinished();
                    LOG.info("New leader = " + newLeader);
                    if (newLeader != -1) {
                      LOG.info("Finished election, leader = " + newLeader);
                      if (newLeader == id) {
                        role = LEADING;
                      } else {
                        role = FOLLOWING;
                      }
                    }
                    if (ipcDir != "") {
                      updateStatetoDMCK();
                    }
                    sendAll(getCurrentMessage());
                  } else {
                    int newLeader = isFinished();
                    if (newLeader != -1) {
                      LOG.info("Finished election, leader = " + newLeader);
                      if (newLeader == id) {
                        role = LEADING;
                      } else {
                        role = FOLLOWING;
                      }
                    }
                  }
                  break;
                case FOLLOWING:
                case LEADING:
                  leader = msg.getLeader();
                  electionTable.put(id, leader);
                  int newLeader = isFinished();
                  LOG.info("Believe new leader = " + newLeader);
                  if (newLeader != -1) {
                    if (newLeader == id) {
                      role = LEADING;
                    } else {
                      role = FOLLOWING;
                    }
                  }
                  if (ipcDir != "") {
                    updateStatetoDMCK();
                  }
                  sendAll(getCurrentMessage());
                  break;
              }
              break;
            case FOLLOWING:
            case LEADING:
              switch (msg.getRole()) {
                case LOOKING:
                  sendAll(getCurrentMessage());
                  break;
                case FOLLOWING:
                case LEADING:
                  // NOTE assume that conflict leader never happen
                  break;
              }
              break;
          }
          LOG.info("Finished processing " + msg);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          LOG.error("", e);
        }
      }
    }

  }

}
