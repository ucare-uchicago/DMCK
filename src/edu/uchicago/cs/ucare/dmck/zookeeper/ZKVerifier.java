package edu.uchicago.cs.ucare.dmck.zookeeper;

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

import edu.uchicago.cs.ucare.dmck.server.SpecVerifier;
import edu.uchicago.cs.ucare.dmck.util.LocalState;

public class ZKVerifier extends SpecVerifier {

  private static final Logger LOG = Logger.getLogger(ZKVerifier.class);

  private static final String MORE_THAN_ONE_LEADER = "There is more than one leader in the system.\n";
  private static final String INCONSISTENT_VALUES = "Replicas are inconsistent.\n";
  private static final String NO_LEADER = "No leader is in place.\n";

  private int numLeader;
  private String errorType = "";

  private Properties kv = new Properties();

  @Override
  public boolean verify() {
    boolean result = true;
    LOG.info("DMCK verify the Zookeeper State");
    updateGlobalState();
    if (numLeader < 1) {
      errorType += NO_LEADER;
      result = false;
    }
    if (modelCheckingServer.hasInitWorkload) {
      if (!checkDataConsistency()) {
        errorType += INCONSISTENT_VALUES;
        result = false;
      }
    }
    if (numLeader > 1) {
      errorType += MORE_THAN_ONE_LEADER;
      result = false;
    }
    return result;
  }

  @Override
  public String verificationDetail() {
    String result = "\n";
    for (int node = 0; node < modelCheckingServer.numNode; node++) {
      LocalState state = this.modelCheckingServer.localStates[node];
      result += "  node-" + node + " state:" + state.getValue("state") + " with proposedLeader:"
          + state.getValue("proposedLeader");
      if (this.modelCheckingServer.numInitWorkload > 0) {
        result += " key=/foo value=" + kv.getProperty(String.valueOf(node));
      }
      result += "\n";
    }

    result += errorType;

    return result;
  }

  private void updateGlobalState() {
    numLeader = 0;
    errorType = "";
    for (int node = 0; node < modelCheckingServer.numNode; node++) {
      LocalState state = this.modelCheckingServer.localStates[node];
      switch ((int) state.getValue("state")) {
        case 2: // LEADING
          numLeader++;
          break;
      }
    }
  }

  private boolean checkDataConsistency() {
    String nodesAliveness = "";
    for (int i = 0; i < modelCheckingServer.numNode; i++) {
      if (i != 0) {
        nodesAliveness += ",";
      }
      nodesAliveness += modelCheckingServer.isNodeOnline[i] ? "1" : "0";
    }
    try {
      LOG.debug("Executing checkDataConsistency script.");
      Runtime.getRuntime().exec(modelCheckingServer.workingDirPath + "/checkConsistency.sh "
          + modelCheckingServer.numNode + " /foo " + nodesAliveness).waitFor();

      LOG.debug("Read DataConsistency file.");
      FileInputStream verifyInputStream = new FileInputStream(modelCheckingServer.workingDirPath + "/temp-verify");
      kv.load(verifyInputStream);

      String[] values = new String[modelCheckingServer.numNode];
      for (int i = 0; i < modelCheckingServer.numNode; i++) {
        values[i] = kv.getProperty(String.valueOf(i));
        if (i == 0) {
          if (values[i].equals("-null-") || values[i].equals("-dead-") || values[i].equals("-exception-")) {
            return false;
          }
        } else {
          if (!values[i - 1].equals(values[i])) {
            return false;
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to check data consistency.");
      return false;
    }
    return true;
  }

}
