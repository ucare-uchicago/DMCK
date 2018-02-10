package edu.uchicago.cs.ucare.dmck.server;

public class PathMeta implements Comparable<PathMeta> {

  private int pathId;

  private int parentPathId;

  private String pathString;

  public PathMeta(int pathId, int parentId, String pathString) {
    this.pathString = pathString;
    this.pathId = pathId;
    this.parentPathId = parentId;
  }

  public String getPathString() {
    return pathString;
  }

  public int getId() {
    return pathId;
  }

  public int getParentId() {
    return parentPathId;
  }

  @Override
  public String toString() {
    return this.pathString;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof PathMeta) {
      return (((PathMeta) obj).pathString.equals(this.pathString));
    }
    return false;
  }

  @Override
  public int hashCode() {
    return pathString.hashCode();
  }

  @Override
  public int compareTo(PathMeta o) {
    return this.pathString.compareTo(o.pathString);
  }
}
