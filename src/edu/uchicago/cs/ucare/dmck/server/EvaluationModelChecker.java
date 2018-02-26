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
import edu.uchicago.cs.ucare.dmck.util.LocalState;

public abstract class EvaluationModelChecker extends ReductionAlgorithmsModelChecker {

  public EvaluationModelChecker(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash,
      int numReboot, String globalStatePathDir, String packetRecordDir, String cacheDir,
      WorkloadDriver workloadDriver, String ipcDir) {
    super(dmckName, fileWatcher, numNode, numCrash, numReboot, globalStatePathDir, packetRecordDir,
        cacheDir, workloadDriver, ipcDir);
  }

  public abstract boolean isLMDependent(LocalState state, Event e1, Event e2);

  public abstract boolean isCMDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState,
      Event msg, NodeCrashTransition crash);

  public abstract boolean isCCDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState,
      NodeCrashTransition crash1, NodeCrashTransition crash2);

  public abstract boolean isCRSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalstate,
      Transition event);

  public abstract boolean isRSSDependent(boolean[] wasNodeOnline, LocalState[] wasLocalState,
      Transition event);

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
      if (isNodeOnline(crashEvent.getId())
          && isCRSDependent(isNodeOnline, localStates, crashEvent)) {
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
      if (!isNodeOnline(rebootEvent.getId())
          && isRSSDependent(isNodeOnline, localStates, rebootEvent)) {
        return -2;
      }
      recordPolicyEffectiveness("crs-rss-runtime");
      return -1;
    }
    return -2;
  }

  protected boolean isPossibleConcurrent(Transition lastTransition, Transition currentTransition) {
    // hack solution for multiple client requests for Cassandra system
    if (dmckName.equals("cassChecker") && lastTransition instanceof PacketSendTransition
        && currentTransition instanceof PacketSendTransition) {
      PacketSendTransition lt = (PacketSendTransition) lastTransition;
      PacketSendTransition tt = (PacketSendTransition) currentTransition;
      int lastCR1 = (int) lt.getPacket().getValue("clientRequest");
      int lastCR2 = (int) tt.getPacket().getValue("clientRequest");
      if (lastCR1 != lastCR2) {
        return true;
      }
    }
    if (lastTransition instanceof AbstractNodeCrashTransition) {
      AbstractNodeCrashTransition abstractCrash = (AbstractNodeCrashTransition) lastTransition;
      NodeCrashTransition realCrash = new NodeCrashTransition(this, abstractCrash.getId());
      realCrash.setVectorClock(abstractCrash.getPossibleVectorClock(abstractCrash.getId()));
      lastTransition = realCrash;
    } else if (lastTransition instanceof AbstractNodeStartTransition) {
      AbstractNodeStartTransition abstractStart = (AbstractNodeStartTransition) lastTransition;
      NodeStartTransition realStart = new NodeStartTransition(this, abstractStart.getId());
      realStart.setVectorClock(abstractStart.getPossibleVectorClock(abstractStart.getId()));
      lastTransition = realStart;
    }
    List<Transition> allBeforeTransitions = dependencies.get(lastTransition);
    if (allBeforeTransitions == null) {
      return false;
    } else {
      if (currentTransition instanceof AbstractNodeCrashTransition) {
        AbstractNodeCrashTransition abstractCrash = (AbstractNodeCrashTransition) currentTransition;
        NodeCrashTransition realCrash = new NodeCrashTransition(this, abstractCrash.getId());
        realCrash.setVectorClock(abstractCrash.getPossibleVectorClock(abstractCrash.getId()));
        currentTransition = realCrash;
      } else if (currentTransition instanceof AbstractNodeStartTransition) {
        AbstractNodeStartTransition abstractStart = (AbstractNodeStartTransition) currentTransition;
        NodeStartTransition realStart = new NodeStartTransition(this, abstractStart.getId());
        realStart.setVectorClock(abstractStart.getPossibleVectorClock(abstractStart.getId()));
        currentTransition = realStart;
      }
      return !allBeforeTransitions.contains(currentTransition);
    }
  }

  // return true = break loop, false = continue
  protected boolean evaluatePairsOfTransitions(Path tmpPath, boolean[] wasOnlineNodes,
      LocalState[] wasLocalStates, Transition currentTransition, Transition lastTransition) {
    if (lastTransition instanceof PacketSendTransition) {
      Event lastPacket = ((PacketSendTransition) lastTransition).getPacket();
      if (currentTransition instanceof PacketSendTransition) {
        if (isPossibleConcurrent(lastTransition, currentTransition)) {
          Event currentPacket = ((PacketSendTransition) currentTransition).getPacket();
          if (lastPacket.isObsolete() || currentPacket.isObsolete()) {
            return false;
          } else if (!wasOnlineNodes[lastPacket.getFromId()]) {
            return true;
          } else if (currentPacket.getToId() != lastPacket.getToId()
              || currentPacket.getFromId() != lastPacket.getFromId()) {
            // DPOR policy
            if (currentPacket.getToId() == lastPacket.getToId()) {
              int toId = currentPacket.getToId();
              // msg vs msg
              if (isLMDependent(wasLocalStates[toId], lastPacket, currentPacket)) {
                return addNewInitialPath(wasLocalStates, tmpPath, currentTransition,
                    lastTransition);
              }
            }
          } else if (currentPacket.getToId() == lastPacket.getToId()
              && currentPacket.getFromId() == lastPacket.getFromId()) {
            // if non-TCP paradigm, then evaluate possibility of
            // reordering
            if (!this.tcpParadigm) {
              if (isLMDependent(wasLocalStates[lastPacket.getToId()], lastPacket, currentPacket)) {
                return addNewInitialPath(wasLocalStates, tmpPath, currentTransition,
                    lastTransition);
              }
            } else {
              return true;
            }
          }
        }
      } else if (currentTransition instanceof NodeCrashTransition) {
        if (isPossibleConcurrent(lastTransition, currentTransition)) {
          NodeCrashTransition currentCrash = (NodeCrashTransition) currentTransition;
          if (lastPacket.getFromId() == currentCrash.getId()
              || lastPacket.getToId() == currentCrash.getId()) {
            if (wasOnlineNodes[lastPacket.getFromId()] && wasOnlineNodes[lastPacket.getToId()]) {
              if (isCMDependent(wasOnlineNodes, wasLocalStates, lastPacket, currentCrash)
                  && isNotDiscardReorder(wasOnlineNodes, wasLocalStates, currentCrash,
                      lastPacket)) {
                if (lastPacket.isObsolete()) {
                  lastPacket.setObsolete(false);
                }
                return addNewInitialPath(wasLocalStates, tmpPath, currentCrash, lastTransition);
              }
            }
          }
        } else {
          return true;
        }
      } else if (currentTransition instanceof NodeStartTransition) {
        if (isPossibleConcurrent(lastTransition, currentTransition)) {
          NodeStartTransition currentStart = (NodeStartTransition) currentTransition;
          if (!lastPacket.isObsolete()) {
            if (currentStart.getId() == lastPacket.getFromId()
                || currentStart.getId() == lastPacket.getToId()) {
              return true;
            }
            if (isNotDiscardReorder(wasOnlineNodes, wasLocalStates, currentStart, lastPacket)) {
              return addNewInitialPath(wasLocalStates, tmpPath, currentStart, lastTransition);
            }
          }
        } else {
          return true;
        }
      }
    } else if (lastTransition instanceof NodeCrashTransition) {
      NodeCrashTransition lastCrash = (NodeCrashTransition) lastTransition;
      if (wasOnlineNodes[lastCrash.getId()]) {
        boolean[] tempOnlineNodes = wasOnlineNodes.clone();
        tempOnlineNodes[lastCrash.getId()] = !wasOnlineNodes[lastCrash.getId()];
        if (currentTransition instanceof PacketSendTransition) {
          if (isPossibleConcurrent(lastTransition, currentTransition)) {
            Event currentPacket = ((PacketSendTransition) currentTransition).getPacket();
            if (currentPacket.getFromId() == lastCrash.getId()
                || currentPacket.getToId() == lastCrash.getId()) {
              if (isCRSDependent(wasOnlineNodes, wasLocalStates, lastCrash)
                  && isCMDependent(wasOnlineNodes, wasLocalStates, currentPacket, lastCrash)) {
                addNewInitialPath(wasLocalStates, tmpPath, currentTransition, lastCrash);
                if (isSAMC) {
                  // If SAMC, record crash to history, although DMCK does not know the impact of the
                  // crash.
                  addEventToHistory(copyLocalState(wasLocalStates), null, lastCrash, null);
                }
                return true;
              }
            }
          } else {
            return true;
          }
        } else if (currentTransition instanceof NodeCrashTransition) {
          NodeCrashTransition currentCrash = (NodeCrashTransition) currentTransition;
          if (tempOnlineNodes[currentCrash.getId()]) {
            // only reorder if both nodes are possible to be crashed
            if (isCRSDependent(wasOnlineNodes, wasLocalStates, lastCrash)
                && isCCDependent(wasOnlineNodes, wasLocalStates, currentCrash, lastCrash)) {
              addNewInitialPath(wasLocalStates, tmpPath, currentCrash, lastCrash);
              if (isSAMC) {
                // If SAMC, record crash to history, although DMCK does not know the impact of the
                // crash.
                addEventToHistory(copyLocalState(wasLocalStates), null, lastCrash, null);
              }
              return true;
            }
          }
        } else if (currentTransition instanceof NodeStartTransition) {
          NodeStartTransition currentStart = (NodeStartTransition) currentTransition;
          if (!tempOnlineNodes[currentStart.getId()]) {
            // only reorder if both nodes are possible to be crashed
            if (isCRSDependent(wasOnlineNodes, wasLocalStates, lastCrash)) {
              addNewInitialPath(wasLocalStates, tmpPath, currentStart, lastCrash);
              if (isSAMC) {
                // If SAMC, record crash to history, although DMCK does not know the impact of the
                // crash.
                addEventToHistory(copyLocalState(wasLocalStates), null, lastCrash, null);
              }
              return true;
            }
          }
        }
      }
    } else if (lastTransition instanceof NodeStartTransition) {
      NodeStartTransition lastStart = (NodeStartTransition) lastTransition;
      if (!wasOnlineNodes[lastStart.getId()]) {
        boolean[] tempOnlineNodes = wasOnlineNodes.clone();
        tempOnlineNodes[lastStart.getId()] = !wasOnlineNodes[lastStart.getId()];
        if (currentTransition instanceof PacketSendTransition) {
          if (isPossibleConcurrent(lastTransition, currentTransition)) {
            if (isRSSDependent(wasOnlineNodes, wasLocalStates, lastStart)) {
              addNewInitialPath(wasLocalStates, tmpPath, currentTransition, lastStart);
              if (isSAMC) {
                // If SAMC, record reboot to history, although DMCK does not know the impact of the
                // reboot.
                addEventToHistory(copyLocalState(wasLocalStates), null, lastStart, null);
              }
            }
          } else {
            return true;
          }
        } else if (currentTransition instanceof NodeCrashTransition) {
          NodeCrashTransition currentCrash = (NodeCrashTransition) currentTransition;
          if (tempOnlineNodes[currentCrash.getId()]) {
            // only reorder if both nodes are possible to be rebooted
            if (isRSSDependent(wasOnlineNodes, wasLocalStates, lastStart)) {
              addNewInitialPath(wasLocalStates, tmpPath, currentCrash, lastStart);
              if (isSAMC) {
                // If SAMC, record reboot to history, although DMCK does not know the impact of the
                // reboot.
                addEventToHistory(copyLocalState(wasLocalStates), null, lastStart, null);
              }
              return true;
            }
          }
        } else if (currentTransition instanceof NodeStartTransition) {
          NodeStartTransition currentStart = (NodeStartTransition) currentTransition;
          if (!tempOnlineNodes[currentStart.getId()]) {
            // only reorder if both nodes are possible to be crashed
            if (isRSSDependent(wasOnlineNodes, wasLocalStates, lastStart)) {
              addNewInitialPath(wasLocalStates, tmpPath, currentStart, lastStart);
              if (isSAMC) {
                // If SAMC, record reboot to history, although DMCK does not know the impact of the
                // reboot.
                addEventToHistory(copyLocalState(wasLocalStates), null, lastStart, null);
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
  @SuppressWarnings({"unchecked", "unlikely-arg-type"})
  protected void reevaluateInitialPaths(Path initialPath, LocalState[] wasLocalStates,
      Transition lastEvent) {
    if (lastEvent instanceof NodeCrashTransition
        || lastEvent instanceof AbstractNodeCrashTransition) {
      LinkedList<Path> initialPathsClone = (LinkedList<Path>) currentInitialPaths.clone();
      Iterator<Path> initialPathsCloneIterator = initialPathsClone.iterator();
      while (initialPathsCloneIterator.hasNext()) {
        Path comparePath = initialPathsCloneIterator.next();
        if (comparePath.contains(initialPath) && comparePath.size() == initialPath.size() + 1) {
          Transition lastEventOfComparePath = comparePath.getLast();
          if (lastEventOfComparePath instanceof NodeCrashTransition) {
            NodeCrashTransition lastCrashCompare = (NodeCrashTransition) lastEventOfComparePath;
            if (isSymmetric(wasLocalStates, lastCrashCompare)) {
              LOG.debug(
                  "Remove an Initial Path from currentInitialPaths because it has symmetrical crash.");
              currentInitialPaths.remove(comparePath);
            }
          }
        }
      }
    } else if (lastEvent instanceof NodeStartTransition
        || lastEvent instanceof AbstractNodeStartTransition) {
      LinkedList<Path> initialPathsClone = (LinkedList<Path>) currentInitialPaths.clone();
      Iterator<Path> initialPathsCloneIterator = initialPathsClone.iterator();
      while (initialPathsCloneIterator.hasNext()) {
        Path comparePath = initialPathsCloneIterator.next();
        if (comparePath.contains(initialPath) && comparePath.size() == initialPath.size() + 1) {
          Transition lastEventOfComparePath = comparePath.getLast();
          if (lastEventOfComparePath instanceof NodeStartTransition) {
            NodeStartTransition lastStartCompare = (NodeStartTransition) lastEventOfComparePath;
            if (isSymmetric(wasLocalStates, lastStartCompare)) {
              LOG.debug(
                  "Remove an Initial Path from currentInitialPaths because it has symmetrical reboot.");
              currentInitialPaths.remove(comparePath);
            }
          }
        }
      }
    }
  }

  @Override
  protected void backtrackExecutedPath() {
    Transition lastTransition;
    while ((lastTransition = currentExploringPath.pollLast()) != null) {
      if (hasDirectedInitialPath && currentExploringPath.size() < directedInitialPath.size()) {
        break;
      }
      boolean[] oldOnlineStatus = prevOnlineStatus.removeLast();
      LocalState[] oldLocalStates = prevLocalStates.removeLast();
      Path tmpPath = currentExploringPath.clone();

      // reevaluate currentInitialPaths with CRS and RSS
      reevaluateInitialPaths(tmpPath, copyLocalState(oldLocalStates), lastTransition);

      // check lastTransition instance, if it is crash or reboot on X
      // node, then add other X node possibilities
      if (lastTransition instanceof AbstractNodeCrashTransition) {
        AbstractNodeCrashTransition abstractNodeCrashTransition =
            (AbstractNodeCrashTransition) lastTransition;
        LinkedList<NodeOperationTransition> transitions =
            abstractNodeCrashTransition.getAllRealNodeOperationTransitions(oldOnlineStatus);
        for (NodeOperationTransition t : transitions) {
          if (abstractNodeCrashTransition.getId() != t.getId()) {
            // check CRS
            if (isCRSDependent(oldOnlineStatus, oldLocalStates, t)) {
              Path interestingPath = tmpPath.clone();
              interestingPath.addTransition(t);
              addToInitialPathList(interestingPath);
              // If SAMC, record crash to history, although DMCK does not know the impact of the
              // crash.
              if (isSAMC) {
                addEventToHistory(copyLocalState(oldLocalStates), null, t, null);
              }
            }
          }
        }
      } else if (lastTransition instanceof AbstractNodeStartTransition) {
        AbstractNodeStartTransition abstractNodeStartTransition =
            (AbstractNodeStartTransition) lastTransition;
        LinkedList<NodeOperationTransition> transitions =
            ((AbstractNodeStartTransition) lastTransition)
                .getAllRealNodeOperationTransitions(oldOnlineStatus);
        for (NodeOperationTransition t : transitions) {
          if (abstractNodeStartTransition.getId() != t.getId()) {
            // check RSS
            if (isRSSDependent(oldOnlineStatus, oldLocalStates, t)) {
              Path interestingPath = tmpPath.clone();
              interestingPath.addTransition(t);
              addToInitialPathList(interestingPath);
              // If SAMC, record reboot to history, although DMCK does not know the impact of the
              // reboot.
              if (isSAMC) {
                addEventToHistory(copyLocalState(oldLocalStates), null, t, null);
              }
            }
          }
        }
      }

      int reverseCounter = currentExploringPath.size();
      Iterator<Transition> reverseIter = currentExploringPath.descendingIterator();
      Iterator<LocalState[]> reverseLocalStateIter = prevLocalStates.descendingIterator();
      Iterator<boolean[]> reverseOnlineStatusIter = prevOnlineStatus.descendingIterator();
      while (reverseIter.hasNext()) {
        if (hasDirectedInitialPath && reverseCounter <= directedInitialPath.size()) {
          break;
        }
        Transition currentTransition = reverseIter.next();
        LocalState[] tmpOldLocalStates = reverseLocalStateIter.next();
        boolean[] tmpOldOnlineStatus = reverseOnlineStatusIter.next();
        reverseCounter--;
        tmpPath.pollLast();

        // transform abstract events to concrete events
        boolean breakLoop = false;
        if (!(lastTransition instanceof AbstractNodeOperationTransition)
            && !(currentTransition instanceof AbstractNodeOperationTransition)) {
          breakLoop = evaluatePairsOfTransitions(tmpPath, tmpOldOnlineStatus, tmpOldLocalStates,
              currentTransition, lastTransition);
        } else if ((lastTransition instanceof AbstractNodeOperationTransition)
            && !(currentTransition instanceof AbstractNodeOperationTransition)) {
          LinkedList<NodeOperationTransition> transitions = null;
          if (lastTransition instanceof AbstractNodeCrashTransition) {
            AbstractNodeCrashTransition abstractNodeCrashTransition =
                (AbstractNodeCrashTransition) lastTransition;
            transitions =
                abstractNodeCrashTransition.getAllRealNodeOperationTransitions(tmpOldOnlineStatus);
          } else if (lastTransition instanceof AbstractNodeStartTransition) {
            AbstractNodeStartTransition abstractNodeStartTransition =
                (AbstractNodeStartTransition) lastTransition;
            transitions =
                abstractNodeStartTransition.getAllRealNodeOperationTransitions(tmpOldOnlineStatus);
          }
          for (NodeOperationTransition lastConcreteTransition : transitions) {
            breakLoop = evaluatePairsOfTransitions(tmpPath, tmpOldOnlineStatus, tmpOldLocalStates,
                currentTransition, lastConcreteTransition) || breakLoop;
          }
        } else if (!(lastTransition instanceof AbstractNodeOperationTransition)
            && (currentTransition instanceof AbstractNodeOperationTransition)) {
          LinkedList<NodeOperationTransition> transitions = null;
          if (currentTransition instanceof AbstractNodeCrashTransition) {
            AbstractNodeCrashTransition abstractNodeCrashTransition =
                (AbstractNodeCrashTransition) currentTransition;
            transitions =
                abstractNodeCrashTransition.getAllRealNodeOperationTransitions(tmpOldOnlineStatus);
          } else if (currentTransition instanceof AbstractNodeStartTransition) {
            AbstractNodeStartTransition abstractNodeStartTransition =
                (AbstractNodeStartTransition) currentTransition;
            transitions =
                abstractNodeStartTransition.getAllRealNodeOperationTransitions(tmpOldOnlineStatus);
          }
          for (NodeOperationTransition concreteTransition : transitions) {
            breakLoop = evaluatePairsOfTransitions(tmpPath, tmpOldOnlineStatus, tmpOldLocalStates,
                concreteTransition, lastTransition) || breakLoop;
          }
        } else {
          LinkedList<NodeOperationTransition> lastTransitions = null;
          LinkedList<NodeOperationTransition> currentTransitions = null;
          if (currentTransition instanceof AbstractNodeCrashTransition) {
            AbstractNodeCrashTransition abstractNodeCrashTransition =
                (AbstractNodeCrashTransition) currentTransition;
            currentTransitions = abstractNodeCrashTransition.getAllRealNodeOperationTransitions();
          } else if (currentTransition instanceof AbstractNodeStartTransition) {
            AbstractNodeStartTransition abstractNodeStartTransition =
                (AbstractNodeStartTransition) currentTransition;
            currentTransitions = abstractNodeStartTransition.getAllRealNodeOperationTransitions();
          }
          if (lastTransition instanceof AbstractNodeCrashTransition) {
            AbstractNodeCrashTransition abstractNodeCrashTransition =
                (AbstractNodeCrashTransition) lastTransition;
            lastTransitions = abstractNodeCrashTransition.getAllRealNodeOperationTransitions();
          } else if (lastTransition instanceof AbstractNodeStartTransition) {
            AbstractNodeStartTransition abstractNodeStartTransition =
                (AbstractNodeStartTransition) lastTransition;
            lastTransitions = abstractNodeStartTransition.getAllRealNodeOperationTransitions();
          }
          for (NodeOperationTransition concreteTransition : currentTransitions) {
            for (NodeOperationTransition lastConcreteTransition : lastTransitions) {
              breakLoop = evaluatePairsOfTransitions(tmpPath, tmpOldOnlineStatus, tmpOldLocalStates,
                  concreteTransition, lastConcreteTransition) || breakLoop;
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
