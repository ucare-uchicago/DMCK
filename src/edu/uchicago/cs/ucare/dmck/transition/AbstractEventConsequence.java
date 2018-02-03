package edu.uchicago.cs.ucare.dmck.transition;

import java.io.Serializable;
import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;
import edu.uchicago.cs.ucare.dmck.server.ReductionAlgorithmsModelChecker;
import edu.uchicago.cs.ucare.dmck.util.LocalState;

public class AbstractEventConsequence implements Serializable {

  private static final long serialVersionUID = -5930228490878216478L;

  private LocalState nodeStateBeforeEventExec;
  private LocalState nodeStateAfterEventExec;
  private Transition event;

  public AbstractEventConsequence(LocalState oldState, Transition ev, LocalState newState) {
    nodeStateBeforeEventExec = oldState;
    nodeStateAfterEventExec = newState;
    event = ev;
  }

  public LocalState getNodeStateBeforeEventExec() {
    return nodeStateBeforeEventExec;
  }

  public LocalState getNodeStateAfterEventExec() {
    return nodeStateAfterEventExec;
  }

  public Transition getEvent() {
    return event;
  }

  public boolean isIdentical(AbstractEventConsequence otherAEC) {
    if (this.toString().equals(otherAEC.toString())) {
      return true;
    } else {
      return false;
    }
  }

  public LocalState getTransformationState(LocalState oldState, Transition ev) {
    if (!ReductionAlgorithmsModelChecker.isIdenticalAbstractLocalStates(nodeStateBeforeEventExec,
        oldState)) {
      return null;
    }

    // check abstract event
    if (!ReductionAlgorithmsModelChecker.isIdenticalAbstractEvent(event, ev)) {
      return null;
    }

    return nodeStateAfterEventExec;
  }

  public String toString() {
    return ReductionAlgorithmsModelChecker.getAbstractLocalState(nodeStateBeforeEventExec) + " >> "
        + ReductionAlgorithmsModelChecker.getAbstractEvent(event) + " >> "
        + ReductionAlgorithmsModelChecker.getAbstractLocalState(nodeStateAfterEventExec).toString();
  }

  public AbstractEventConsequence getSerializable(int numNode) {
    return new AbstractEventConsequence(this.nodeStateBeforeEventExec,
        this.event.getSerializable(numNode), this.nodeStateAfterEventExec);
  }

  public AbstractEventConsequence getRealAbsEvCons(ModelCheckingServerAbstract mc) {
    return new AbstractEventConsequence(this.getNodeStateBeforeEventExec(),
        Transition.getRealTransition(mc, this.getEvent()), this.getNodeStateAfterEventExec());
  }

}
