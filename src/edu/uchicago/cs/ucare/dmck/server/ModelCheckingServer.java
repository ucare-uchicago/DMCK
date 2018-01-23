package edu.uchicago.cs.ucare.dmck.server;

import edu.uchicago.cs.ucare.dmck.event.Event;

public interface ModelCheckingServer {

  public void offerPacket(Event packet);

  public void setTestId(int testId);

  public void updateLocalState(int nodeId, int state);

  public void informActiveState(int id);

  public void informSteadyState(int id, int runningState);

}
