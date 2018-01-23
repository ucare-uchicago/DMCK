package edu.uchicago.cs.ucare.dmck.util;

public interface ExploredBranchRecorder {

  public void markBelowSubtreeFinished();

  public void markBelowSubtreeFinished(String note);

  public boolean isSubtreeBelowFinished();

  public boolean isSubtreeBelowChildFinished(long child);

  public boolean isSubtreeBelowChildrenFinished(long[] children);

  public boolean createChild(long child);

  public boolean noteThisNode(String key, String value);

  public boolean noteThisNode(String key, String value, boolean overwrite);

  public boolean noteThisNode(String key, byte[] value);

  public boolean noteThisNode(String key, byte[] value, boolean overwrite);

  public byte[] readThisNode(String key);

  public void traverseUpward(int hop);

  public void traverseDownTo(long child);

  public int getCurrentDepth();

  public void resetTraversal();

  public String getCurrentPath();

}
