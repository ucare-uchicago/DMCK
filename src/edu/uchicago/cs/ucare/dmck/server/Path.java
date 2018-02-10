package edu.uchicago.cs.ucare.dmck.server;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;

public class Path extends LinkedList<Transition> implements Serializable {

  /**
   *
   */
  private static final long serialVersionUID = 7359356166185399233L;

  private int myId;

  private int myParentId;

  /**
   * Only use this constructor if we only care about it as collection. The pathId and parentPathId
   * by default will set to -1. If pathId and parentPathId is matter in intialization, use the the
   * alternative constructor {@code Path(int pathId, int parentId)}
   */
  public Path() {
    super();
    this.myId = -1;
    this.myParentId = -1;
  }

  public Path(int pathId, int parentId) {
    super();
    this.myId = pathId;
    this.myParentId = parentId;
  }

  private Path(Path transitions) {
    super(transitions);
    this.myId = transitions.getId();
    this.myParentId = transitions.getParentId();
  }

  public void setId(int id) {
    this.myId = id;
  }

  public int getId() {
    return myId;
  }

  public void setParentId(int parentId) {
    this.myParentId = parentId;
  }

  public int getParentId() {
    return myParentId;
  }

  public PathMeta toPathMeta() {
    return new PathMeta(myId, myParentId, Path.pathToString(this));
  }

  @Override
  public Path clone() {
    return new Path(this);
  }

  @Deprecated
  public Path getSerializable(int numNode) {
    Path retVal = new Path(this.myId, this.myParentId);

    for (Transition transition : this) {
      retVal.add(transition.getSerializable(numNode));
    }

    return retVal;
  }

  // Current expected events to add into Path List are only
  // PacketSendTransition, NodeCrashTransition, and NodeStartTransition.
  // Otherwise, DMCK will throw exception, since we try to add unknown type of
  // events.
  public void addTransition(Transition ev) {
    if (ev instanceof PacketSendTransition) {
      this.add(((PacketSendTransition) ev).clone());
    } else if (ev instanceof NodeCrashTransition) {
      this.add(((NodeCrashTransition) ev).clone());
    } else if (ev instanceof NodeStartTransition) {
      this.add(((NodeStartTransition) ev).clone());
    } else {
      throw new RuntimeException("Add unknown transition ev=" + ev.toString());
    }
  }

  @Override
  public String toString() {
    return Path.pathToString(this);
  }

  public static String pathToString(Path path) {
    String result = "";
    for (int i = 0; i < path.size(); i++) {
      if (path.get(i) instanceof PacketSendTransition) {
        if (i == 0) {
          result = String.valueOf(path.get(i).getTransitionId());
        } else {
          result += "," + String.valueOf(path.get(i).getTransitionId());
        }
      } else {
        if (i == 0) {
          result = ((NodeOperationTransition) path.get(i)).toStringForFutureExecution();
        } else {
          result += "," + ((NodeOperationTransition) path.get(i)).toStringForFutureExecution();
        }
      }
    }
    return result;
  }
}
