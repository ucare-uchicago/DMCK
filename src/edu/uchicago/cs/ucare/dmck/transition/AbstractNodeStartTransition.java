package edu.uchicago.cs.ucare.dmck.transition;

import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

@SuppressWarnings("serial")
public class AbstractNodeStartTransition extends AbstractNodeOperationTransition {

  private final static Logger LOG = LoggerFactory.getLogger(AbstractNodeStartTransition.class);

  public AbstractNodeStartTransition(ModelCheckingServerAbstract dmck) {
    super(dmck);
  }

  public AbstractNodeStartTransition(int numNode) {
    super(numNode);
  }

  public AbstractNodeStartTransition(ModelCheckingServerAbstract dmck, boolean isRandom) {
    super(dmck, isRandom);
  }

  @Override
  public boolean apply() {
    NodeOperationTransition t = getRealNodeOperationTransition();
    if (t == null) {
      return false;
    }
    id = t.getId();
    return t.apply();
  }

  @Override
  public long getTransitionId() {
    return 113;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof AbstractNodeStartTransition;
  }

  @Override
  public int hashCode() {
    return 113;
  }

  @Override
  public NodeStartTransition getRealNodeOperationTransition() {
    if (isRandom) {
      LinkedList<NodeOperationTransition> allPossible = getAllRealNodeOperationTransitions(dmck.isNodeOnline);
      if (allPossible.isEmpty()) {
        LOG.debug("Try to execute start node event, but currently there is no offline node");
        return null;
      }
      int i = RANDOM.nextInt(allPossible.size());
      return (NodeStartTransition) allPossible.get(i);
    } else {
      for (int i = 0; i < dmck.numNode; ++i) {
        if (!dmck.isNodeOnline(i)) {
          NodeStartTransition realStart = new NodeStartTransition(dmck, i);
          realStart.setVectorClock(getPossibleVectorClock(i));
          return realStart;
        }
      }
      LOG.debug("Try to execute start node event, but currently there is no offline node");
      return null;
    }
  }

  @Override
  public NodeOperationTransition getRealNodeOperationTransition(int suggestExecuteNodeId) {
    if (!dmck.isNodeOnline(suggestExecuteNodeId)) {
      NodeStartTransition realStart = new NodeStartTransition(dmck, suggestExecuteNodeId);
      realStart.setVectorClock(getPossibleVectorClock(suggestExecuteNodeId));
      return realStart;
    }
    LOG.debug(
        "Try to execute start node event based on suggestion, but currently the suggested node is already online.");
    return null;
  }

  @Override
  public LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions(boolean[] onlineStatus) {
    LinkedList<NodeOperationTransition> result = new LinkedList<NodeOperationTransition>();
    for (int i = 0; i < onlineStatus.length; ++i) {
      if (!onlineStatus[i]) {
        NodeStartTransition realStart = new NodeStartTransition(dmck, i);
        realStart.setVectorClock(getPossibleVectorClock(i));
        result.add(realStart);
      }
    }
    return result;
  }

  @Override
  public LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions() {
    LinkedList<NodeOperationTransition> result = new LinkedList<NodeOperationTransition>();
    for (int i = 0; i < dmck.numNode; ++i) {
      NodeStartTransition realStart = new NodeStartTransition(dmck, i);
      realStart.setVectorClock(getPossibleVectorClock(i));
      result.add(realStart);
    }
    return result;
  }

  public String toString() {
    return "abstract_start";
  }

  @Override
  public String toStringForFutureExecution() {
    return "abstract_start";
  }

}