package edu.uchicago.cs.ucare.dmck.server;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.transition.TransitionTuple;
import edu.uchicago.cs.ucare.dmck.util.LocalState;
import edu.uchicago.cs.ucare.dmck.util.WorkloadDriver;

public abstract class EvaluationModelChecker extends ReductionAlgorithmsModelChecker {

  public EvaluationModelChecker(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash, int numReboot,
      String globalStatePathDir, String packetRecordDir, String cacheDir, WorkloadDriver workloadDriver,
      String ipcDir) {
    super(dmckName, fileWatcher, numNode, numCrash, numReboot, globalStatePathDir, packetRecordDir, cacheDir,
        workloadDriver, ipcDir);
  }

  public abstract boolean isLMDependent(LocalState state, Event e1, Event e2);

  public abstract boolean isCMDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, Event msg,
      NodeCrashTransition crash);

  public abstract boolean isCCDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, NodeCrashTransition crash1,
      NodeCrashTransition crash2);

  public abstract boolean isCRSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalstate, Transition event);

  public abstract boolean isRSSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState, Transition event);

  public abstract boolean isNotDiscardReorder(boolean[] wasNodeOnline, LocalState[] wasLocalState,
      NodeOperationTransition crashOrRebootEvent, Event msg);

  // if return >= 0, then return node that will be crashed / rebooted
  // if return -1, then there is no interesting node to be crashed / rebooted,
  // therefore choose other event
  // if return -2, then execute the nextTransition

  protected int isRuntimeCrashRebootSymmetric(Transition nextTransition) {
    if (nextTransition instanceof AbstractNodeCrashTransition) {
      for (int i = 0; i < numNode; ++i) {
        NodeCrashTransition crashEvent = new NodeCrashTransition(this, i);
        if (isNodeOnline(i) && isCRSDependent(isNodeOnline, localStates, crashEvent)) {
          return i;
        }
      }
      recordPolicyEffectiveness("crs-rss-runtime");
      return -1;
    } else if (nextTransition instanceof NodeCrashTransition) {
      NodeCrashTransition crashEvent = (NodeCrashTransition) nextTransition;
      if (isNodeOnline(crashEvent.id) && isCRSDependent(isNodeOnline, localStates, crashEvent)) {
        return -2;
      }
      recordPolicyEffectiveness("crs-rss-runtime");
      return -1;
    } else if (nextTransition instanceof AbstractNodeStartTransition) {
      for (int i = 0; i < numNode; ++i) {
        NodeStartTransition rebootEvent = new NodeStartTransition(this, i);
        if (!isNodeOnline(i) && isRSSDependent(isNodeOnline, localStates, rebootEvent)) {
          return i;
        }
      }
      recordPolicyEffectiveness("crs-rss-runtime");
      return -1;
    } else if (nextTransition instanceof NodeStartTransition) {
      NodeStartTransition rebootEvent = (NodeStartTransition) nextTransition;
      if (!isNodeOnline(rebootEvent.id) && isRSSDependent(isNodeOnline, localStates, rebootEvent)) {
        return -2;
      }
      recordPolicyEffectiveness("crs-rss-runtime");
      return -1;
    }
    return -2;
  }

  protected boolean isPossibleConcurrent(Transition lastTransition, Transition tupleTransition) {
    // hack solution for multiple client requests for Cassandra system
    if (dmckName.equals("cassChecker") && lastTransition instanceof PacketSendTransition
        && tupleTransition instanceof PacketSendTransition) {
      PacketSendTransition lt = (PacketSendTransition) lastTransition;
      PacketSendTransition tt = (PacketSendTransition) tupleTransition;
      int lastCR1 = (int) lt.getPacket().getValue("clientRequest");
      int lastCR2 = (int) tt.getPacket().getValue("clientRequest");
      if (lastCR1 != lastCR2) {
        return true;
      }
    }
    if (lastTransition instanceof AbstractNodeCrashTransition) {
      AbstractNodeCrashTransition abstractCrash = (AbstractNodeCrashTransition) lastTransition;
      NodeCrashTransition realCrash = new NodeCrashTransition(this, abstractCrash.id);
      realCrash.setVectorClock(abstractCrash.getPossibleVectorClock(abstractCrash.id));
      lastTransition = realCrash;
    } else if (lastTransition instanceof AbstractNodeStartTransition) {
      AbstractNodeStartTransition abstractStart = (AbstractNodeStartTransition) lastTransition;
      NodeStartTransition realStart = new NodeStartTransition(this, abstractStart.id);
      realStart.setVectorClock(abstractStart.getPossibleVectorClock(abstractStart.id));
      lastTransition = realStart;
    }
    List<Transition> allBeforeTransitions = dependencies.get(lastTransition);
    if (allBeforeTransitions == null) {
      return false;
    } else {
      if (tupleTransition instanceof AbstractNodeCrashTransition) {
        AbstractNodeCrashTransition abstractCrash = (AbstractNodeCrashTransition) tupleTransition;
        NodeCrashTransition realCrash = new NodeCrashTransition(this, abstractCrash.id);
        realCrash.setVectorClock(abstractCrash.getPossibleVectorClock(abstractCrash.id));
        tupleTransition = realCrash;
      } else if (tupleTransition instanceof AbstractNodeStartTransition) {
        AbstractNodeStartTransition abstractStart = (AbstractNodeStartTransition) tupleTransition;
        NodeStartTransition realStart = new NodeStartTransition(this, abstractStart.id);
        realStart.setVectorClock(abstractStart.getPossibleVectorClock(abstractStart.id));
        tupleTransition = realStart;
      }
      return !allBeforeTransitions.contains(tupleTransition);
    }
  }

  // return true = break loop, false = continue
  protected boolean evaluatePairsOfTransitions(Path tmpPath, boolean[] wasOnlineNodes, LocalState[] wasLocalStates,
      TransitionTuple tuple, TransitionTuple lastTransition) {
    if (lastTransition.transition instanceof PacketSendTransition) {
      Event lastPacket = ((PacketSendTransition) lastTransition.transition).getPacket();
      if (tuple.transition instanceof PacketSendTransition) {
        if (isPossibleConcurrent(lastTransition.transition, tuple.transition)) {
          Event tuplePacket = ((PacketSendTransition) tuple.transition).getPacket();
          if (lastPacket.isObsolete() || tuplePacket.isObsolete()) {
            return false;
          } else if (!wasOnlineNodes[lastPacket.getFromId()]) {
            return true;
          } else if (tuplePacket.getToId() != lastPacket.getToId()
              || tuplePacket.getFromId() != lastPacket.getFromId()) {
            // DPOR policy
            if (tuplePacket.getToId() == lastPacket.getToId()) {
              int toId = tuplePacket.getToId();
              // msg vs msg
              if (isLMDependent(wasLocalStates[toId], lastPacket, tuplePacket)) {
                return addNewInitialPath(wasLocalStates, tmpPath, new TransitionTuple(0, tuple.transition),
                    new TransitionTuple(0, lastTransition.transition));
              }
            }
          } else if (tuplePacket.getToId() == lastPacket.getToId()
              && tuplePacket.getFromId() == lastPacket.getFromId()) {
            // if non-TCP paradigm, then evaluate possibility of
            // reordering
            if (!this.tcpParadigm) {
              if (isLMDependent(wasLocalStates[lastPacket.getToId()], lastPacket, tuplePacket)) {
                return addNewInitialPath(wasLocalStates, tmpPath, new TransitionTuple(0, tuple.transition),
                    new TransitionTuple(0, lastTransition.transition));
              }
            } else {
              return true;
            }
          }
        }
      } else if (tuple.transition instanceof NodeCrashTransition) {
        if (isPossibleConcurrent(lastTransition.transition, tuple.transition)) {
          NodeCrashTransition tupleCrash = (NodeCrashTransition) tuple.transition;
          if (lastPacket.getFromId() == tupleCrash.getId() || lastPacket.getToId() == tupleCrash.getId()) {
            if (wasOnlineNodes[lastPacket.getFromId()] && wasOnlineNodes[lastPacket.getToId()]) {
              if (isCMDependent(wasOnlineNodes, wasLocalStates, lastPacket, tupleCrash)
                  && isNotDiscardReorder(wasOnlineNodes, wasLocalStates, tupleCrash, lastPacket)) {
                if (lastPacket.isObsolete()) {
                  lastPacket.setObsolete(false);
                }
                return addNewInitialPath(wasLocalStates, tmpPath, new TransitionTuple(0, tupleCrash),
                    new TransitionTuple(0, lastTransition.transition));
              }
            }
          }
        } else {
          return true;
        }
      } else if (tuple.transition instanceof NodeStartTransition) {
        if (isPossibleConcurrent(lastTransition.transition, tuple.transition)) {
          NodeStartTransition tupleStart = (NodeStartTransition) tuple.transition;
          if (!lastPacket.isObsolete()) {
            if (tupleStart.getId() == lastPacket.getFromId() || tupleStart.getId() == lastPacket.getToId()) {
              return true;
            }
            if (isNotDiscardReorder(wasOnlineNodes, wasLocalStates, tupleStart, lastPacket)) {
              return addNewInitialPath(wasLocalStates, tmpPath, new TransitionTuple(0, tupleStart),
                  new TransitionTuple(0, lastTransition.transition));
            }
          }
        } else {
          return true;
        }
      }
    } else if (lastTransition.transition instanceof NodeCrashTransition) {
      NodeCrashTransition lastCrash = (NodeCrashTransition) lastTransition.transition;
      if (wasOnlineNodes[lastCrash.getId()]) {
        boolean[] tempOnlineNodes = wasOnlineNodes.clone();
        tempOnlineNodes[lastCrash.getId()] = !wasOnlineNodes[lastCrash.getId()];
        if (tuple.transition instanceof PacketSendTransition) {
          if (isPossibleConcurrent(lastTransition.transition, tuple.transition)) {
            PacketSendTransition tupleTransition = (PacketSendTransition) tuple.transition;
            if (tupleTransition.getPacket().getFromId() == lastCrash.getId()
                || tupleTransition.getPacket().getToId() == lastCrash.getId()) {
              if (isCRSDependent(wasOnlineNodes, wasLocalStates, lastCrash)
                  && isCMDependent(wasOnlineNodes, wasLocalStates, tupleTransition.getPacket(), lastCrash)) {
                addNewInitialPath(wasLocalStates, tmpPath, new TransitionTuple(0, tupleTransition),
                    new TransitionTuple(0, lastCrash));
                if (isSAMC) {
                  addEventToIncompleteHistory(copyLocalState(wasLocalStates), lastCrash);
                }
                return true;
              }
            }
          } else {
            return true;
          }
        } else if (tuple.transition instanceof NodeCrashTransition) {
          NodeCrashTransition tupleCrash = (NodeCrashTransition) tuple.transition;
          if (tempOnlineNodes[tupleCrash.getId()]) {
            // only reorder if both nodes are possible to be crashed
            if (isCRSDependent(wasOnlineNodes, wasLocalStates, lastCrash)
                && isCCDependent(wasOnlineNodes, wasLocalStates, tupleCrash, lastCrash)) {
              addNewInitialPath(wasLocalStates, tmpPath, new TransitionTuple(0, tupleCrash),
                  new TransitionTuple(0, lastCrash));
              if (isSAMC) {
                addEventToIncompleteHistory(copyLocalState(wasLocalStates), lastCrash);
              }
              return true;
            }
          }
        } else if (tuple.transition instanceof NodeStartTransition) {
          NodeStartTransition tupleStart = (NodeStartTransition) tuple.transition;
          if (!tempOnlineNodes[tupleStart.getId()]) {
            // only reorder if both nodes are possible to be crashed
            if (isCRSDependent(wasOnlineNodes, wasLocalStates, lastCrash)) {
              addNewInitialPath(wasLocalStates, tmpPath, new TransitionTuple(0, tupleStart),
                  new TransitionTuple(0, lastCrash));
              if (isSAMC) {
                addEventToIncompleteHistory(copyLocalState(wasLocalStates), lastCrash);
              }
              return true;
            }
          }
        }
      }
    } else if (lastTransition.transition instanceof NodeStartTransition) {
      NodeStartTransition lastStart = (NodeStartTransition) lastTransition.transition;
      if (!wasOnlineNodes[lastStart.getId()]) {
        boolean[] tempOnlineNodes = wasOnlineNodes.clone();
        tempOnlineNodes[lastStart.getId()] = !wasOnlineNodes[lastStart.getId()];
        if (tuple.transition instanceof PacketSendTransition) {
          if (isPossibleConcurrent(lastTransition.transition, tuple.transition)) {
            PacketSendTransition tupleTransition = (PacketSendTransition) tuple.transition;
            if (isRSSDependent(wasOnlineNodes, wasLocalStates, lastStart)) {
              addNewInitialPath(wasLocalStates, tmpPath, new TransitionTuple(0, tupleTransition),
                  new TransitionTuple(0, lastStart));
              if (isSAMC) {
                addEventToIncompleteHistory(copyLocalState(wasLocalStates), lastStart);
              }
            }
          } else {
            return true;
          }
        } else if (tuple.transition instanceof NodeCrashTransition) {
          NodeCrashTransition tupleCrash = (NodeCrashTransition) tuple.transition;
          if (tempOnlineNodes[tupleCrash.getId()]) {
            // only reorder if both nodes are possible to be rebooted
            if (isRSSDependent(wasOnlineNodes, wasLocalStates, lastStart)) {
              addNewInitialPath(wasLocalStates, tmpPath, new TransitionTuple(0, tupleCrash),
                  new TransitionTuple(0, lastStart));
              if (isSAMC) {
                addEventToIncompleteHistory(copyLocalState(wasLocalStates), lastStart);
              }
              return true;
            }
          }
        } else if (tuple.transition instanceof NodeStartTransition) {
          NodeStartTransition tupleStart = (NodeStartTransition) tuple.transition;
          if (!tempOnlineNodes[tupleStart.getId()]) {
            // only reorder if both nodes are possible to be crashed
            if (isRSSDependent(wasOnlineNodes, wasLocalStates, lastStart)) {
              addNewInitialPath(wasLocalStates, tmpPath, new TransitionTuple(0, tupleStart),
                  new TransitionTuple(0, lastStart));
              if (isSAMC) {
                addEventToIncompleteHistory(copyLocalState(wasLocalStates), lastStart);
              }
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  // by CRS or RSS, we will eliminate symmetrical events
  @SuppressWarnings({ "unchecked", "unlikely-arg-type" })
  protected void reevaluateInitialPaths(Path initialPath, LocalState[] wasLocalStates, Transition lastEvent) {
    if (lastEvent instanceof NodeCrashTransition || lastEvent instanceof AbstractNodeCrashTransition) {
      LinkedList<Path> initialPathsClone = (LinkedList<Path>) initialPaths.clone();
      Iterator<Path> initialPathsCloneIterator = initialPathsClone.iterator();
      while (initialPathsCloneIterator.hasNext()) {
        Path comparePath = initialPathsCloneIterator.next();
        if (comparePath.contains(initialPath) && comparePath.size() == initialPath.size() + 1) {
          Transition lastEventOfComparePath = comparePath.getLast().transition;
          if (lastEventOfComparePath instanceof NodeCrashTransition) {
            NodeCrashTransition lastCrashCompare = (NodeCrashTransition) lastEventOfComparePath;
            if (isSymmetric(wasLocalStates, lastCrashCompare)) {
              LOG.debug("Remove an Initial Path from initialPaths because it has symmetrical crash.");
              initialPaths.remove(comparePath);
            }
          }
        }
      }
    } else if (lastEvent instanceof NodeStartTransition || lastEvent instanceof AbstractNodeStartTransition) {
      LinkedList<Path> initialPathsClone = (LinkedList<Path>) initialPaths.clone();
      Iterator<Path> initialPathsCloneIterator = initialPathsClone.iterator();
      while (initialPathsCloneIterator.hasNext()) {
        Path comparePath = initialPathsCloneIterator.next();
        if (comparePath.contains(initialPath) && comparePath.size() == initialPath.size() + 1) {
          Transition lastEventOfComparePath = comparePath.getLast().transition;
          if (lastEventOfComparePath instanceof NodeStartTransition) {
            NodeStartTransition lastStartCompare = (NodeStartTransition) lastEventOfComparePath;
            if (isSymmetric(wasLocalStates, lastStartCompare)) {
              LOG.debug("Remove an Initial Path from initialPaths because it has symmetrical reboot.");
              initialPaths.remove(comparePath);
            }
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void calculateInitialPaths() {
    TransitionTuple lastTransition;
    while ((lastTransition = currentExploringPath.pollLast()) != null) {
      if (hasDirectedInitialPath && currentExploringPath.size() < directedInitialPath.size()) {
        break;
      }
      boolean[] oldOnlineStatus = prevOnlineStatus.removeLast();
      LocalState[] oldLocalStates = prevLocalStates.removeLast();
      Path tmpPath = currentExploringPath.clone();

      // reevaluate InitialPaths with CRS and RSS
      reevaluateInitialPaths(tmpPath, copyLocalState(oldLocalStates), lastTransition.transition);

      // check lastTransition instance, if it is crash or reboot on X
      // node, then add other X node possibilities
      if (lastTransition.transition instanceof AbstractNodeCrashTransition) {
        AbstractNodeCrashTransition abstractNodeCrashTransition = (AbstractNodeCrashTransition) lastTransition.transition;
        LinkedList<NodeOperationTransition> transitions = abstractNodeCrashTransition
            .getAllRealNodeOperationTransitions(oldOnlineStatus);
        for (NodeOperationTransition t : transitions) {
          if (abstractNodeCrashTransition.id != t.id) {
            // check CRS
            if (isCRSDependent(oldOnlineStatus, oldLocalStates, t)) {
              Path interestingPath = tmpPath.clone();
              interestingPath.add(new TransitionTuple(0, t));
              addToInitialPathList(interestingPath);
              // if SAMC, record crash to history
              if (isSAMC) {
                addEventToIncompleteHistory(copyLocalState(oldLocalStates), t);
              }
            }
          }
        }
      } else if (lastTransition.transition instanceof AbstractNodeStartTransition) {
        AbstractNodeStartTransition abstractNodeStartTransition = (AbstractNodeStartTransition) lastTransition.transition;
        LinkedList<NodeOperationTransition> transitions = ((AbstractNodeStartTransition) lastTransition.transition)
            .getAllRealNodeOperationTransitions(oldOnlineStatus);
        for (NodeOperationTransition t : transitions) {
          if (abstractNodeStartTransition.id != t.id) {
            // check RSS
            if (isRSSDependent(oldOnlineStatus, oldLocalStates, t)) {
              Path interestingPath = tmpPath.clone();
              interestingPath.add(new TransitionTuple(0, t));
              addToInitialPathList(interestingPath);
              // if SAMC, record reboot to history
              if (isSAMC) {
                addEventToIncompleteHistory(copyLocalState(oldLocalStates), t);
              }
            }
          }
        }
      }

      int reverseCounter = currentExploringPath.size();
      Iterator<TransitionTuple> reverseIter = currentExploringPath.descendingIterator();
      Iterator<LocalState[]> reverseLocalStateIter = prevLocalStates.descendingIterator();
      Iterator<boolean[]> reverseOnlineStatusIter = prevOnlineStatus.descendingIterator();
      while (reverseIter.hasNext()) {
        if (hasDirectedInitialPath && reverseCounter <= directedInitialPath.size()) {
          break;
        }
        TransitionTuple tuple = reverseIter.next();
        LocalState[] tmpOldLocalStates = reverseLocalStateIter.next();
        boolean[] tmpOldOnlineStatus = reverseOnlineStatusIter.next();
        reverseCounter--;
        tmpPath.pollLast();

        // transform abstract events to concrete events
        boolean breakLoop = false;
        if (!(lastTransition.transition instanceof AbstractNodeOperationTransition)
            && !(tuple.transition instanceof AbstractNodeOperationTransition)) {
          breakLoop = evaluatePairsOfTransitions(tmpPath, tmpOldOnlineStatus, tmpOldLocalStates, tuple, lastTransition);
        } else if ((lastTransition.transition instanceof AbstractNodeOperationTransition)
            && !(tuple.transition instanceof AbstractNodeOperationTransition)) {
          LinkedList<NodeOperationTransition> transitions = null;
          if (lastTransition.transition instanceof AbstractNodeCrashTransition) {
            AbstractNodeCrashTransition abstractNodeCrashTransition = (AbstractNodeCrashTransition) lastTransition.transition;
            transitions = abstractNodeCrashTransition.getAllRealNodeOperationTransitions(tmpOldOnlineStatus);
          } else if (lastTransition.transition instanceof AbstractNodeStartTransition) {
            AbstractNodeStartTransition abstractNodeStartTransition = (AbstractNodeStartTransition) lastTransition.transition;
            transitions = abstractNodeStartTransition.getAllRealNodeOperationTransitions(tmpOldOnlineStatus);
          }
          for (NodeOperationTransition lastConcreteTransition : transitions) {
            breakLoop = evaluatePairsOfTransitions(tmpPath, tmpOldOnlineStatus, tmpOldLocalStates, tuple,
                new TransitionTuple(lastTransition.state, lastConcreteTransition)) || breakLoop;
          }
        } else if (!(lastTransition.transition instanceof AbstractNodeOperationTransition)
            && (tuple.transition instanceof AbstractNodeOperationTransition)) {
          LinkedList<NodeOperationTransition> transitions = null;
          if (tuple.transition instanceof AbstractNodeCrashTransition) {
            AbstractNodeCrashTransition abstractNodeCrashTransition = (AbstractNodeCrashTransition) tuple.transition;
            transitions = abstractNodeCrashTransition.getAllRealNodeOperationTransitions(tmpOldOnlineStatus);
          } else if (tuple.transition instanceof AbstractNodeStartTransition) {
            AbstractNodeStartTransition abstractNodeStartTransition = (AbstractNodeStartTransition) tuple.transition;
            transitions = abstractNodeStartTransition.getAllRealNodeOperationTransitions(tmpOldOnlineStatus);
          }
          for (NodeOperationTransition tupleConcreteTransition : transitions) {
            breakLoop = evaluatePairsOfTransitions(tmpPath, tmpOldOnlineStatus, tmpOldLocalStates,
                new TransitionTuple(tuple.state, tupleConcreteTransition), lastTransition) || breakLoop;
          }
        } else {
          LinkedList<NodeOperationTransition> lastTransitions = null;
          LinkedList<NodeOperationTransition> tupleTransitions = null;
          if (tuple.transition instanceof AbstractNodeCrashTransition) {
            AbstractNodeCrashTransition abstractNodeCrashTransition = (AbstractNodeCrashTransition) tuple.transition;
            tupleTransitions = abstractNodeCrashTransition.getAllRealNodeOperationTransitions();
          } else if (tuple.transition instanceof AbstractNodeStartTransition) {
            AbstractNodeStartTransition abstractNodeStartTransition = (AbstractNodeStartTransition) tuple.transition;
            tupleTransitions = abstractNodeStartTransition.getAllRealNodeOperationTransitions();
          }
          if (lastTransition.transition instanceof AbstractNodeCrashTransition) {
            AbstractNodeCrashTransition abstractNodeCrashTransition = (AbstractNodeCrashTransition) lastTransition.transition;
            lastTransitions = abstractNodeCrashTransition.getAllRealNodeOperationTransitions();
          } else if (lastTransition.transition instanceof AbstractNodeStartTransition) {
            AbstractNodeStartTransition abstractNodeStartTransition = (AbstractNodeStartTransition) lastTransition.transition;
            lastTransitions = abstractNodeStartTransition.getAllRealNodeOperationTransitions();
          }
          for (NodeOperationTransition tupleConcreteTransition : tupleTransitions) {
            for (NodeOperationTransition lastConcreteTransition : lastTransitions) {
              breakLoop = evaluatePairsOfTransitions(tmpPath, tmpOldOnlineStatus, tmpOldLocalStates,
                  new TransitionTuple(tuple.state, tupleConcreteTransition),
                  new TransitionTuple(lastTransition.state, lastConcreteTransition)) || breakLoop;
            }
          }
        }

        if (breakLoop) {
          break;
        }

      }
    }
  }

}
