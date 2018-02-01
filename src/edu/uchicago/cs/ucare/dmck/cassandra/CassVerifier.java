package edu.uchicago.cs.ucare.dmck.cassandra;

import java.io.FileInputStream;
import java.util.Properties;
import org.apache.log4j.Logger;
import edu.uchicago.cs.ucare.dmck.server.SpecVerifier;

public class CassVerifier extends SpecVerifier {

  private static final Logger LOG = Logger.getLogger(CassVerifier.class);

  private Properties kv = new Properties();

  private String errorType = "";

  private String owner;
  private String value_1;
  private String value_2;
  private String applied_1, applied_2;

  @Override
  public boolean verify() {
    boolean result = true;
    if (!checkDataConsistency()) {
      result = false;
    }
    return result;
  }

  @Override
  public String verificationDetail() {
    String result = "";
    result += "owner=" + owner + " ;";
    result += " value_1=" + value_1 + " ;";
    result += " value_2=" + value_2 + " ;";
    result += " applied_1=" + applied_1 + " ;";
    result += " applied_2=" + applied_2 + " ;";

    result += "\n";
    result += errorType;
    return result;
  }

  private boolean checkDataConsistency() {
    try {
      LOG.debug("Executing checkDataConsistency script.");
      Runtime.getRuntime().exec(modelCheckingServer.workingDirPath + "/checkConsistency.sh")
          .waitFor();

      LOG.debug("Read DataConsistency file.");
      FileInputStream verifyInputStream =
          new FileInputStream(modelCheckingServer.workingDirPath + "/temp-verify");
      kv.load(verifyInputStream);
      owner = kv.getProperty("owner");
      value_1 = kv.getProperty("value_1");
      value_2 = kv.getProperty("value_2");

      applied_1 = this.modelCheckingServer.workloadHasApplied.containsKey(1)
          ? this.modelCheckingServer.workloadHasApplied.get(1)
          : "false";
      applied_2 = this.modelCheckingServer.workloadHasApplied.containsKey(2)
          ? this.modelCheckingServer.workloadHasApplied.get(2)
          : "false";

      if (value_1.equals("A") && (value_2.equals("B"))) {
        errorType = "Cass-6023";
        return false;
      }

      if (owner.equals("user_2") && applied_1.equals("false")) {
        errorType = "Cass-6013";
        return false;
      }

      return true;
    } catch (Exception e) {
      LOG.error("Failed to check data consistency.");
      LOG.error(e);
      return true;
    }

  }

}
