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

  public Path() {
    super();
  }

  public Path(Collection<Transition> transitions) {
    super(transitions);
  }

  public Path(Path transitions) {
    super(transitions);
  }

  @Override
  public Path clone() {
    return new Path(this);
  }

  public Path getSerializable(int numNode) {
    Path retVal = new Path(new LinkedList<Transition>());

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
