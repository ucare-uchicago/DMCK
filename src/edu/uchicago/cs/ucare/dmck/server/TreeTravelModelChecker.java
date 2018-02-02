package edu.uchicago.cs.ucare.dmck.server;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;
import com.almworks.sqlite4java.SQLiteException;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.ExploredBranchRecorder;
import edu.uchicago.cs.ucare.dmck.util.SqliteExploredBranchRecorder;

public abstract class TreeTravelModelChecker extends ModelCheckingServerAbstract {

  protected String stateDir;
  protected ExploredBranchRecorder exploredBranchRecorder;
  protected int numCrash;
  protected int numReboot;
  protected int currentCrash;
  protected int currentReboot;

  public TreeTravelModelChecker(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash,
      int numReboot, String globalStatePathDir, String packetRecordDir, String workingDir,
      WorkloadDriver workloadDriver, String ipcDir) {
    super(dmckName, fileWatcher, numNode, globalStatePathDir, workingDir, workloadDriver, ipcDir);
    try {
      this.numCrash = numCrash;
      this.numReboot = numReboot;
      this.stateDir = packetRecordDir;
      exploredBranchRecorder = new SqliteExploredBranchRecorder(packetRecordDir);
    } catch (SQLiteException e) {
      LOG.error("", e);
    }
    resetTest();
  }

  abstract public Transition nextTransition(LinkedList<Transition> transitions);

  @Override
  public void resetTest() {
    if (exploredBranchRecorder == null) {
      return;
    }
    super.resetTest();
    modelChecking = new PathTraversalWorker();
    currentEnabledTransitions = new LinkedList<Transition>();
    currentCrash = 0;
    currentReboot = 0;
    exploredBranchRecorder.resetTraversal();
    signalWaitingFile();
  }

  protected void signalWaitingFile() {
    File waiting = new File(stateDir + "/.waiting");
    try {
      waiting.createNewFile();
    } catch (IOException e) {
      LOG.error("", e);
    }
  }

  protected void adjustCrashAndReboot(LinkedList<Transition> transitions) {
    if (numCurrentCrash < numCrash) {
      for (int i = 0; i < isNodeOnline.length; ++i) {
        if (isNodeOnline(i)) {
          // only add crash event if the crash event doesn't exist
          NodeCrashTransition crashEvent = new NodeCrashTransition(this, i);
          crashEvent.setVectorClock(vectorClocks[i][numNode]);
          if (!transitions.contains(crashEvent)) {
            transitions.add(crashEvent);
          }
          // if existing transitions has startTransition node i in the
          // list,
          // but the node i now is already online, then remove the
          // start node i events
          for (int j = transitions.size() - 1; j >= 0; j--) {
            if (transitions.get(j) instanceof NodeStartTransition
                && ((NodeStartTransition) transitions.get(j)).getId() == i) {
              LOG.debug("Remove event: " + transitions.get(j).toString());
              transitions.remove(j);
            }
          }
        }
      }
    } else {
      ListIterator<Transition> iter = transitions.listIterator();
      while (iter.hasNext()) {
        if (iter.next() instanceof NodeCrashTransition) {
          iter.remove();
        }
      }
    }
    if (numCurrentReboot < numReboot) {
      for (int i = 0; i < isNodeOnline.length; ++i) {
        if (!isNodeOnline(i)) {
          // only add crash event if the crash event doesn't exist
          NodeStartTransition rebootEvent = new NodeStartTransition(this, i);
          rebootEvent.setVectorClock(vectorClocks[i][numNode]);
          if (!transitions.contains(rebootEvent)) {
            transitions.add(rebootEvent);
          }
        }
      }
    } else {
      ListIterator<Transition> iter = transitions.listIterator();
      while (iter.hasNext()) {
        if (iter.next() instanceof NodeStartTransition) {
          iter.remove();
        }
      }
    }
  }

  protected void recordTestId() {
    exploredBranchRecorder.noteThisNode(".test_id", testId + "");
  }

  class PathTraversalWorker extends Thread {
    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      boolean hasExploredAll = false;
      boolean hasWaited = waitEndExploration == 0;
      LinkedList<LinkedList<Transition>> pastEnabledTransitionList =
          new LinkedList<LinkedList<Transition>>();
      while (true) {
        executeMidWorkload();
        updateSAMCQueue();
        boolean terminationPoint = checkTerminationPoint(currentEnabledTransitions);
        if (terminationPoint && hasWaited) {
          LOG.info("---- End of a Path Execution ----");

          // Performance evaluation
          collectPerformancePerEventMetrics();
          collectPerformancePerPathMetrics();

          boolean verifiedResult = verifier.verify();
          String detail = verifier.verificationDetail();
          saveResult(verifiedResult + "; " + detail + "\n");
          recordTestId();
          exploredBranchRecorder.markBelowSubtreeFinished();
          int currentStep = 0;
          for (LinkedList<Transition> pastTransitions : pastEnabledTransitionList) {
            currentStep++;
            if (currentStep <= pastEnabledTransitionList.size() - directedInitialPath.size()) {
              exploredBranchRecorder.traverseUpward(1);
              Transition transition = nextTransition(pastTransitions);
              if (transition == null) {
                exploredBranchRecorder.markBelowSubtreeFinished();
                hasExploredAll = true;
              } else {
                hasExploredAll = false;
                break;
              }
            } else {
              break;
            }
          }
          LOG.info("---- End of Path Evaluation ----");
          if (!hasExploredAll) {
            resetTest();
          } else if (exploredBranchRecorder.getCurrentDepth() == 0) {
            hasFinishedAllExploration = true;
            signalWaitingFile();
          }
          break;
        } else if (terminationPoint) {
          try {
            if (dmckName.equals("raftModelChecker") && waitForNextLE
                && waitedForNextLEInDiffTermCounter < 20) {
              Thread.sleep(leaderElectionTimeout);
            } else {
              hasWaited = true;
              LOG.debug("Wait for any long process");
              Thread.sleep(waitEndExploration);
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          continue;
        }
        pastEnabledTransitionList
            .addFirst((LinkedList<Transition>) currentEnabledTransitions.clone());
        Transition transition;
        boolean recordPath = true;
        if (hasDirectedInitialPath && !hasFinishedDirectedInitialPath) {
          transition = nextInitialTransition();
          recordPath = false;
        } else {
          transition = nextTransition(currentEnabledTransitions);
        }
        if (transition != null) {
          if (recordPath) {
            exploredBranchRecorder.createChild(transition.getTransitionId());
            exploredBranchRecorder.traverseDownTo(transition.getTransitionId());
            exploredBranchRecorder.noteThisNode(".packet", transition.toString());
          }
          LOG.info("[NEXT TRANSITION] " + transition.toString());
          collectDebugNextTransition(transition);
          try {
            if (transition.apply()) {
              pathRecordFile.write((transition.toString() + "\n").getBytes());
              updateSAMCQueueAfterEventExecution(transition);
            } else {
              LOG.warn(transition.toString() + " IS NOT EXECUTED!");
            }
          } catch (Exception e) {
            LOG.error("", e);
          }
        } else {
          if (!hasWaited) {
            try {
              Thread.sleep(leaderElectionTimeout);
              if (waitedForNextLEInDiffTermCounter >= 20) {
                hasWaited = true;
              }
            } catch (InterruptedException e) {
              LOG.error("", e);
            }
            continue;
          } else {
            LOG.error("There might be some errors");
            hasFinishedAllExploration = true;
            signalWaitingFile();
            break;
          }
        }
      }
    }
  }

}
