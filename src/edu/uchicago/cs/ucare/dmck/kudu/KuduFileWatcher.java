package edu.uchicago.cs.ucare.dmck.kudu;

import java.util.Properties;
import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.dmck.server.FileWatcher;
import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

public class KuduFileWatcher extends FileWatcher {

  public KuduFileWatcher(String sPath, ModelCheckingServerAbstract dmck) {
    super(sPath, dmck);
  }

  @Override
  public void proceedEachFile(String filename, Properties ev) {}

  @Override
  protected void sequencerEnablingSignal(Event packet) {
    // Since current DMCK integration with Kudu has not supported sequencer yet,
    // DMCK should just use common enabling signal function for now.
    commonEnablingSignal(packet);
  }

}
