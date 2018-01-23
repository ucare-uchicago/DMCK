package edu.uchicago.cs.ucare.dmck.server;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;

import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.TransitionTuple;

public class Path extends LinkedList<TransitionTuple> implements Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = 7359356166185399233L;

  public Path() {
    super();
  }

  public Path(Collection<TransitionTuple> transitionTuples) {
    super(transitionTuples);
  }

  public Path(Path transitionTuples) {
    super(transitionTuples);
  }

  @Override
  public Path clone() {
    return new Path(this);
  }

  public Path getSerializable(int numNode) {
    Path retVal = new Path(new LinkedList<TransitionTuple>());

    for (TransitionTuple realTuple : this) {
      retVal.add(realTuple.getSerializable(numNode));
    }

    return retVal;
  }

  @Override
  public String toString() {
    return Path.pathToString(this);
  }

  public static String pathToString(Path dporInitialPath) {
    String path = "";
    for (int i = 0; i < dporInitialPath.size(); i++) {
      if (dporInitialPath.get(i).transition instanceof PacketSendTransition) {
        if (i == 0) {
          path = String.valueOf(dporInitialPath.get(i).transition.getTransitionId());
        } else {
          path += "," + String.valueOf(dporInitialPath.get(i).transition.getTransitionId());
        }
      } else {
        if (i == 0) {
          path = ((NodeOperationTransition) dporInitialPath.get(i).transition).toStringForFutureExecution();
        } else {
          path += "," + ((NodeOperationTransition) dporInitialPath.get(i).transition).toStringForFutureExecution();
        }
      }
    }
    return path;
  }
}
