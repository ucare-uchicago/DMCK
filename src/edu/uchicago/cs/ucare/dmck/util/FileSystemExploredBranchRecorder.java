package edu.uchicago.cs.ucare.dmck.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * I use dir representing node that I have explored. If there is .finished under which dir, it means
 * that I have explored all possible path below that node e.g. initDir = /tmp/record,
 * /tmp/record/1/2/.finished means I have explored all possible branch under 2 (i.e. 2/3/4/..,
 * 2/4/3/..., ...). And /tmp/record/.finished means I am finished exploring all possible branch
 */
public class FileSystemExploredBranchRecorder implements ExploredBranchRecorder {

  final static Logger LOG = LoggerFactory.getLogger(FileSystemExploredBranchRecorder.class);

  int currentDepth;
  String initDir;
  String currentDir;

  public FileSystemExploredBranchRecorder(String initDir) {
    this.initDir = initDir;
    resetTraversal();
  }

  public FileSystemExploredBranchRecorder(String initDir, boolean createDir) {
    this.initDir = initDir;
    if (createDir) {
      File initDirFile = new File(initDir);
      initDirFile.mkdirs();
    }
    resetTraversal();
  }

  public FileSystemExploredBranchRecorder(FileSystemExploredBranchRecorder origin) {
    currentDepth = origin.currentDepth;
    initDir = origin.initDir;
    currentDir = origin.currentDir;
  }

  public void markBelowSubtreeFinished() {
    try {
      File finishedFile = new File(currentDir + "/.finished");
      finishedFile.createNewFile();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Marked " + currentDir + " to be explored");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void markBelowSubtreeFinished(String note) {
    try {
      FileOutputStream fos = new FileOutputStream(currentDir + "/.finished");
      fos.write(note.getBytes());
      fos.close();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Marked " + currentDir + " to be explored with note, " + note);
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public boolean isSubtreeBelowFinished() {
    File finishedFlag = new File(currentDir + "/.finished");
    return finishedFlag.exists();
  }

  public boolean isSubtreeBelowChildFinished(long child) {
    // LOG.info("korn is subtree finished " + currentDir + "/" + child);
    File finishedFlag = new File(currentDir + "/" + child + "/.finished");
    return finishedFlag.exists();
  }

  public boolean isSubtreeBelowChildrenFinished(long[] children) {
    StringBuilder strBuilder = new StringBuilder(currentDir);
    for (long child : children) {
      strBuilder.append('/');
      strBuilder.append(child);
      File finishedFlag = new File(strBuilder.toString() + "/.finished");
      if (!finishedFlag.exists()) {
        return false;
      }
    }
    File finishedFlag = new File(strBuilder.toString() + "/.finished");
    return finishedFlag.exists();
  }

  public boolean createChild(long child) {
    File currentIdDirFile = new File(currentDir + "/" + child);
    if (!currentIdDirFile.exists()) {
      return currentIdDirFile.mkdir();
    }
    return true;
  }

  public boolean noteThisNode(String key, String value) {
    return noteThisNode(key, value.getBytes(), true);
  }

  public boolean noteThisNode(String key, String value, boolean overwrite) {
    return noteThisNode(key, value.getBytes(), overwrite);
  }

  public boolean noteThisNode(String key, byte[] value) {
    return noteThisNode(key, value, true);
  }

  public boolean noteThisNode(String key, byte[] value, boolean overwrite) {
    File note = new File(currentDir + "/" + key);
    try {
      if (!note.exists() || overwrite) {
        note.createNewFile();
        FileOutputStream fos = new FileOutputStream(note);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        bos.write(value);
        bos.close();
        fos.close();
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public byte[] readThisNode(String key) {
    // LOG.info("korn reading " + currentDir + "/" + key);
    File note = new File(currentDir + "/" + key);
    if (!note.exists()) {
      return null;
    }
    try {
      FileInputStream fis = new FileInputStream(note);
      int fileSize = (int) note.length();
      byte[] buff = new byte[fileSize];
      fis.read(buff);
      fis.close();
      return buff;
    } catch (FileNotFoundException e) {
      return null;
    } catch (IOException e) {
      return null;
    }
  }

  public void traverseUpward(int hop) {
    for (int i = 0; i < hop && currentDepth > 0; ++i, --currentDepth) {
      currentDir = currentDir.substring(0, currentDir.lastIndexOf('/'));
    }
  }

  public void traverseDownTo(long child) {
    currentDir = currentDir + "/" + child;
    ++currentDepth;
  }

  public void resetTraversal() {
    currentDepth = 0;
    currentDir = initDir;
  }

  public int getCurrentDepth() {
    return currentDepth;
  }

  public String getCurrentPath() {
    return currentDir;
  }

}
