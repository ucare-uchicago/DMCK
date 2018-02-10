package edu.uchicago.cs.ucare.dmck.server;

import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.uchicago.cs.ucare.dmck.transition.Transition;

public class EventCausality {

  protected final Logger LOG = LoggerFactory.getLogger(this.getClass());

  private LinkedList<Transition> prevEvents;
  private LinkedList<Transition> directParents;
  private Transition child;

  public EventCausality(Transition event, List<Transition> list) {
    child = event;
    prevEvents = (LinkedList<Transition>) list;

    if (prevEvents == null) {
      directParents = null;
    }
  }

  public LinkedList<Transition> getPrevEvents() {
    return prevEvents;
  }

  public LinkedList<Transition> getDirectParents() {
    return directParents;
  }

  public Transition getChild() {
    return child;
  }

  public void setDirectParents(List<Transition> list) {
    directParents = (LinkedList<Transition>) list;

    String tmp = "Direct Parents of " + child.toString() + ":\n";
    for (Transition t : directParents) {
      tmp += t.toString() + "\n";
    }
    LOG.debug(tmp);
  }
}
