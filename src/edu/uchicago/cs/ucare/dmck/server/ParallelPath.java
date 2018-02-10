package edu.uchicago.cs.ucare.dmck.server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.VectorClockUtil;

public class ParallelPath implements Serializable {

  private static final long serialVersionUID = 1L;

  protected final Logger LOG = LoggerFactory.getLogger(this.getClass());

  private Path path;
  private ArrayList<Transition> reorderedEvents;
  private ArrayList<Integer> reorderedNodes;

  // private LinkedList<EventCausality> eventCausality;
  private Hashtable<Transition, List<Transition>> dependencies;

  // riza
  private int pathId;
  private int firstParentId;
  private int secondParentId;

  public ParallelPath(Path newPath, Hashtable<Transition, List<Transition>> dependencies) {
    path = newPath.clone();

    Transition oldTransition = newPath.get(newPath.size() - 1);
    Transition newTransition = newPath.get(newPath.size() - 2);
    reorderedEvents = new ArrayList<Transition>();
    reorderedEvents.add(oldTransition);
    reorderedEvents.add(newTransition);

    reorderedNodes = new ArrayList<Integer>();
    if (oldTransition instanceof PacketSendTransition) {
      reorderedNodes.add(((PacketSendTransition) oldTransition).getPacket().getToId());
    } else if (oldTransition instanceof NodeOperationTransition) {
      reorderedNodes.add(((NodeOperationTransition) oldTransition).getId());
    } else {
      LOG.warn("Reordered Nodes are empty due to undefined class of events.");
    }

    // buildCausalityEventsChain(dependencies);
    this.dependencies = dependencies;

    // riza
    this.pathId = newPath.getId();
    this.firstParentId = newPath.getParentId();
    this.secondParentId = newPath.getParentId();
  }

  public ParallelPath(Path newPath, ArrayList<Transition> events, ArrayList<Integer> nodes,
      Hashtable<Transition, List<Transition>> dependencies) {
    path = newPath;
    reorderedEvents = events;
    reorderedNodes = nodes;
    this.dependencies = dependencies;

    // riza
    this.pathId = newPath.getId();
    this.firstParentId = newPath.getParentId();
    this.secondParentId = newPath.getParentId();
  }

  public Path getPath() {
    return path;
  }

  public ArrayList<Transition> getReorderedEvents() {
    return reorderedEvents;
  }

  public ArrayList<Integer> getReorderedNodes() {
    return reorderedNodes;
  }

  public Transition getEventTransition(int p) {
    return path.get(p);
  }

  public Hashtable<Transition, List<Transition>> getDependencies() {
    return dependencies;
  }

  public void setPath(Path newPath) {
    this.path = newPath;
  }

  public void addMoreReorderedEvents(ArrayList<Transition> reorderedEvents) {
    this.reorderedEvents.addAll(reorderedEvents);
  }

  public void addMoreReorderedNodes(ArrayList<Integer> reorderedNodes) {
    this.reorderedNodes.addAll(reorderedNodes);
  }

  public ParallelPath combineOtherPath(ParallelPath otherPath) {

    // filter 1: cannot combine paths with same reordered nodes
    for (int otherNode : otherPath.getReorderedNodes()) {
      for (int myNode : this.reorderedNodes) {
        if (otherNode == myNode) {
          LOG.debug("Cannot combine paths because they are processed in the same node: "
              + this.reorderedNodes.toString() + " vs " + otherPath.getReorderedNodes().toString());
          return null;
        }
      }
    }

    for (Transition ev1 : this.reorderedEvents) {
      for (Transition ev2 : otherPath.getReorderedEvents()) {
        if (ev1.getTransitionId() == ev2.getTransitionId()) {
          LOG.debug("Cannot combine paths that both have at least one same reordered events.");
          return null;
        }
        if (VectorClockUtil.isConcurrent(ev1.getVectorClock(), ev2.getVectorClock()) != 0) {
          if (ev1 instanceof PacketSendTransition && ev2 instanceof PacketSendTransition) {
            PacketSendTransition msg1 = (PacketSendTransition) ev1;
            PacketSendTransition msg2 = (PacketSendTransition) ev2;
            try {
              int clientRequest1 = (int) msg1.getPacket().getValue("clientRequest");
              int clientRequest2 = (int) msg2.getPacket().getValue("clientRequest");
              if (clientRequest1 != clientRequest2) {
                continue;
              }
            } catch (Exception ex) {
              LOG.error(ex.toString());
              return null;
            }
          } else {
            LOG.debug("For now, cannot combine paths that consist of crash / reboot event");
            return null;
          }

          LOG.debug(
              "Cannot combine paths because the reordered events are dependent to one another.");
          return null;
        }
      }
    }

    Path combinePath = new Path();
    int minimumPath = this.path.size() < otherPath.getPath().size() ? this.path.size()
        : otherPath.getPath().size();
    int startingDiff = -1;
    for (int k = 0; k < minimumPath; k++) {
      if (this.path.get(k).getTransitionId() == otherPath.getPath().get(k).getTransitionId()) {
        combinePath.addTransition(this.path.get(k));
      } else {
        startingDiff = k;
        break;
      }
    }

    // Filter 2: Dependencies checking
    boolean existInOtherPath = false;
    for (Transition t1 : this.reorderedEvents) {
      if (t1 instanceof NodeStartTransition || t1 instanceof NodeCrashTransition) {
        return null;
      }
      if (otherPath.hasTransition(t1)) {
        existInOtherPath = true;
        // all events before t1 cannot happen after all events in
        // otherpath
        for (int e1 = (getIndexInPath(t1) - 1); e1 >= startingDiff; e1--) {
          for (int e2 = otherPath.getPath().size() - 1; e2 >= startingDiff; e2--) {
            Transition tempE1 = this.getEventTransition(e1);
            Transition tempE2 = otherPath.getEventTransition(e2);
            if (VectorClockUtil.isConcurrent(tempE1.getVectorClock(),
                tempE2.getVectorClock()) == 1) {
              if (tempE1 instanceof PacketSendTransition
                  && tempE2 instanceof PacketSendTransition) {
                PacketSendTransition msg1 = (PacketSendTransition) tempE1;
                PacketSendTransition msg2 = (PacketSendTransition) tempE2;
                try {
                  int clientRequest1 = (int) msg1.getPacket().getValue("clientRequest");
                  int clientRequest2 = (int) msg2.getPacket().getValue("clientRequest");
                  if (clientRequest1 != clientRequest2) {
                    continue;
                  }
                } catch (Exception ex) {
                  LOG.warn(ex.toString());
                  return null;
                }
              }
              LOG.debug("Conflicting Reordering due to the vector clock.");
              return null;
            }
          }
        }
      }
    }

    // Filter 2: Dependencies checking
    for (Transition t2 : otherPath.getReorderedEvents()) {
      if (hasTransition(t2)) {
        if (t2 instanceof NodeStartTransition || t2 instanceof NodeCrashTransition) {
          return null;
        }
        if (existInOtherPath) {
          LOG.debug("Both reordering events exist in both dependency paths.");
          return null;
        }
        // all events before t2 cannot happen after all events in this
        // path
        for (int e2 = (otherPath.getIndexInPath(t2) - 1); e2 >= startingDiff; e2--) {
          for (int e1 = this.path.size() - 1; e1 >= startingDiff; e1--) {
            Transition tempE2 = otherPath.getEventTransition(e2);
            Transition tempE1 = this.getEventTransition(e1);
            if (VectorClockUtil.isConcurrent(tempE2.getVectorClock(),
                tempE1.getVectorClock()) == 1) {
              if (tempE1 instanceof PacketSendTransition
                  && tempE2 instanceof PacketSendTransition) {
                PacketSendTransition msg2 = (PacketSendTransition) tempE2;
                PacketSendTransition msg1 = (PacketSendTransition) tempE1;
                try {
                  int clientRequest2 = (int) msg2.getPacket().getValue("clientRequest");
                  int clientRequest1 = (int) msg1.getPacket().getValue("clientRequest");
                  if (clientRequest1 != clientRequest2) {
                    continue;
                  }
                } catch (Exception ex) {
                  LOG.warn(ex.toString());
                  return null;
                }
              }
              LOG.debug("Conflicting Reordering due to the vector clock.");
              return null;
            }
          }
        }
      }
    }

    // starting to mix the 2 different path
    boolean unsafeMix = false;
    if (startingDiff > -1) {
      int k1 = startingDiff;
      int k2 = startingDiff;

      while (k1 < this.path.size() || k2 < otherPath.getPath().size()) {
        if (k1 >= this.path.size()) {
          addEventIntoPath(combinePath, otherPath.getPath().get(k2));
          k2++;
        } else if (k2 >= otherPath.getPath().size()) {
          addEventIntoPath(combinePath, this.path.get(k1));
          k1++;
        } else if (otherPath.getReorderedEvents().contains(this.getEventTransition(k1))
            && this.reorderedEvents.contains(otherPath.getEventTransition(k2))) {
          unsafeMix = true;
          break;
        } else if (this.reorderedEvents.contains(this.getEventTransition(k1))) {
          combinePath.addTransition(this.path.get(k1));
          k1++;
        } else if (otherPath.getReorderedEvents().contains(otherPath.getEventTransition(k2))) {
          combinePath.addTransition(otherPath.getPath().get(k2));
          k2++;
        } else if (otherPath.getReorderedEvents().contains(this.getEventTransition(k1))) {
          k1++;
        } else if (this.reorderedEvents.contains(otherPath.getEventTransition(k2))) {
          k2++;
        } else if (this.getEventTransition(k1) == otherPath.getEventTransition(k2)) {
          combinePath.addTransition(this.path.get(k1));
          k1++;
          k2++;
        } else {
          int isConcurrent = VectorClockUtil.isConcurrent(this.path.get(k1).getVectorClock(),
              otherPath.getPath().get(k2).getVectorClock());
          if (isConcurrent == -1) {
            addEventIntoPath(combinePath, this.path.get(k1));
            k1++;
          } else if (isConcurrent == 1) {
            addEventIntoPath(combinePath, otherPath.getPath().get(k2));
            k2++;
          } else {
            addEventIntoPath(combinePath, this.path.get(k1));
            addEventIntoPath(combinePath, otherPath.getPath().get(k2));
            k1++;
            k2++;
          }
        }
      }

      // Filter 3: if it is unsafe to mix the paths
      if (unsafeMix) {
        LOG.debug("Unsafe Paths Combination");
        return null;
      } else {
        ParallelPath newCombinedPath = new ParallelPath(this.getPath(), this.getReorderedEvents(),
            this.getReorderedNodes(), this.getDependencies());
        newCombinedPath.setPath(combinePath.clone());
        newCombinedPath.addMoreReorderedNodes(otherPath.getReorderedNodes());
        newCombinedPath.addMoreReorderedEvents(otherPath.getReorderedEvents());

        String debugPath = "Path 1 to combine:\n";
        for (Transition t : this.path) {
          debugPath += t.toString() + "\n";
        }
        debugPath += "Path 2 to combine:\n";
        for (Transition t : otherPath.getPath()) {
          debugPath += t.toString() + "\n";
        }

        debugPath += "Combination Path:\n";
        for (Transition t : newCombinedPath.getPath()) {
          debugPath += t.toString() + "\n";
        }

        LOG.debug(debugPath);

        // riza
        newCombinedPath.pathId = -1;
        newCombinedPath.firstParentId = this.pathId;
        newCombinedPath.secondParentId = otherPath.pathId;
        newCombinedPath.getPath().setParentId(this.pathId);
        return newCombinedPath;
      }
    } else {
      LOG.debug("Paths that are compared are identical.");
      return null;
    }
    /*
     * // filter 1: cannot combine paths with same reordered nodes for (int otherNode :
     * otherPath.getReorderedNodes()) { for (int myNode : this.reorderedNodes) { if (otherNode ==
     * myNode) { LOG. debug("Cannot combine paths because they are processed in the same node: " +
     * this.reorderedNodes.toString() + " vs " + otherPath.getReorderedNodes().toString()); return
     * null; } } }
     *
     * Path combinePath = new Path(); int minimumPath = this.path.size() <
     * otherPath.getPath().size() ? this.path.size() : otherPath.getPath().size(); int startingDiff
     * = -1; for (int k = 0; k < minimumPath; k++) { if
     * (this.path.get(k).transition.getTransitionId() == otherPath.getPath().get(k).transition
     * .getTransitionId()) { combinePath.add(this.path.get(k)); } else { startingDiff = k; break; }
     * }
     *
     * // Filter 2: reordered events cannot be dependent events in the other // reordered path for
     * (Transition t1 : this.reorderedEvents) { for (Transition t2 : otherPath.getReorderedEvents())
     * { if ((dependencies.get(t1) != null && dependencies.get(t1).contains(t2)) ||
     * (otherPath.getDependencies().get(t2) != null &&
     * otherPath.getDependencies().get(t2).contains(t1))) { LOG.
     * debug("One of the reordered pairs are dependency events of the other reordered paths." );
     * return null; } else if (t1 instanceof NodeOperationTransition || t2 instanceof
     * NodeOperationTransition) { LOG.debug(
     * "One of the reordered pairs are crash/reboot events. Have not figured out how to handle it"
     * ); return null; } } }
     *
     * // starting to mix the 2 different path boolean unsafeMix = false; if (startingDiff > -1) {
     * int k1 = startingDiff; int k2 = startingDiff;
     *
     * while (k1 < this.path.size() || k2 < otherPath.getPath().size()) { if (k1 >=
     * this.path.size()) { addEventIntoPath(combinePath, otherPath.getPath().get(k2)); k2++; } else
     * if (k2 >= otherPath.getPath().size()) { addEventIntoPath(combinePath, this.path.get(k1));
     * k1++; } else if (otherPath.getReorderedEvents().contains(this.getEventTransition(k1)) &&
     * this.reorderedEvents.contains(otherPath.getEventTransition(k2))) { unsafeMix = true; break; }
     * else if (this.reorderedEvents.contains(this.getEventTransition(k1))) {
     * combinePath.add(this.path.get(k1)); k1++; } else if
     * (otherPath.getReorderedEvents().contains(otherPath.getEventTransition (k2))) {
     * combinePath.add(otherPath.getPath().get(k2)); k2++; } else if
     * (otherPath.getReorderedEvents().contains(this.getEventTransition(k1)) ) { k1++; } else if
     * (this.reorderedEvents.contains(otherPath.getEventTransition(k2))) { k2++; } else if
     * (this.getEventTransition(k1) == otherPath.getEventTransition(k2)) {
     * combinePath.add(this.path.get(k1)); k1++; k2++; } else { int isConcurrent =
     * VectorClockUtil.isConcurrent(this.path.get(k1).transition. getVectorClock(),
     * otherPath.getPath().get(k2).transition.getVectorClock()); if (isConcurrent == -1) {
     * addEventIntoPath(combinePath, this.path.get(k1)); k1++; } else if (isConcurrent == 1) {
     * addEventIntoPath(combinePath, otherPath.getPath().get(k2)); k2++; } else {
     * addEventIntoPath(combinePath, this.path.get(k1)); addEventIntoPath(combinePath,
     * otherPath.getPath().get(k2)); k1++; k2++; } } }
     *
     * // Filter 3: if it is unsafe to mix the paths if (unsafeMix) {
     * LOG.debug("Unsafe Paths Combination"); return null; } else { ParallelPath newCombinedPath =
     * new ParallelPath(this.getPath(), this.getReorderedEvents(), this.getReorderedNodes(),
     * this.getDependencies()); newCombinedPath.setPath(combinePath.clone());
     * newCombinedPath.addMoreReorderedNodes(otherPath.getReorderedNodes());
     * newCombinedPath.addMoreReorderedEvents(otherPath.getReorderedEvents() );
     *
     * String debugPath = "Path 1 to combine:\n"; for (TransitionTuple t : this.path) { debugPath +=
     * t.transition.toString() + "\n"; } debugPath += "Path 2 to combine:\n"; for (TransitionTuple t
     * : otherPath.getPath()) { debugPath += t.transition.toString() + "\n"; }
     *
     * debugPath += "Combination Path:\n"; for (TransitionTuple t : newCombinedPath.getPath()) {
     * debugPath += t.transition.toString() + "\n"; }
     *
     * LOG.debug(debugPath);
     *
     * return newCombinedPath; } } else { LOG.debug("Paths that are compared are identical.");
     * return null; }
     */
  }

  private boolean hasTransition(Transition t) {
    for (Transition event : path) {
      if (event == t) {
        return true;
      }
    }
    return false;
  }

  private int getIndexInPath(Transition t) {
    for (int i = 0; i < path.size(); i++) {
      if (path.get(i) == t) {
        return i;
      }
    }
    return -1;
  }

  public ParallelPath getSerializable(int numNode) {
    Path temp = new Path();
    for (Transition t : this.path) {
      temp.add(t.getSerializable(numNode));
    }
    ArrayList<Transition> temp2 = new ArrayList<Transition>();
    for (Transition t : this.reorderedEvents) {
      temp2.add(t.getSerializable(numNode));
    }
    Hashtable<Transition, List<Transition>> temp3 = new Hashtable<Transition, List<Transition>>();
    for (Transition t : this.dependencies.keySet()) {
      LinkedList<Transition> temp4 = new LinkedList<Transition>();
      for (Transition t2 : this.dependencies.get(t)) {
        temp4.add(t2.getSerializable(numNode));
      }
      temp3.put(t.getSerializable(numNode), temp4);
    }
    return new ParallelPath(temp, temp2, this.reorderedNodes, temp3);
  }

  public static ParallelPath deserialize(ModelCheckingServerAbstract mc, ParallelPath pp) {
    Path temp = new Path();
    for (Transition t : pp.getPath()) {
      temp.add(Transition.getRealTransition(mc, t));
    }
    ArrayList<Transition> temp2 = new ArrayList<Transition>();
    for (Transition t : pp.reorderedEvents) {
      temp2.add(Transition.getRealTransition(mc, t));
    }
    Hashtable<Transition, List<Transition>> temp3 = new Hashtable<Transition, List<Transition>>();
    for (Transition t : pp.getDependencies().keySet()) {
      LinkedList<Transition> temp4 = new LinkedList<Transition>();
      for (Transition t2 : pp.getDependencies().get(t)) {
        temp4.add(Transition.getRealTransition(mc, t2));
      }
      temp3.put(Transition.getRealTransition(mc, t), temp4);
    }

    return new ParallelPath(temp, temp2, pp.getReorderedNodes(), temp3);
  }

  private void addEventIntoPath(Path path, Transition event) {
    boolean exist = false;
    for (Transition t : path) {
      if (t.getTransitionId() == event.getTransitionId()) {
        exist = true;
        break;
      }
    }
    if (!exist) {
      path.addTransition(event);
    }
  }

  // in case we need the chain of events
  @SuppressWarnings("unchecked")
  private void buildCausalityEventsChain(Hashtable<Transition, List<Transition>> dependencies) {
    LinkedList<EventCausality> eventCausality = new LinkedList<EventCausality>();

    for (int i = 0; i < path.size(); i++) {
      EventCausality ev = new EventCausality(path.get(i), dependencies.get(path.get(i)));

      if (ev.getPrevEvents().size() > 0) {
        LinkedList<Transition> directParents = (LinkedList<Transition>) ev.getPrevEvents().clone();
        for (int j = eventCausality.size() - 1; j > -1; j--) {
          if (VectorClockUtil.isConcurrent(ev.getChild().getVectorClock(),
              eventCausality.get(j).getChild().getVectorClock()) != 0) {
            directParents.removeAll(eventCausality.get(j).getPrevEvents());
          }
        }
        ev.setDirectParents(directParents);
      } else {
        String tmp = "Direct Parents of " + path.get(i).toString() + ": NONE\n";
        LOG.debug(tmp);
      }

      eventCausality.add(ev);
    }
  }

  /**
   *
   * @return If its a product of path combination, it will return -1. If its not a product of path
   *         combination, it will return the original pathId.
   */
  public int getId() {
    return this.pathId;
  }

  /**
   *
   * @return If its a product of path combination, it will return the first parent pathId. If its
   *         not a product of path combination, it will return the original parent pathId.
   */
  public int getFirstParentId() {
    return this.firstParentId;
  }

  /**
   *
   * @return If its a product of path combination, it will return the second parent pathId. If its
   *         not a product of path combination, it will return the original parent pathId.
   */
  public int getSecondParentId() {
    return this.secondParentId;
  }
}