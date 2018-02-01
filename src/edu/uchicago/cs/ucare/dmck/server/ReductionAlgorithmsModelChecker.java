package edu.uchicago.cs.ucare.dmck.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

import com.almworks.sqlite4java.SQLiteException;

import edu.uchicago.cs.ucare.dmck.transition.AbstractEventConsequence;
import edu.uchicago.cs.ucare.dmck.transition.AbstractGlobalStates;
import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.AbstractNodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.SleepTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;
import edu.uchicago.cs.ucare.dmck.util.ExploredBranchRecorder;
import edu.uchicago.cs.ucare.dmck.util.LocalState;
import edu.uchicago.cs.ucare.dmck.util.SqliteExploredBranchRecorder;
import edu.uchicago.cs.ucare.dmck.util.VectorClockUtil;

public abstract class ReductionAlgorithmsModelChecker extends ModelCheckingServerAbstract {

  ExploredBranchRecorder exploredBranchRecorder;

  public static String[] abstractGlobalStateKeys;
  public static String[] nonAbstractEventKeys;

  // high priority
  LinkedList<Path> importantInitialPaths;
  LinkedList<Path> currentImportantInitialPaths;
  // normal priority
  LinkedList<Path> initialPaths;
  LinkedList<Path> currentInitialPaths;
  // low priority
  LinkedList<Path> unnecessaryInitialPaths;
  LinkedList<Path> currentUnnecessaryInitialPaths;

  HashSet<PathMeta> finishedInitialPaths;
  HashSet<PathMeta> currentFinishedInitialPaths;
  HashSet<PathMeta> initialPathSecondAttempt;
  Path currentInitialPath;
  Path currentExploringPath = new Path();

  // record all transition global states before and after
  LinkedList<AbstractGlobalStates> incompleteEventHistory;
  LinkedList<AbstractGlobalStates> eventHistory;
  LinkedList<AbstractEventConsequence> eventImpacts;
  int recordedEventImpacts;

  protected boolean isSAMC;
  protected ArrayList<String> reductionAlgorithms;

  String stateDir;

  int numCrash;
  int numReboot;
  int currentCrash;
  int currentReboot;

  int globalState2;
  LinkedList<boolean[]> prevOnlineStatus;

  LinkedList<LocalState[]> prevLocalStates;

  // record policy effectiveness
  Hashtable<String, Integer> policyRecord;

  public ReductionAlgorithmsModelChecker(String dmckName, FileWatcher fileWatcher, int numNode, int numCrash,
      int numReboot, String globalStatePathDir, String packetRecordDir, String workingDir,
      WorkloadDriver workloadDriver, String ipcDir) {
    super(dmckName, fileWatcher, numNode, globalStatePathDir, workingDir, workloadDriver, ipcDir);
    importantInitialPaths = new LinkedList<Path>();
    currentImportantInitialPaths = new LinkedList<Path>();
    initialPaths = new LinkedList<Path>();
    currentInitialPaths = new LinkedList<Path>();
    unnecessaryInitialPaths = new LinkedList<Path>();
    currentUnnecessaryInitialPaths = new LinkedList<Path>();
    finishedInitialPaths = new HashSet<PathMeta>();
    currentFinishedInitialPaths = new HashSet<PathMeta>();
    initialPathSecondAttempt = new HashSet<PathMeta>();
    incompleteEventHistory = new LinkedList<AbstractGlobalStates>();
    eventHistory = new LinkedList<AbstractGlobalStates>();
    eventImpacts = new LinkedList<AbstractEventConsequence>();
    recordedEventImpacts = 0;
    this.numCrash = numCrash;
    this.numReboot = numReboot;
    isSAMC = true;
    reductionAlgorithms = new ArrayList<String>();
    policyRecord = new Hashtable<String, Integer>();

    stateDir = packetRecordDir;
    try {
      exploredBranchRecorder = new SqliteExploredBranchRecorder(packetRecordDir);
    } catch (SQLiteException e) {
      LOG.error("", e);
    }

    getSAMCConfig();
    loadInitialPathsFromFile();
    resetTest();
  }

  @Override
  public void resetTest() {
    if (exploredBranchRecorder == null) {
      return;
    }
    super.resetTest();
    currentCrash = 0;
    currentReboot = 0;
    modelChecking = new PathTraversalWorker();
    currentEnabledTransitions = new LinkedList<Transition>();
    currentExploringPath = new Path();
    exploredBranchRecorder.resetTraversal();
    prevOnlineStatus = new LinkedList<boolean[]>();
    File waiting = new File(stateDir + "/.waiting");
    try {
      waiting.createNewFile();
    } catch (IOException e) {
      LOG.error("", e);
    }
    prevLocalStates = new LinkedList<LocalState[]>();

    // record policy effectiveness
    storePolicyEffectiveness();
  }

  // load samc.conf to the DMCK
  public void getSAMCConfig() {
    if (!(this instanceof DporModelChecker)) {
      try {
        String samcConfigFile = workingDirPath + "/samc.conf";
        FileInputStream configInputStream = new FileInputStream(samcConfigFile);
        Properties samcConf = new Properties();
        samcConf.load(configInputStream);
        configInputStream.close();

        abstractGlobalStateKeys = samcConf.getProperty("abstract_global_state") != null
            ? samcConf.getProperty("abstract_global_state").split(",")
            : null;
        nonAbstractEventKeys = samcConf.getProperty("non_abstract_event") != null
            ? samcConf.getProperty("non_abstract_event").split(",")
            : null;
        for (String algorithm : samcConf.getProperty("reduction_algorithms", "").split("")) {
          reductionAlgorithms.add(algorithm);
        }

        // sanity check
        if (reductionAlgorithms.contains("symmetry") && nonAbstractEventKeys == null) {
          LOG.error("In samc.conf, you have configured symmetry=true, but you have not specified non_abstract_event.");
          System.exit(1);
        }
      } catch (Exception e) {
        LOG.error("Error in reading samc config file:\n" + e.toString());
      }
    }
  }

  protected Hashtable<Long, Transition> loadAllEventsDB() {
    // Load all events information from all-events-db directory
    Hashtable<Long, Transition> allEvents = new Hashtable<Long, Transition>();
    for (File evFile : allEventsDBDir.listFiles()) {
      ObjectInputStream ois;
      try {
        ois = new ObjectInputStream(new FileInputStream(evFile));
        Transition t = (Transition) ois.readObject();
        allEvents.put(t.getTransitionId(), Transition.getRealTransition(this, t));
        ois.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return allEvents;
  }

  // load existing list of paths to particular queue
  protected void loadPaths(Hashtable<Long, Transition> allEventsDB, LinkedList<Path> pathQueue, int numRecord,
      String fileName) {
    for (int record = 1; record <= numRecord; record++) {
      File initialPathFile = new File(testRecordDirPath + "/" + record + "/" + fileName);
      if (initialPathFile.exists()) {
        try {
          BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(initialPathFile)));
          String path;
          while ((path = br.readLine()) != null) {
            Path initPath = new Path();
            String[] eventIDs = path.trim().split(",");
            for (String eventID : eventIDs) {
              initPath.add(allEventsDB.get(Long.parseLong(eventID)));
            }
            pathQueue.add(initPath);
          }
          br.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  protected void loadEventIDsPaths(Collection<PathMeta> pathQueue, int numRecord, String fileName) {
    for (int i = 1; i <= numRecord; i++) {
      File listOfPathsFile = new File(testRecordDirPath + "/" + i + "/" + fileName);
      if (listOfPathsFile.exists()) {
        try {
          BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(listOfPathsFile)));
          String path;
          while ((path = br.readLine()) != null) {
            pathQueue.add(new PathMeta(path.trim()));
          }
          br.close();
        } catch (FileNotFoundException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  protected void loadInitialPathsFromFile() {
    try {
      // Grab the max record directory
      File recordDir = new File(testRecordDirPath);
      File[] listOfRecordDir = recordDir.listFiles();
      int numRecord = listOfRecordDir.length;

      // Load all events DB
      Hashtable<Long, Transition> allEventsDB = loadAllEventsDB();

      if (reductionAlgorithms.contains("parallelism")) {
        loadPaths(allEventsDB, importantInitialPaths, numRecord, "importantInitialPathsInQueue");
        loadPaths(allEventsDB, unnecessaryInitialPaths, numRecord, "unnecessaryInitialPathsInQueue");
      }

      loadPaths(allEventsDB, initialPaths, numRecord, "initialPathsInQueue");

      for (int i = 1; i <= numRecord; i++) {
        File initialPathFile = new File(testRecordDirPath + "/" + i + "/currentInitialPath");
        if (initialPathFile.exists()) {
          BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(initialPathFile)));
          String path = null;
          while ((path = br.readLine()) != null) {
            path = path.trim();
            boolean hasRemovedPath = false;
            if (reductionAlgorithms.contains("parallelism")) {
              for (Path p : importantInitialPaths) {
                if (pathToString(p).equals(path)) {
                  importantInitialPaths.remove(p);
                  hasRemovedPath = true;
                  break;
                }
              }
            }
            if (!hasRemovedPath) {
              for (Path p : initialPaths) {
                if (pathToString(p).equals(path)) {
                  initialPaths.remove(p);
                  hasRemovedPath = true;
                  break;
                }
              }
            }
            if (reductionAlgorithms.contains("parallelism") && !hasRemovedPath) {
              for (Path p : unnecessaryInitialPaths) {
                if (pathToString(p).equals(path)) {
                  unnecessaryInitialPaths.remove(p);
                  hasRemovedPath = true;
                  break;
                }
              }
            }
          }
          br.close();
        }
      }

      loadEventIDsPaths(finishedInitialPaths, numRecord, "finishedInitialPaths");

      LOG.info("Total Important Initial Path that has been loaded:" + importantInitialPaths.size());
      LOG.info("Total Initial Path that has been loaded:" + initialPaths.size());
      LOG.info("Total Unnecessary Initial Path that has been loaded:" + unnecessaryInitialPaths.size());

      loadNextInitialPath(false, false);

    } catch (FileNotFoundException e1) {
      LOG.warn("", e1);
    } catch (IOException e1) {
      LOG.warn("", e1);
    }
  }

  public void loadNextInitialPath(boolean markPrevPathFinished, boolean finishedExploredAll) {
    while (true) {
      if (importantInitialPaths.size() > 0) {
        currentInitialPath = importantInitialPaths.remove();
      } else if (initialPaths.size() > 0) {
        currentInitialPath = initialPaths.remove();
      } else if (unnecessaryInitialPaths.size() > 0) {
        currentInitialPath = unnecessaryInitialPaths.remove();
      } else {
        if (markPrevPathFinished) {
          exploredBranchRecorder.resetTraversal();
          exploredBranchRecorder.markBelowSubtreeFinished();
        }
        if (finishedExploredAll) {
          hasFinishedAllExploration = true;
        }
      }

      if (reductionAlgorithms.contains("symmetry_double_check") && currentInitialPath != null) {
        if (!isSymmetricPath(currentInitialPath)) {
          break;
        }
        recordPolicyEffectiveness("symmetry_double_check");
        String tmp = "Reduce Initial Path due to Symmetry Double Check reduction algorithm:\n";
        for (Transition event : currentInitialPath) {
          tmp += event.toString() + "\n";
        }
        LOG.info(tmp);
        collectDebug(tmp);
      } else {
        break;
      }
    }
  }

  public Transition nextTransition(LinkedList<Transition> transitions) {
    // CRS runtime checking
    if (this.isSAMC && (this.numCrash > 0 || this.numReboot > 0)) {
      ListIterator<Transition> iter = transitions.listIterator();
      LinkedList<Transition> tempContainer = new LinkedList<Transition>();
      int queueSize = transitions.size();
      int continueCounter = 0;
      while (iter.hasNext()) {
        Transition transition = iter.next();
        if (reductionAlgorithms.contains("crs_rss_runtime")) {
          int isSymmetric = isRuntimeCrashRebootSymmetric(transition);

          if (isSymmetric == -1 && continueCounter < queueSize) {
            LOG.debug("Abstract Event has been executed before in the history. transition=" + transition.toString());
            continueCounter++;
            tempContainer.add(transition);
            continue;
          } else if (isSymmetric >= 0) {
            AbstractNodeOperationTransition tmp = (AbstractNodeOperationTransition) transition;
            tmp.setId(isSymmetric);
            transition = tmp;
            LOG.debug("NextTransition is suggested to transform " + transition.toString() + " to " + tmp.getId());
          }
        }
        if (continueCounter >= queueSize) {
          LOG.debug("Queue only consists of uninteresting abstract event. DMCK decided to choose the first event.");
        }
        if (!exploredBranchRecorder.isSubtreeBelowChildFinished(transition.getTransitionId())) {
          iter.remove();
          return transition;
        }
      }
      if (tempContainer.size() > 0) {
        Transition result = tempContainer.getFirst();
        transitions.remove(result);
        return result;
      }
    } else if (transitions.size() > 0) {
      return transitions.removeFirst();
    }

    return null;
  }

  protected void adjustCrashAndReboot(LinkedList<Transition> enabledTransitions) {
    int numOnline = 0;
    for (int i = 0; i < numNode; ++i) {
      if (isNodeOnline(i)) {
        numOnline++;
      }
    }
    int numOffline = numNode - numOnline;
    int tmp = numOnline < numCrash - currentCrash ? numOnline : numCrash - currentCrash;
    for (int i = 0; i < tmp; ++i) {
      AbstractNodeCrashTransition crash = new AbstractNodeCrashTransition(this);
      for (int j = 0; j < numNode; ++j) {
        crash.setPossibleVectorClock(j, vectorClocks[j][numNode]);
      }
      enabledTransitions.add(crash);
      currentCrash++;
      numOffline++;
    }
    tmp = numOffline < numReboot - currentReboot ? numOffline : numReboot - currentReboot;
    for (int i = 0; i < tmp; ++i) {
      AbstractNodeStartTransition start = new AbstractNodeStartTransition(this);
      for (int j = 0; j < numNode; ++j) {
        start.setPossibleVectorClock(j, vectorClocks[j][numNode]);
      }
      enabledTransitions.add(start);
      currentReboot++;
    }
  }

  protected void markPacketsObsolete(int obsoleteBy, int crashingNode, LinkedList<Transition> enabledTransitions) {
    ListIterator<Transition> iter = enabledTransitions.listIterator();
    while (iter.hasNext()) {
      Transition t = iter.next();
      if (t instanceof PacketSendTransition) {
        PacketSendTransition p = (PacketSendTransition) t;
        if (p.getPacket().getFromId() == crashingNode || p.getPacket().getToId() == crashingNode) {
          p.getPacket().setObsolete(true);
          p.getPacket().setObsoleteBy(obsoleteBy);
        }
      }
    }
  }

  public void updateGlobalState2() {
    int prime = 31;
    globalState2 = getGlobalState();
    globalState2 = prime * globalState2 + currentEnabledTransitions.hashCode();
    for (int i = 0; i < numNode; ++i) {
      for (int j = 0; j < numNode; ++j) {
        globalState2 = prime * globalState2 + Arrays.hashCode(messagesQueues[i][j].toArray());
      }
    }
  }

  protected int getGlobalState2() {
    return globalState2;
  }

  protected void convertExecutedAbstractTransitionToReal(Path executedPath) {
    ListIterator<Transition> iter = executedPath.listIterator();
    while (iter.hasNext()) {
      Transition iterItem = iter.next();
      if (iterItem instanceof AbstractNodeCrashTransition) {
        AbstractNodeCrashTransition crash = (AbstractNodeCrashTransition) iterItem;
        NodeCrashTransition crashTransition = new NodeCrashTransition(ReductionAlgorithmsModelChecker.this, crash.getId());
        crashTransition.setVectorClock(crash.getPossibleVectorClock(crash.getId()));
        iter.set(crashTransition);
      } else if (iterItem instanceof AbstractNodeStartTransition) {
        AbstractNodeStartTransition start = (AbstractNodeStartTransition) iterItem;
        NodeStartTransition startTransition = new NodeStartTransition(ReductionAlgorithmsModelChecker.this, start.getId());
        startTransition.setVectorClock(start.getPossibleVectorClock(start.getId()));
        iter.set(startTransition);
      }
    }
  }

  protected String pathToString(Path initialPath) {
    String path = "";
    for (int i = 0; i < initialPath.size(); i++) {
      if (initialPath.get(i) instanceof PacketSendTransition) {
        if (i == 0) {
          path = String.valueOf(initialPath.get(i).getTransitionId());
        } else {
          path += "," + String.valueOf(initialPath.get(i).getTransitionId());
        }
      } else {
        if (i == 0) {
          path = ((NodeOperationTransition) initialPath.get(i)).toStringForFutureExecution();
        } else {
          path += "," + ((NodeOperationTransition) initialPath.get(i)).toStringForFutureExecution();
        }
      }
    }
    return path;
  }

  protected String pathToHistoryString(Path initialPath) {
    String result = "";
    String[] path = new String[numNode];
    for (Transition transition : initialPath) {
      if (transition instanceof PacketSendTransition) {
        PacketSendTransition t = (PacketSendTransition) transition;
        if (path[t.getPacket().getToId()] == null) {
          path[t.getPacket().getToId()] = String.valueOf(t.getTransitionId());
        } else {
          path[t.getPacket().getToId()] += "," + String.valueOf(t.getTransitionId());
        }
      } else if (transition instanceof NodeOperationTransition) {
        NodeOperationTransition t = (NodeOperationTransition) transition;
        if (path[t.getId()] == null) {
          path[t.getId()] = String.valueOf(t.getTransitionId());
        } else {
          path[t.getId()] += "," + String.valueOf(t.getTransitionId());
        }
      }
    }
    for (int i = 0; i < path.length; i++) {
      result += i + ":" + path[i] + ";";
    }
    return result;
  }

  protected void addPathToFinishedInitialPath(Path path) {
    String newHistoryPath = pathToString(path);
    currentFinishedInitialPaths.add(new PathMeta(newHistoryPath));
  }

  protected boolean isIdenticalHistoricalPath(String currentPath, String historicalPathNodes) {
    String[] currentPathNodes = currentPath.split(";");
    String[] historyStates = historicalPathNodes.split(";");
    for (int i = 0; i < currentPathNodes.length; i++) {
      if (!historyStates[i].startsWith(currentPathNodes[i])) {
        return false;
      }
    }
    return true;
  }

  protected boolean pathExistInHistory(Path path) {
    PathMeta currentPath = path.toPathMeta();
    for (PathMeta finishedPath : currentFinishedInitialPaths) {
      if (isIdenticalHistoricalPath(currentPath.getPathString(), finishedPath.getPathString())) {
        return true;
      }
    }
    for (PathMeta finishedPath : finishedInitialPaths) {
      if (isIdenticalHistoricalPath(currentPath.getPathString(), finishedPath.getPathString())) {
        return true;
      }
    }
    return false;
  }

  protected void addToInitialPathList(Path initialPath) {
    convertExecutedAbstractTransitionToReal(initialPath);
    if (!pathExistInHistory(initialPath)) {
      initialPaths.add(initialPath);
      addPathToFinishedInitialPath(initialPath);
    }
  }

  public static String getAbstractLocalState(LocalState ls) {
    String result = "[";
    boolean isFirst = true;
    for (String key : abstractGlobalStateKeys) {
      if (isFirst) {
        isFirst = false;
      } else {
        result += ", ";
      }
      result += key + "=" + ls.getValue(key);
    }
    result += "]";
    return result;
  }

  public static String getAbstractEvent(Transition ev) {
    String result = "";
    if (ev instanceof PacketSendTransition) {
      result = "abstract-message: ";
      PacketSendTransition msg = (PacketSendTransition) ev;
      boolean isFirst = true;
      for (String key : msg.getPacket().getAllKeys()) {
        boolean nextKey = false;
        for (String nonAbstractKey : ReductionAlgorithmsModelChecker.nonAbstractEventKeys) {
          if (key.equals(nonAbstractKey)) {
            nextKey = true;
            break;
          }
        }
        if (nextKey) {
          continue;
        }

        if (isFirst) {
          isFirst = false;
        } else {
          result += ", ";
        }
        result += key + "=" + msg.getPacket().getValue(key);
      }
    } else if (ev instanceof AbstractNodeCrashTransition || ev instanceof NodeCrashTransition) {
      result = "abstract-crash";
    } else if (ev instanceof AbstractNodeStartTransition || ev instanceof NodeStartTransition) {
      result = "abstract-reboot";
    }
    return result;
  }

  public static boolean isIdenticalAbstractLocalStates(LocalState ls1, LocalState ls2) {
    for (String key : abstractGlobalStateKeys) {
      if (ls1.getValue(key) == null && ls2.getValue(key) == null) {
        continue;
      } else if (ls1.getValue(key) == null || ls2.getValue(key) == null) {
        return false;
      } else if (!ls1.getValue(key).toString().equals(ls2.getValue(key).toString())) {
        return false;
      }
    }
    return true;
  }

  public static boolean isIdenticalAbstractEvent(Transition e1, Transition e2) {
    if (e1 instanceof PacketSendTransition && e2 instanceof PacketSendTransition) {
      PacketSendTransition m1 = (PacketSendTransition) e1;
      PacketSendTransition m2 = (PacketSendTransition) e2;
      for (String key : m1.getPacket().getAllKeys()) {
        // if key is in non abstract event keys, then it does not need to be evaluated
        boolean nextKey = false;
        for (String nonAbstractKey : ReductionAlgorithmsModelChecker.nonAbstractEventKeys) {
          if (key.equals(nonAbstractKey)) {
            nextKey = true;
            break;
          }
        }
        if (nextKey) {
          continue;
        }

        // if key in m1 does not exist in m2, these messages are not identical
        if (m2.getPacket().getValue(key) == null) {
          return false;
        }

        // if value in m1 and m2 are different, then these messages are not identical
        if (!m1.getPacket().getValue(key).toString().equals(m2.getPacket().getValue(key).toString())) {
          return false;
        }
      }
      return true;
    } else if ((e1 instanceof NodeCrashTransition || e1 instanceof AbstractNodeCrashTransition)
        && (e2 instanceof NodeCrashTransition || e2 instanceof AbstractNodeCrashTransition)) {
      return true;
    } else if ((e1 instanceof NodeStartTransition || e1 instanceof AbstractNodeStartTransition)
        && (e2 instanceof NodeStartTransition || e2 instanceof AbstractNodeStartTransition)) {
      return true;
    } else {
      return false;
    }
  }

  // focus on swapping the newTransition before oldTransition
  protected Path reorderEvents(LocalState[] wasLocalStates, Path initialPath, Transition oldTransition,
      Transition newTransition) {
    Path reorderingEvents = new Path();

    // compare initial path with dependency path that includes initial path
    if (newTransition instanceof PacketSendTransition) {
      List<Transition> allBeforeTransitions = dependencies.get(newTransition);
      ListIterator<Transition> beforeIter = allBeforeTransitions.listIterator();
      Iterator<Transition> checkingIter = initialPath.iterator();
      while (beforeIter.hasNext()) {
        Transition beforeTransition = beforeIter.next();
        boolean isFound = false;
        while (checkingIter.hasNext()) {
          if (checkingIter.next().equals(beforeTransition)) {
            isFound = true;
            break;
          }
        }
        if (!isFound) {
          beforeIter.previous();
          break;
        }
      }
      while (beforeIter.hasNext()) {
        Transition nextEvent = beforeIter.next();
        reorderingEvents.addTransition(nextEvent);
      }
    }

    reorderingEvents.addTransition(newTransition);
    reorderingEvents.addTransition(oldTransition);

    return reorderingEvents;
  }

  protected boolean addNewInitialPath(LocalState[] wasLocalStates, Path initialPath, Transition oldTransition,
      Transition newTransition) {
    // mark the initial path plus the old event as explored
    Path oldPath = (Path) initialPath.clone();
    convertExecutedAbstractTransitionToReal(oldPath);
    oldPath.addTransition(oldTransition);
    addPathToFinishedInitialPath(oldPath);

    Path newInitialPath = (Path) initialPath.clone();
    convertExecutedAbstractTransitionToReal(newInitialPath);

    Path reorderedEvents = reorderEvents(wasLocalStates, newInitialPath, oldTransition, newTransition);

    newInitialPath.addAll(reorderedEvents);

    // check symmetrical path
    if (isSymmetricPath(newInitialPath)) {
      return false;
    }

    if (!pathExistInHistory(newInitialPath)) {
      LOG.info("Transition " + newTransition.getTransitionId() + " needs to be reordered with "
          + oldTransition.getTransitionId());
      initialPaths.add(newInitialPath);
      if (!reductionAlgorithms.contains("parallelism")) {
        addPathToFinishedInitialPath(newInitialPath);
      }

      // add new initial path in debug.log
      String debugNewPath = "New Initial Path:\n";
      for (Transition t : newInitialPath) {
        debugNewPath += t.toString() + "\n";
      }
      collectDebug(debugNewPath);

      return true;
    }
    return false;
  }

  protected void saveAllExecutedEvents() {
    Iterator<Transition> currentExploredPathIter = currentExploringPath.iterator();
    while (currentExploredPathIter.hasNext()) {
      Transition transition = currentExploredPathIter.next();
      // Check all-events-db current files
      boolean isNewEv = true;
      for (File evFile : allEventsDBDir.listFiles()) {
        if (evFile.getName() == String.valueOf(transition.getTransitionId())) {
          isNewEv = false;
          break;
        }
      }
      if (isNewEv) {
        try {
          // Save per event to all-events-db directory
          ObjectOutputStream evStream = new ObjectOutputStream(
              new FileOutputStream(allEventsDBDirPath + "/" + transition.getTransitionId()));
          evStream.writeObject(transition.getSerializable(numNode));
          evStream.close();
        } catch (Exception e) {
          LOG.error("", e);
        }
      }
    }
  }

  // save collection of paths event IDs to file
  protected boolean savePaths(LinkedList<Path> pathsQueue, String fileName) {
    if (pathsQueue.size() > 0) {
      try {
        BufferedWriter bw = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(new File(idRecordDirPath + "/" + fileName))));
        Iterator<Path> pathsQueueIter = pathsQueue.iterator();
        String paths = "";
        while (pathsQueueIter.hasNext()) {
          Path path = pathsQueueIter.next();
          paths += pathToString(path) + "\n";
        }
        bw.write(paths);
        bw.close();

        return true;
      } catch (FileNotFoundException e) {
        LOG.error("", e);
      } catch (IOException e) {
        LOG.error("", e);
      }
    }
    return false;
  }

  protected boolean saveEventIDsPaths(Collection<PathMeta> pathsQueue, String fileName) {
    if (pathsQueue.size() > 0) {
      try {
        BufferedWriter bw = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(new File(idRecordDirPath + "/" + fileName))));
        String paths = "";
        for (PathMeta path : pathsQueue) {
          paths += path.getPathString() + "\n";
        }
        bw.write(paths);
        bw.close();

        return true;
      } catch (FileNotFoundException e) {

      } catch (IOException e) {
        LOG.error("", e);
      }
    }
    return false;
  }

  // to log paths
  protected void printPaths(String pathsName, Collection<String> paths) {
    String logs = pathsName + " consists of " + paths.size() + " paths:\n";
    int i = 1;
    for (String path : paths) {
      logs += "Path " + i++ + ":\n" + path + "\n";
    }
    LOG.info(logs);
  }

  protected void printPaths(String pathsName, LinkedList<Path> paths) {
    String logs = pathsName + " consists of " + paths.size() + " paths:\n";
    int i = 1;
    for (Path path : paths) {
      logs += "Path " + i++ + ":\n";
      for (Transition transition : path) {
        logs += transition.toString() + "\n";
      }
    }
    LOG.info(logs);
  }

  protected void saveGeneratedInitialPaths() {
    try {
      if (currentInitialPath != null) {
        BufferedWriter bw = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(new File(idRecordDirPath + "/currentInitialPath"))));
        bw.write(pathToString(currentInitialPath));
        bw.close();
      }

      if (reductionAlgorithms.contains("parallelism")) {
        // to save high priority initial path
        if (savePaths(currentImportantInitialPaths, "importantInitialPathsInQueue")) {
          importantInitialPaths.addAll(currentImportantInitialPaths);
          currentImportantInitialPaths.clear();
        }
      }

      // to save normal priority initial path
      if (savePaths(currentInitialPaths, "initialPathsInQueue")) {
        initialPaths.addAll(currentInitialPaths);
        currentInitialPaths.clear();

        printPaths("Initial Paths", initialPaths);
      }

      if (reductionAlgorithms.contains("parallelism")) {
        // to save low priority initial path
        if (savePaths(currentUnnecessaryInitialPaths, "unnecessaryInitialPathsInQueue")) {
          unnecessaryInitialPaths.addAll(currentUnnecessaryInitialPaths);
          currentUnnecessaryInitialPaths.clear();
        }

        printPaths("Important Initial Paths", importantInitialPaths);
        printPaths("Low Priority Initial Paths", unnecessaryInitialPaths);
      }

      if (saveEventIDsPaths(currentFinishedInitialPaths, "finishedInitialPaths")) {
        finishedInitialPaths.addAll(currentFinishedInitialPaths);
        currentFinishedInitialPaths.clear();
      }
    } catch (FileNotFoundException e) {
      LOG.error("", e);
    } catch (IOException e) {
      LOG.error("", e);
    }
  }

  protected void evaluateParallelismInitialPaths() {
    boolean evaluateAllInitialPaths = true;
    LinkedList<ParallelPath> transformedInitialPaths = new LinkedList<ParallelPath>();
    for (Path path : initialPaths) {
      ParallelPath newPath = new ParallelPath(path, dependencies);
      transformedInitialPaths.add(newPath);
    }

    // compare reordered path
    while (evaluateAllInitialPaths) {
      evaluateAllInitialPaths = false;
      LinkedList<ParallelPath> pathQueue = new LinkedList<ParallelPath>();
      pathQueue.addAll(transformedInitialPaths);

      for (int i = 0; i < pathQueue.size() - 1; i++) {
        for (int j = i + 1; j < pathQueue.size(); j++) {
          LOG.debug("Evaluate path-" + i + " vs path-" + j);
          ParallelPath path1 = pathQueue.get(i);
          ParallelPath path2 = pathQueue.get(j);
          ParallelPath newPath = path1.combineOtherPath(path2);
          if (newPath != null) {
            String combinedComment = "Combine ";
            evaluateAllInitialPaths = true;

            combinedComment += "path-" + j + " into ";
            transformedInitialPaths.remove(j);
            currentUnnecessaryInitialPaths.add(path2.getPath());
            addPathToFinishedInitialPath(path2.getPath());

            combinedComment += "path-" + i + "\n";
            transformedInitialPaths.remove(i);
            currentUnnecessaryInitialPaths.add(path1.getPath());
            addPathToFinishedInitialPath(path1.getPath());

            transformedInitialPaths.addFirst(newPath);
            recordPolicyEffectiveness("parallelism");

            LOG.debug(combinedComment);
            collectDebug(combinedComment);
            break;
          }
        }
        if (evaluateAllInitialPaths) {
          break;
        }
      }
    }

    for (ParallelPath newPath : transformedInitialPaths) {
      if (!pathExistInHistory(newPath.getPath())) {
        currentImportantInitialPaths.add(newPath.getPath());
        addPathToFinishedInitialPath(newPath.getPath());

        // add new initial path in debug.log
        String debugNewPath = "New Important Paths:\n";
        for (Transition t : newPath.getPath()) {
          debugNewPath += t.toString() + "\n";
        }
        collectDebug(debugNewPath);
      }
    }
    initialPaths.clear();
  }

  protected void evaluateExecutedPath() {
    saveAllExecutedEvents();
    saveEventConsequences();

    backtrackExecutedPath();
    printPaths("Initial Paths", initialPaths);

    if (reductionAlgorithms.contains("parallelism")) {
      evaluateParallelismInitialPaths();
    }

    saveGeneratedInitialPaths();
  }

  Hashtable<Transition, List<Transition>> dependencies = new Hashtable<Transition, List<Transition>>();

  protected void calculateDependencyGraph() {
    dependencies.clear();
    Path realExecutionPath = new Path();
    for (Transition transition : currentExploringPath) {
      if (transition instanceof AbstractNodeCrashTransition) {
        AbstractNodeCrashTransition abstractCrash = (AbstractNodeCrashTransition) transition;
        NodeCrashTransition realCrash = new NodeCrashTransition(this, abstractCrash.getId());
        realCrash.setVectorClock(abstractCrash.getPossibleVectorClock(abstractCrash.getId()));
        realExecutionPath.addTransition(realCrash);
      } else if (transition instanceof AbstractNodeStartTransition) {
        AbstractNodeStartTransition abstractStart = (AbstractNodeStartTransition) transition;
        NodeStartTransition realStart = new NodeStartTransition(this, abstractStart.getId());
        realStart.setVectorClock(abstractStart.getPossibleVectorClock(abstractStart.getId()));
        realExecutionPath.addTransition(realStart);
      } else {
        realExecutionPath.addTransition(transition);
      }
    }
    ListIterator<Transition> currentIter = (ListIterator<Transition>) realExecutionPath
        .listIterator(realExecutionPath.size());
    while (currentIter.hasPrevious()) {
      Transition current = currentIter.previous();
      LinkedList<Transition> partialOrder = new LinkedList<Transition>();
      if (currentIter.hasPrevious()) {
        ListIterator<Transition> comparingIter = realExecutionPath.listIterator(currentIter.nextIndex());
        while (comparingIter.hasPrevious()) {
          Transition comparing = comparingIter.previous();
          int compareResult = VectorClockUtil.isConcurrent(current.getVectorClock(), comparing.getVectorClock());
          if (compareResult == 1) {
            // hack solution for multiple client requests for
            // Cassandra system
            if (dmckName.equals("cassChecker") && current instanceof PacketSendTransition
                && comparing instanceof PacketSendTransition) {
              PacketSendTransition lt = (PacketSendTransition) current;
              PacketSendTransition tt = (PacketSendTransition) comparing;
              int lastCR1 = (int) lt.getPacket().getValue("clientRequest");
              int lastCR2 = (int) tt.getPacket().getValue("clientRequest");
              if (lastCR1 != lastCR2) {
                continue;
              }
            }
            partialOrder.addFirst(comparing);
          }
        }
      }
      dependencies.put(current, partialOrder);
    }
  }

  protected int findTransition(String transitionId) {
    int result = -1;
    long transId = Long.parseLong(transitionId);
    for (int index = 0; index < currentEnabledTransitions.size(); index++) {
      if (transId == currentEnabledTransitions.get(index).getTransitionId()) {
        result = index;
        break;
      }
    }
    return result;
  }

  protected void recordPolicyEffectiveness(String policy) {
    if (policyRecord.containsKey(policy)) {
      int currentRecord = (Integer) policyRecord.get(policy);
      currentRecord += 1;
      policyRecord.put(policy, currentRecord);
    } else {
      policyRecord.put(policy, 1);
    }
  }

  protected void storePolicyEffectiveness() {
    try {
      BufferedWriter bw = new BufferedWriter(
          new OutputStreamWriter(new FileOutputStream(new File(workingDirPath + "/policyEffect.txt"))));

      String policyEffect = "";
      Enumeration<String> keys = policyRecord.keys();
      while (keys.hasMoreElements()) {
        String key = (String) keys.nextElement();
        policyEffect += key + ":" + policyRecord.get(key) + "\n";
      }

      bw.write(policyEffect);
      bw.close();
    } catch (Exception ex) {
      LOG.error(ex.toString());
    }
  }

  protected void saveEventConsequences() {
    if (testId > 0) {
      try {
        BufferedWriter bw = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(new File(idRecordDirPath + "/" + "eventConsequences"))));
        String eventConsequences = "";
        for (int i = recordedEventImpacts; i < eventImpacts.size(); i++) {
          eventConsequences += eventImpacts.get(i) + "\n";
        }
        bw.write(eventConsequences);
        bw.close();

        recordedEventImpacts = eventImpacts.size();
      } catch (FileNotFoundException e) {
        LOG.error("", e);
      } catch (IOException e) {
        LOG.error("", e);
      }
    }

  }

  public boolean isSymmetricPath(Path initialPath) {
    if (reductionAlgorithms.contains("symmetry")) {
      LocalState[] globalStates = getInitialGlobalStates();
      for (Transition event : initialPath) {
        AbstractGlobalStates ags = new AbstractGlobalStates(globalStates, event);
        for (int nodeId = 0; nodeId < numNode; nodeId++) {
          if (isIdenticalAbstractLocalStates(globalStates[nodeId], ags.getExecutingNodeState())) {
            LocalState newLS = findLocalStateChange(globalStates[nodeId], ags.getEvent());
            if (newLS != null) {
              globalStates[nodeId] = newLS.clone();
            } else {
              LOG.debug("SYMMETRY CHECK: node with state=" + globalStates[nodeId].toString() + " executes "
                  + ags.getEvent().toString() + " transform to unknown state");
              return false;
            }
            break;
          }
        }
      }
      // if until the end of all initial path the DMCK can predict the end global
      // states
      // then the initial path is symmetrical
      recordPolicyEffectiveness("symmetry");
      return true;
    }
    return false;
  }

  public boolean isSymmetric(LocalState[] localStates, Transition event) {
    AbstractGlobalStates crashGS = new AbstractGlobalStates(localStates, event);
    boolean isSymmetric = false;
    for (AbstractGlobalStates historicalAGS : incompleteEventHistory) {
      if (historicalAGS.equals(crashGS)) {
        isSymmetric = true;
        break;
      }
    }
    return isSymmetric;
  }

  public boolean addEventToIncompleteHistory(LocalState[] localStates, Transition event) {
    // do not add null event to history
    if (event == null) {
      return false;
    }

    AbstractGlobalStates ags = new AbstractGlobalStates(localStates, event);
    if (isSymmetricEvent(ags) == -1) {
      incompleteEventHistory.add(ags);
      return true;
    }
    return false;
  }

  public void moveIncompleteEventToEventHistory(LocalState[] oldLocalStates, LocalState[] newLocalStates,
      Transition event) {
    AbstractGlobalStates ags = new AbstractGlobalStates(oldLocalStates, event);
    int eventIndex = -1;
    for (int x = 0; x < incompleteEventHistory.size(); x++) {
      if (incompleteEventHistory.get(x).equals(ags)) {
        eventIndex = x;
        break;
      }
    }
    if (eventIndex >= 0) {
      incompleteEventHistory.remove(eventIndex);
      ags.setAbstractGlobalStateAfter(newLocalStates);
      // collect : stateBefore >> event >> stateAfter chains
      AbstractEventConsequence newAEC = ags.getAbstractEventConsequence();
      boolean record = true;
      for (AbstractEventConsequence recordedAEC : eventImpacts) {
        if (recordedAEC.isIdentical(newAEC)) {
          record = false;
          break;
        }
      }
      if (record) {
        eventImpacts.add(newAEC);
      }
      eventHistory.add(ags);
    }
  }

  // if symmetric, return id in history. otherwise, return -1.
  public int isSymmetricEvent(AbstractGlobalStates otherAGS) {
    for (int x = 0; x < eventHistory.size(); x++) {
      if (eventHistory.get(x).equals(otherAGS)) {
        return x;
      }
    }
    return -1;
  }

  public LocalState findLocalStateChange(LocalState oldState, Transition event) {
    for (AbstractEventConsequence aec : eventImpacts) {
      LocalState newLS = aec.getTransformationState(oldState, event);
      if (newLS != null) {
        return newLS;
      }
    }
    return null;
  }

  protected abstract void backtrackExecutedPath();

  protected abstract int isRuntimeCrashRebootSymmetric(Transition nextTransition);

  protected Transition transformStringToTransition(LinkedList<Transition> currentEnabledTransitions, String nextEvent) {
    Transition t = null;
    if (nextEvent.startsWith("nodecrash")) {
      int id = Integer.parseInt(nextEvent.substring(13).trim());
      NodeCrashTransition crashTransition = new NodeCrashTransition(ReductionAlgorithmsModelChecker.this, id);
      boolean absExist = false;
      for (Transition tt : currentEnabledTransitions) {
        if (tt instanceof AbstractNodeCrashTransition) {
          absExist = true;
          crashTransition.setVectorClock(((AbstractNodeCrashTransition) tt).getPossibleVectorClock(id));
          currentEnabledTransitions.remove(tt);
          break;
        }
      }
      if (absExist)
        t = crashTransition;
    } else if (nextEvent.startsWith("nodestart")) {
      int id = Integer.parseInt(nextEvent.substring(13).trim());
      NodeStartTransition startTransition = new NodeStartTransition(this, id);
      boolean absExist = false;
      for (Transition tt : currentEnabledTransitions) {
        if (tt instanceof AbstractNodeStartTransition) {
          absExist = true;
          startTransition.setVectorClock(((AbstractNodeStartTransition) tt).getPossibleVectorClock(id));
          currentEnabledTransitions.remove(tt);
          break;
        }
      }
      if (absExist)
        t = startTransition;
    } else if (nextEvent.startsWith("sleep")) {
      int time = Integer.parseInt(nextEvent.substring(6));
      t = new SleepTransition(time);
    } else {
      int index = findTransition(nextEvent);
      if (index >= 0) {
        t = currentEnabledTransitions.remove(index);
      }
    }
    return t;
  }

  protected Transition retrieveEventFromQueue(LinkedList<Transition> dmckQueue, Transition event) {
    if (event instanceof SleepTransition) {
      return new SleepTransition(((SleepTransition) event).getSleepTime());
    }
    for (int index = 0; index < dmckQueue.size(); index++) {
      if (event instanceof PacketSendTransition && dmckQueue.get(index) instanceof PacketSendTransition
          && event.getTransitionId() == dmckQueue.get(index).getTransitionId()) {
        return dmckQueue.remove(index);
      } else if (event instanceof NodeCrashTransition && dmckQueue.get(index) instanceof AbstractNodeCrashTransition) {
        AbstractNodeCrashTransition absCrashEvent = (AbstractNodeCrashTransition) dmckQueue.remove(index);
        int crashId = ((NodeCrashTransition) event).getId();
        NodeCrashTransition crashEvent = new NodeCrashTransition(this, crashId);
        crashEvent.setVectorClock(absCrashEvent.getPossibleVectorClock(crashId));
        return crashEvent;
      } else if (event instanceof NodeStartTransition && dmckQueue.get(index) instanceof AbstractNodeStartTransition) {
        AbstractNodeStartTransition absRebootEvent = (AbstractNodeStartTransition) dmckQueue.remove(index);
        int rebootId = ((NodeStartTransition) event).getId();
        NodeStartTransition rebootEvent = new NodeStartTransition(this, rebootId);
        rebootEvent.setVectorClock(absRebootEvent.getPossibleVectorClock(rebootId));
        return rebootEvent;
      }
    }
    return null;
  }

  class PathTraversalWorker extends Thread {

    @Override
    public void run() {
      int workloadRetry = 10;
      if (currentInitialPath != null && !currentInitialPath.isEmpty()) {
        LOG.info("Start with existing initial path first.");
        String tmp = "Current Initial Path:\n";
        for (Transition event : currentInitialPath) {
          tmp += event.toString() + "\n";
        }
        LOG.info(tmp);
        collectDebug(tmp);
        int transitionCounter = 0;
        for (Transition event : currentInitialPath) {
          transitionCounter++;
          executeMidWorkload();
          updateSAMCQueue();
          updateGlobalState2();
          Transition nextEvent = retrieveEventFromQueue(currentEnabledTransitions, event);
          for (int i = 0; i < 20; ++i) {
            if (nextEvent != null) {
              break;
            } else {
              try {
                Thread.sleep(steadyStateTimeout / 2);
                updateSAMCQueue();
              } catch (InterruptedException e) {
                LOG.error("", e);
              }
            }
          }
          if (nextEvent == null) {
            LOG.error("ERROR: Expected to execute " + event + ", but the event is not in queue.");
            LOG.error("Being in wrong state, there is not transition " + event + " to apply");
            try {
              pathRecordFile.write(("no transition. looking for event with id=" + event + "\n").getBytes());
            } catch (IOException e) {
              LOG.error("", e);
            }
            if (!initialPathSecondAttempt.contains(currentInitialPath.toPathMeta())) {
              currentUnnecessaryInitialPaths.addFirst(currentInitialPath);
              LOG.warn("Try this initial path once again, but at low priority. Total Low Priority Initial Path="
                  + (unnecessaryInitialPaths.size() + currentUnnecessaryInitialPaths.size())
                  + " Total Initial Path Second Attempt=" + initialPathSecondAttempt.size());
              initialPathSecondAttempt.add(currentInitialPath.toPathMeta());
            }
            loadNextInitialPath(true, false);
            LOG.warn("---- Quit of Path Execution because an error ----");
            resetTest();
            return;
          } else {
            executeEvent(nextEvent, transitionCounter <= directedInitialPath.size());
          }
        }
      }
      LOG.info("Try to find new path/Continue from Initial Path");
      boolean hasWaited = waitEndExploration == 0;
      while (true) {
        executeMidWorkload();
        updateSAMCQueue();
        updateGlobalState2();
        boolean terminationPoint = checkTerminationPoint(currentEnabledTransitions);
        if (terminationPoint && hasWaited) {
          // Performance evaluation
          collectPerformanceMetrics();

          boolean verifiedResult = verifier.verify();
          String detail = verifier.verificationDetail();
          saveResult(verifiedResult + " ; " + detail + "\n");
          exploredBranchRecorder.markBelowSubtreeFinished();
          calculateDependencyGraph();
          String currentFinishedPath = "Finished execution path\n";
          for (Transition transition : currentExploringPath) {
            currentFinishedPath += transition.toString() + "\n";
          }
          LOG.info(currentFinishedPath);
          Path finishedExploringPath = (Path) currentExploringPath.clone();
          convertExecutedAbstractTransitionToReal(finishedExploringPath);
          addPathToFinishedInitialPath(finishedExploringPath);
          evaluateExecutedPath();
          loadNextInitialPath(true, true);
          LOG.info("---- End of Path Execution ----");
          resetTest();
          break;
        } else if (terminationPoint) {
          try {
            if (dmckName.equals("raftModelChecker") && waitForNextLE && waitedForNextLEInDiffTermCounter < 20) {
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
        hasWaited = waitEndExploration == 0;
        Transition nextEvent;
        boolean isDirectedEvent = false;
        if (hasDirectedInitialPath && !hasFinishedDirectedInitialPath && currentInitialPath == null) {
          nextEvent = nextInitialTransition();
          isDirectedEvent = true;
        } else {
          nextEvent = nextTransition(currentEnabledTransitions);
        }
        if (nextEvent != null) {
          executeEvent(nextEvent, isDirectedEvent);
        } else if (exploredBranchRecorder.getCurrentDepth() == 0) {
          LOG.warn("Finished exploring all states");
        } else if (dmckName.equals("zkChecker-ZAB") && numQueueInitWorkload > 0) {
          if (workloadRetry <= 0) {
            numQueueInitWorkload--;
            workloadRetry = 10;
          }
          workloadRetry--;
          LOG.info(
              "No Transition to execute, but DMCK has not reached termination point. workloadRetry=" + workloadRetry);
          try {
            Thread.sleep(steadyStateTimeout);
          } catch (InterruptedException e) {
            LOG.error("", e);
          }
          continue;
        } else {
          loadNextInitialPath(true, true);
          try {
            pathRecordFile.write("duplicated\n".getBytes());
          } catch (IOException e) {
            LOG.error("", e);
          }
          resetTest();
          break;
        }
      }
    }

    protected void executeEvent(Transition nextEvent, boolean isDirectedEvent) {
      collectDebugNextTransition(nextEvent);
      if (isDirectedEvent) {
        LOG.debug("NEXT TRANSITION IS DIRECTED BY INITIAL PATH=" + nextEvent.toString());
      } else {
        LOG.debug("NEXT TRANSITION=" + nextEvent.toString());
        exploredBranchRecorder.createChild(nextEvent.getTransitionId());
        exploredBranchRecorder.traverseDownTo(nextEvent.getTransitionId());
      }
      try {
        currentExploringPath.add(nextEvent);
        prevOnlineStatus.add(isNodeOnline.clone());
        prevLocalStates.add(copyLocalState(localStates));
        saveLocalState();

        if (nextEvent instanceof AbstractNodeOperationTransition) {
          AbstractNodeOperationTransition nodeOperationTransition = (AbstractNodeOperationTransition) nextEvent;

          if (nodeOperationTransition.getId() > -1) {
            nextEvent = ((AbstractNodeOperationTransition) nextEvent)
                .getRealNodeOperationTransition(nodeOperationTransition.getId());
            LOG.debug("DMCK is going to follow the suggestion to execute=" + nextEvent.toString());
          } else {
            nextEvent = ((AbstractNodeOperationTransition) nextEvent).getRealNodeOperationTransition();
          }
          nodeOperationTransition.setId(((NodeOperationTransition) nextEvent).getId());
        }

        boolean eventAddedToHistory = false;
        LocalState[] oldLocalStates = copyLocalState(localStates);
        Transition event = null;
        if (isSAMC) {
          if (nextEvent instanceof NodeCrashTransition) {
            event = ((NodeCrashTransition) nextEvent).clone();
          } else if (nextEvent instanceof NodeStartTransition) {
            event = ((NodeStartTransition) nextEvent).clone();
          } else if (nextEvent instanceof PacketSendTransition && reductionAlgorithms.contains("symmetry")) {
            event = ((PacketSendTransition) nextEvent).clone();
          }
          eventAddedToHistory = addEventToIncompleteHistory(oldLocalStates, event);
        }

        if (nextEvent.apply()) {
          pathRecordFile.write((nextEvent.toString() + "\n").getBytes());
          updateGlobalState();
          updateSAMCQueueAfterEventExecution(nextEvent);
        }

        if (eventAddedToHistory && reductionAlgorithms.contains("symmetry")) {
          moveIncompleteEventToEventHistory(oldLocalStates, copyLocalState(localStates), event);
        }
      } catch (IOException e) {
        LOG.error("", e);
      }
    }
  }
}
