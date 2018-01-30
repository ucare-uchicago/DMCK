package edu.uchicago.cs.ucare.dmck.kudu;

import java.util.Properties;

import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

public class KuduFileWatcher extends FileWatcher {

  public KuduFileWatcher(String sPath, ModelCheckingServerAbstract dmck) {
    super(sPath, dmck);
  }

  @Override
  public void proceedEachFile(String filename, Properties ev) {
  }

}
