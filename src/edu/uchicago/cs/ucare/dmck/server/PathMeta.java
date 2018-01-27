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

  public PathMeta(Path path) {
    this.pathId = path.getId();
    this.parentPathId = path.getParentId();
    this.pathString = Path.pathToString(path);
  }

  @Deprecated
  public PathMeta(String pathString) {
    // TODO: this constructor is temporary and will be removed after we finalize
    // JSON serialization and pathId inheritance
    this.pathId = 0;
    this.parentPathId = 0;
    this.pathString = pathString;
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
