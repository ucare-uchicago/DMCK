package edu.uchicago.cs.ucare.dmck.rpc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardCopyOption.*;
import java.util.HashMap;
import java.util.Map;

public class FileIpc implements SamcIpc {

  private String mainIpcDirName;
  private File mainIpcDir;
  private File channelDir;
  private File channelNewDir;
  private File channelSendDir;

  private static long ROUND_SLEEP = 50;

  public FileIpc(String mainIpcDirName, String channelName) {
    super();
    this.mainIpcDirName = mainIpcDirName;

    mainIpcDir = new File(mainIpcDirName);
    if (!mainIpcDir.exists()) {
      mainIpcDir.mkdirs();
    } else if (!mainIpcDir.isDirectory()) {
      throw new RuntimeException(mainIpcDirName + " exists, and is not a directory");
    }
    channelDir = new File(mainIpcDirName, channelName);
    if (!channelDir.exists()) {
      channelDir.mkdir();
    } else if (!channelDir.isDirectory()) {
      throw new RuntimeException(channelDir.getAbsolutePath() + " exists, and is not a directory");
    }
    channelNewDir = new File(channelDir, "new");
    if (!channelNewDir.exists()) {
      channelNewDir.mkdir();
    } else if (!channelNewDir.isDirectory()) {
      throw new RuntimeException(
          channelNewDir.getAbsolutePath() + " exists, and is not a directory");
    }
    channelSendDir = new File(channelDir, "send");
    if (!channelSendDir.exists()) {
      channelSendDir.mkdir();
    } else if (!channelSendDir.isDirectory()) {
      throw new RuntimeException(
          channelSendDir.getAbsolutePath() + " exists, and is not a directory");
    }
  }

  @Override
  public void send(String recv, String msgName, Map<String, String> msg) throws Exception {
    Path newMsgPath = FileSystems.getDefault().getPath(mainIpcDirName, recv, "new", msgName);
    Path sendMsgPath = FileSystems.getDefault().getPath(mainIpcDirName, recv, "send", msgName);
    FileWriter writer = new FileWriter(newMsgPath.toFile());
    for (String key : msg.keySet()) {
      writer.write(key);
      writer.write('=');
      writer.write(msg.get(key).toString());
      writer.write('\n');
    }
    writer.close();
    Files.move(newMsgPath, sendMsgPath, REPLACE_EXISTING, ATOMIC_MOVE);
  }

  private Map<String, String> readMessage(String msgName) {
    File msgFile = new File(channelSendDir, msgName);
    if (!msgFile.exists()) {
      return null;
    }
    try {
      BufferedReader reader = new BufferedReader(new FileReader(msgFile));
      Map<String, String> msg = new HashMap<String, String>();
      String line;
      while ((line = reader.readLine()) != null) {
        String[] kv = line.trim().split("=");
        msg.put(kv[0], kv[1]);
      }
      reader.close();
      msgFile.delete();
      return msg;
    } catch (FileNotFoundException e) {
      // Should not happen
      e.printStackTrace();
      return null;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public Map<String, String> receive(String msgName) throws Exception {
    Map<String, String> msg;
    while ((msg = readMessage(msgName)) == null) {
      Thread.sleep(ROUND_SLEEP);
    }
    return msg;
  }

  @Override
  public Map<String, Map<String, String>> receive() throws Exception {
    String[] allMsgNames;
    for (allMsgNames = channelSendDir.list(); allMsgNames.length == 0; allMsgNames =
        channelSendDir.list()) {
      Thread.sleep(ROUND_SLEEP);
    }
    Map<String, Map<String, String>> allMsgs = new HashMap<String, Map<String, String>>();
    for (String msgName : allMsgNames) {
      allMsgs.put(msgName, receive(msgName));
    }
    return allMsgs;
  }

  @Override
  public Map<String, String> receive(String msgName, long timeout) throws Exception {
    Map<String, String> msg;
    int numWait = (int) (timeout / ROUND_SLEEP);
    int i = 0;
    while ((msg = readMessage(msgName)) == null && i < numWait) {
      Thread.sleep(50);
      i++;
    }
    return msg;
  }

  @Override
  public Map<String, Map<String, String>> receive(long timeout) throws Exception {
    String[] allMsgNames;
    int numWait = (int) (timeout / ROUND_SLEEP);
    int i;
    for (allMsgNames = channelSendDir.list(), i = 0; allMsgNames.length == 0
        && i < numWait; allMsgNames = channelSendDir.list(), i++) {
      Thread.sleep(ROUND_SLEEP);
    }
    Map<String, Map<String, String>> allMsgs = new HashMap<String, Map<String, String>>();
    for (String msgName : allMsgNames) {
      allMsgs.put(msgName, receive(msgName));
    }
    return allMsgs;
  }

}
