package edu.uchicago.cs.ucare.dmck.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;

public class GuideModelChecker extends ModelCheckingServerAbstract {

  protected ProgramParser parser;
  protected LinkedList<Event> enabledPackets;
  protected Thread afterProgramModelChecker;
  protected File program;

  public GuideModelChecker(String interceptorName, FileWatcher fileWatcher, int numNode, String globalStatePathDir,
      File program, String workingDir, WorkloadDriver workloadDriver, String ipcDir) throws FileNotFoundException {
    super(interceptorName, fileWatcher, numNode, globalStatePathDir, workingDir, workloadDriver, ipcDir);
    this.program = program;
    afterProgramModelChecker = null;
    resetTest();
  }

  @Override
  public void resetTest() {
    super.resetTest();
    try {
      if (program != null) {
        parser = new ProgramParser(this, program);
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e.getMessage());
    }
    modelChecking = new ProgramExecutor(this);
    enabledPackets = new LinkedList<Event>();
  }

  class ProgramExecutor extends ModelCheckingServerAbstract.Explorer {

    public ProgramExecutor(ModelCheckingServerAbstract dmck) {
      super(dmck);
    }

    @Override
    public void run() {
      InstructionTransition instruction;
      while (parser != null && (instruction = parser.readNextInstruction()) != null) {
        getOutstandingEventTransition(currentEnabledTransitions);
        printTransitionQueues(currentEnabledTransitions);
        // raft specific
        if (dmckName.equals("raftModelChecker")) {
          while (checkTerminationPoint(currentEnabledTransitions)) {
            try {
              if (dmckName.equals("raftModelChecker") && waitForNextLE && waitedForNextLEInDiffTermCounter < 20) {
                Thread.sleep(leaderElectionTimeout);
              }
              break;
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
        }

        Transition transition = instruction.getRealTransition(dmck);
        if (transition == null) {
          break;
        }
        if (transition.apply()) {
          updateGlobalState();
          updateSAMCQueueAfterEventExecution(transition);
        } else {

        }
        if (transition instanceof PacketSendTransition) {
          currentEnabledTransitions.remove(transition);
        }
      }

      if (afterProgramModelChecker != null) {
        afterProgramModelChecker.start();
      } else {
        LOG.debug("Events left in Queue:");
        printTransitionQueues(currentEnabledTransitions);

        boolean verifiedResult = verifier.verify();
        String detail = verifier.verificationDetail();
        saveResult(verifiedResult + " ; " + detail + "\n");

        dmck.stopEnsemble();
        System.exit(0);
      }
    }

  }

  class ProgramParser {

    BufferedReader programReader;

    public ProgramParser(ModelCheckingServerAbstract dmck, File program) throws FileNotFoundException {
      this.programReader = new BufferedReader(new FileReader(program));
    }

    public InstructionTransition readNextInstruction() {
      try {
        String transitionString = programReader.readLine();
        if (transitionString == null) {
          return null;
        }
        String[] tokens = transitionString.split(" ");
        if (tokens[0].equals("packetsend")) {
          String packetTransitionIdString = tokens[1].split("=")[1];
          if (packetTransitionIdString.equals("*")) {
            return new PacketSendInstructionTransition(0);
          } else {
            long packetTransitionId = Long.parseLong(packetTransitionIdString);
            return new PacketSendInstructionTransition(packetTransitionId);
          }
        } else if (tokens[0].equals("nodecrash")) {
          int id = Integer.parseInt(tokens[1].split("=")[1]);
          return new NodeCrashInstructionTransition(id);
        } else if (tokens[0].equals("nodestart")) {
          int id = Integer.parseInt(tokens[1].split("=")[1]);
          return new NodeStartInstructionTransition(id);
        } else if (tokens[0].equals("sleep")) {
          long sleep = Long.parseLong(tokens[1].split("=")[1]);
          return new SleepInstructionTransition(sleep);
        } else if (tokens[0].equals("stop")) {
          return new ExitInstructionTransaction();
        }
      } catch (IOException e) {
        return null;
      }
      return null;
    }

  }

  @Override
  protected void adjustCrashAndReboot(LinkedList<Transition> transitions) {
    // TODO Auto-generated method stub
  }

}