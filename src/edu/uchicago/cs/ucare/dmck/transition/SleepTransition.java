package edu.uchicago.cs.ucare.dmck.transition;

@SuppressWarnings("serial")
public class SleepTransition extends NodeOperationTransition {

  private long sleep;

  public SleepTransition(long sleep) {
    this.sleep = sleep;
  }

  @Override
  public boolean apply() {
    try {
      Thread.sleep(sleep);
    } catch (InterruptedException e) {
      return false;
    }
    return true;
  }

  public long getSleepTime() {
    return sleep;
  }

  @Override
  public long getTransitionId() {
    return 0;
  }

  @Override
  public String toString() {
    return "sleep=" + sleep;
  }

  @Override
  public String toStringForFutureExecution() {
    return "sleep=" + sleep;
  }

}
