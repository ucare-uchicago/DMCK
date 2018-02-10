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
import java.sql.Timestamp;
import java.util.ArrayList;
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
import com.google.gson.Gson;

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
  Path currentExploringPath = new Path(1, 0);

  // record all transition global states before and after
  Hashtable<Long, Transition> allEventsDB;
  LinkedList<AbstractGlobalStates> eventHistory;
  LinkedList<AbstractEventConsequence> eventImpacts;
  int recordedEventHistory;
  int recordedEventImpacts;

  protected boolean isSAMC;
  protected ArrayList<String> reductionAlgorithms;

  String stateDir;

  int numCrash;
  int numReboot;
  int currentCrash;
  int currentReboot;

  LinkedList<boolean[]> prevOnlineStatus;
  LinkedList<LocalState[]> prevLocalStates;

  // record policy effectiveness
  Hashtable<String, Integer> policyRecord;

  // for pathId
  private int lastPathId = 1;
  private int lastDiscardedPathId = -10;

  public ReductionAlgorithmsModelChecker(String dmckName, FileWatcher fileWatcher, int numNode,
      int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir,
      String workingDir, WorkloadDriver workloadDriver, String ipcDir) {
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
    eventHistory = new LinkedList<AbstractGlobalStates>();
    eventImpacts = new LinkedList<AbstractEventConsequence>();
    recordedEventHistory = 0;
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
    loadExistingDataFromFile();
    resetTest();
  }

  private int nextPathId() {
    return ++lastPathId;
  }

  private int nextDiscardedPathId() {
    return --lastDiscardedPathId;
  }

  private void updateLastKnownPathId(int id) {
    if (id < 0) {
      lastDiscardedPathId = Math.min(lastDiscardedPathId, id);
    } else {
      lastPathId = Math.max(lastPathId, id);
    }
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
    currentExploringPath = new Path(1, 0);
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
        for (String algorithm : samcConf.getProperty("reduction_algorithms", "").split(",")) {
          reductionAlgorithms.add(algorithm);
        }

        // sanity check
        if (reductionAlgorithms.contains("symmetry") && nonAbstractEventKeys == null) {
          LOG.error(
              "In samc.conf, you have configured symmetry=true, but you have not specified non_abstract_event.");
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

  @SuppressWarnings("unchecked")
  protected void loadEventConsequences(int numRecord) {
    for (int record = 1; record <= numRecord; record++) {
      File serializedEvConsFile =
          new File(testRecordDirPath + "/" + record + "/serializedEventConsequences");
      if (serializedEvConsFile.exists()) {
        ObjectInputStream ois;
        try {
          ois = new ObjectInputStream(new FileInputStream(serializedEvConsFile));
          ArrayList<AbstractEventConsequence> tempEvImpacts =
              (ArrayList<AbstractEventConsequence>) ois.readObject();
          for (AbstractEventConsequence aec : tempEvImpacts) {
            eventImpacts.add(aec.getRealAbsEvCons(this));
          }
          ois.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    recordedEventImpacts = eventImpacts.size();
  }

  @SuppressWarnings("unchecked")
  protected void loadEventHistory(int numRecord) {
    for (int record = 1; record <= numRecord; record++) {
      File serializedEvHistFile =
          new File(testRecordDirPath + "/" + record + "/serializedEventHistory");
      if (serializedEvHistFile.exists()) {
        ObjectInputStream ois;
        try {
          ois = new ObjectInputStream(new FileInputStream(serializedEvHistFile));
          ArrayList<AbstractGlobalStates> tempEvHistory =
              (ArrayList<AbstractGlobalStates>) ois.readObject();
          for (AbstractGlobalStates ags : tempEvHistory) {
            eventHistory.add(ags.getRealAbsEvCons(this));
          }
          ois.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    recordedEventHistory = eventHistory.size();
  }

  // load existing list of paths to particular queue
  protected void loadPaths(Hashtable<Long, Transition> allEventsDB, LinkedList<Path> pathQueue,
      int numRecord, String fileName) {
    for (int record = 1; record <= numRecord; record++) {
      File initialPathFile = new File(testRecordDirPath + "/" + record + "/" + fileName);
      if (initialPathFile.exists()) {
        try {
          BufferedReader br =
              new BufferedReader(new InputStreamReader(new FileInputStream(initialPathFile)));
          StringBuffer fileContents = new StringBuffer();
          String path;
          while ((path = br.readLine()) != null) {
            fileContents.append(path);
          }
          br.close();

          Gson gson = new Gson();
          PathMeta[] arrPathMeta = gson.fromJson(fileContents.toString(), PathMeta[].class);

          for (PathMeta meta : arrPathMeta) {
            Path initPath = new Path(meta.getId(), meta.getParentId());
            String[] eventIDs = meta.getPathString().trim().split(",");
            for (String eventID : eventIDs) {
              initPath.add(allEventsDB.get(Long.parseLong(eventID)));
            }
            pathQueue.add(initPath);
            updateLastKnownPathId(initPath.getId());
          }
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
          BufferedReader br =
              new BufferedReader(new InputStreamReader(new FileInputStream(listOfPathsFile)));
          StringBuffer fileContents = new StringBuffer();
          String path;
          while ((path = br.readLine()) != null) {
            fileContents.append(path);
          }
          br.close();

          Gson gson = new Gson();
          PathMeta[] arrPathMeta = gson.fromJson(fileContents.toString(), PathMeta[].class);
          for (PathMeta pathMeta : arrPathMeta) {
            pathQueue.add(pathMeta);
            updateLastKnownPathId(pathMeta.getId());
          }
        } catch (FileNotFoundException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  protected void loadExistingDataFromFile() {
    try {
      // Grab the max record directory
      File recordDir = new File(testRecordDirPath);
      File[] listOfRecordDir = recordDir.listFiles();
      int numRecord = listOfRecordDir.length;

      // Load all events DB.
      allEventsDB = loadAllEventsDB();
      if (allEventsDB.size() > 0) {
        LOG.info("Total Events that is loaded to allEventsDB=" + allEventsDB.size());
      }

      // Load eventImpacts.
      loadEventConsequences(numRecord);
      if (eventImpacts.size() > 0) {
        LOG.info("Total AbsEvConsequence that is loaded to eventImpacts=" + eventImpacts.size());
      }

      // Load eventHistory.
      loadEventHistory(numRecord);
      if (eventHistory.size() > 0) {
        LOG.info("Total AGS that is loaded to eventHistory=" + eventHistory.size());
      }

      if (reductionAlgorithms.contains("parallelism")) {
        loadPaths(allEventsDB, importantInitialPaths, numRecord, "importantInitialPathsInQueue");
        loadPaths(allEventsDB, unnecessaryInitialPaths, numRecord,
            "unnecessaryInitialPathsInQueue");
      }

      loadPaths(allEventsDB, initialPaths, numRecord, "initialPathsInQueue");

      for (int i = 1; i <= numRecord; i++) {
        File initialPathFile = new File(testRecordDirPath + "/" + i + "/currentInitialPath");
        if (initialPathFile.exists()) {
          BufferedReader br =
              new BufferedReader(new InputStreamReader(new FileInputStream(initialPathFile)));
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

      if (importantInitialPaths.size() > 0) {
        LOG.info(
            "Total Important Initial Path that has been loaded=" + importantInitialPaths.size());
      }
      if (initialPaths.size() > 0) {
        LOG.info("Total Initial Path that has been loaded=" + initialPaths.size());
      }
      if (unnecessaryInitialPaths.size() > 0) {
        LOG.info("Total Unnecessary Initial Path that has been loaded="
            + unnecessaryInitialPaths.size());
      }

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
    if (this.isSAMC && (this.numCrash > 0 || this.numReboot > 0)) {
      ListIterator<Transition> iter = transitions.listIterator();
      LinkedList<Transition> tempContainer = new LinkedList<Transition>();
      int queueSize = transitions.size();
      int continueCounter = 0;
      while (iter.hasNext()) {
        Transition transition = iter.next();
        // CRS runtime checking
        if (reductionAlgorithms.contains("crs_rss_runtime")) {
          int isSymmetric = isRuntimeCrashRebootSymmetric(transition);

          if (isSymmetric == -1 && continueCounter < queueSize) {
            LOG.debug("Abstract Event has been executed before in the history. transition="
                + transition.toString());
            continueCounter++;
            tempContainer.add(transition);
            continue;
          } else if (isSymmetric >= 0) {
            AbstractNodeOperationTransition tmp = (AbstractNodeOperationTransition) transition;
            tmp.setId(isSymmetric);
            transition = tmp;
            LOG.debug("NextTransition is suggested to transform " + transition.toString() + " to "
                + tmp.getId());
          }
        }
        if (continueCounter >= queueSize) {
          LOG.debug(
              "Queue only consists of uninteresting abstract event. DMCK decided to choose the first event.");
        }
        if (!exploredBranchRecorder.isSubtreeBelowChildFinished(transition.getTransitionId())) {
          return transition;
        }
      }
      if (tempContainer.size() > 0) {
        return tempContainer.getFirst();
      }
    } else if (transitions.size() > 0) {
      return transitions.getFirst();
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

  protected void markPacketsObsolete(int obsoleteBy, int crashingNode,
      LinkedList<Transition> enabledTransitions) {
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

  protected void convertExecutedAbstractTransitionToReal(Path executedPath) {
    ListIterator<Transition> iter = executedPath.listIterator();
    while (iter.hasNext()) {
      Transition iterItem = iter.next();
      if (iterItem instanceof AbstractNodeCrashTransition) {
        AbstractNodeCrashTransition crash = (AbstractNodeCrashTransition) iterItem;
        NodeCrashTransition crashTransition =
            new NodeCrashTransition(ReductionAlgorithmsModelChecker.this, crash.getId());
        crashTransition.setVectorClock(crash.getPossibleVectorClock(crash.getId()));
        iter.set(crashTransition);
      } else if (iterItem instanceof AbstractNodeStartTransition) {
        AbstractNodeStartTransition start = (AbstractNodeStartTransition) iterItem;
        NodeStartTransition startTransition =
            new NodeStartTransition(ReductionAlgorithmsModelChecker.this, start.getId());
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
    currentFinishedInitialPaths.add(path.toPathMeta());
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

      // new pathId inheritance
      initialPath.setParentId(initialPath.getId());
      initialPath.setId(nextPathId());

      initialPaths.add(initialPath);
      addPathToFinishedInitialPath(initialPath);
    }
  }

  public static String getAbstractLocalState(LocalState ls) {
    String result = "[";
    boolean isFirst = true;
    for (String key : abstractGlobalStateKeys) {
      Object v = ls.getValue(key);
      if (v != null) {
        String temp = key + "=" + v;
        if (isFirst) {
          isFirst = false;
          result += temp;
        } else {
          result += ", " + temp;
        }
      }
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

        Object v = msg.getPacket().getValue(key);
        if (v != null) {
          String temp = key + "=" + v;
          if (isFirst) {
            isFirst = false;
            result += temp;
          } else {
            result += ", " + temp;
          }
        }
      }
    } else if (ev instanceof AbstractNodeCrashTransition || ev instanceof NodeCrashTransition) {
      result = "abstract-crash";
    } else if (ev instanceof AbstractNodeStartTransition || ev instanceof NodeStartTransition) {
      result = "abstract-reboot";
    }
    return result;
  }

  public static ArrayList<String> getAbstractEventQueue(LinkedList<Transition> queue) {
    ArrayList<String> absEvQueue = new ArrayList<String>();
    for (Transition ev : queue) {
      absEvQueue.add(getAbstractEvent(ev));
    }
    return absEvQueue;
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
        if (!m1.getPacket().getValue(key).toString()
            .equals(m2.getPacket().getValue(key).toString())) {
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
  protected Path reorderEvents(LocalState[] wasLocalStates, Path initialPath,
      Transition oldTransition, Transition newTransition) {
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

  protected boolean addNewInitialPath(LocalState[] wasLocalStates, Path initialPath,
      Transition oldTransition, Transition newTransition) {
    // mark the initial path plus the old event as explored
    Path oldPath = (Path) initialPath.clone();

    // this path is skipable and only memorized for historical purpose. We assign negative pathId
    // here because we will not explore this path in the future.
    oldPath.setParentId(initialPath.getId());
    oldPath.setId(nextDiscardedPathId());

    convertExecutedAbstractTransitionToReal(oldPath);
    oldPath.addTransition(oldTransition);
    addPathToFinishedInitialPath(oldPath);

    Path newInitialPath = (Path) initialPath.clone();
    convertExecutedAbstractTransitionToReal(newInitialPath);

    Path reorderedEvents =
        reorderEvents(wasLocalStates, newInitialPath, oldTransition, newTransition);

    newInitialPath.addAll(reorderedEvents);

    // check symmetrical path
    if (isSymmetricPath(newInitialPath)) {
      return false;
    }

    if (!pathExistInHistory(newInitialPath)) {
      LOG.info("Transition " + newTransition.getTransitionId() + " needs to be reordered with "
          + oldTransition.getTransitionId());

      // new pathId inheritance
      newInitialPath.setParentId(initialPath.getId());
      newInitialPath.setId(nextPathId());

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
        allEventsDB.put(transition.getTransitionId(), transition);
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
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(new File(idRecordDirPath + "/" + fileName))));
        Iterator<Path> pathsQueueIter = pathsQueue.iterator();

        List<PathMeta> meta = new ArrayList<PathMeta>();
        while (pathsQueueIter.hasNext()) {
          Path path = pathsQueueIter.next();
          meta.add(path.toPathMeta());
        }

        Gson gson = new Gson();
        String paths = gson.toJson(meta);
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
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(new File(idRecordDirPath + "/" + fileName))));

        Gson gson = new Gson();
        String paths = gson.toJson(pathsQueue);
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

  protected void saveEventConsequences() {
    try {
      // Readable event consequence file version.
      BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
          new FileOutputStream(new File(idRecordDirPath + "/" + "eventConsequences"))));
      String eventConsequences = "";
      // Serialized object event consequence file version
      ObjectOutputStream evStream = new ObjectOutputStream(
          new FileOutputStream(idRecordDirPath + "/" + "serializedEventConsequences"));
      ArrayList<AbstractEventConsequence> tempEvImpacts = new ArrayList<AbstractEventConsequence>();
      for (int i = recordedEventImpacts; i < eventImpacts.size(); i++) {
        eventConsequences += eventImpacts.get(i) + "\n";
        tempEvImpacts.add(eventImpacts.get(i).getSerializable(numNode));
      }
      bw.write(eventConsequences);
      bw.close();
      evStream.writeObject(tempEvImpacts);
      evStream.close();

      recordedEventImpacts = eventImpacts.size();
    } catch (FileNotFoundException e) {
      LOG.error("", e);
    } catch (IOException e) {
      LOG.error("", e);
    }
  }

  protected void saveEventHistory() {
    try {
      // Serialized object event consequence file version
      ObjectOutputStream evStream = new ObjectOutputStream(
          new FileOutputStream(idRecordDirPath + "/" + "serializedEventHistory"));
      ArrayList<AbstractGlobalStates> tempEvHistory = new ArrayList<AbstractGlobalStates>();
      for (int i = recordedEventHistory; i < eventHistory.size(); i++) {
        tempEvHistory.add(eventHistory.get(i).getSerializable(numNode));
      }
      evStream.writeObject(tempEvHistory);
      evStream.close();

      recordedEventHistory = eventHistory.size();
    } catch (FileNotFoundException e) {
      LOG.error("", e);
    } catch (IOException e) {
      LOG.error("", e);
    }
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
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(new File(idRecordDirPath + "/currentInitialPath"))));
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

        // new pathId inheritance
        int parentId = newPath.getId() < 0 ? newPath.getFirstParentId() : newPath.getId();
        newPath.getPath().setId(parentId);
        newPath.getPath().setId(nextPathId());

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
    saveEventHistory();

    backtrackExecutedPath();
    printPaths("Initial Paths", initialPaths);

    if (reductionAlgorithms.contains("parallelism")) {
      evaluateParallelismInitialPaths();
    }

    saveGeneratedInitialPaths();
  }

  Hashtable<Transition, List<Transition>> dependencies =
      new Hashtable<Transition, List<Transition>>();

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
    ListIterator<Transition> currentIter =
        (ListIterator<Transition>) realExecutionPath.listIterator(realExecutionPath.size());
    while (currentIter.hasPrevious()) {
      Transition current = currentIter.previous();
      LinkedList<Transition> partialOrder = new LinkedList<Transition>();
      if (currentIter.hasPrevious()) {
        ListIterator<Transition> comparingIter =
            realExecutionPath.listIterator(currentIter.nextIndex());
        while (comparingIter.hasPrevious()) {
          Transition comparing = comparingIter.previous();
          int compareResult =
              VectorClockUtil.isConcurrent(current.getVectorClock(), comparing.getVectorClock());
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

  @Override
  public void collectPerformancePerPathMetrics() {
    // Performance evaluation: Collect time spent to execute a single path
    endTimePathExecution = new Timestamp(System.currentTimeMillis());
    long totalPathExecutionTime = endTimePathExecution.getTime() - startTimePathExecution.getTime();

    String content = "-------\n";
    content += "SUMMARY\n";
    content += "-------\n";
    content += "total-execution-path-time=" + totalPathExecutionTime + "ms;\n";
    content += "total-ags-in-eventHistory=" + eventHistory.size() + ";\n";
    try {
      performanceRecordFile.write(content.getBytes());
    } catch (Exception e) {
      LOG.error("", e);
    }
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
      BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
          new FileOutputStream(new File(workingDirPath + "/policyEffect.txt"))));

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
              LOG.debug("SYMMETRY CHECK: node with state=" + globalStates[nodeId].toString()
                  + " executes " + ags.getEvent().toString() + " transform to unknown state");
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
    return findLocalStateChange(localStates, event) != null;
  }

  public boolean addEventToHistory(LocalState[] globalStateBefore, LocalState[] globalStateAfter,
      Transition event, ArrayList<String> causalNewEvents) {
    AbstractGlobalStates ags = new AbstractGlobalStates(globalStateBefore, event);
    boolean isNewAGS = true;
    int index = 0;
    while (index < eventHistory.size()) {
      if (eventHistory.get(index).equals(ags)) {
        isNewAGS = false;
        break;
      }
      index++;
    }
    if (isNewAGS) {
      ags.setAbstractGlobalStateAfter(globalStateAfter);
      ags.setCausalNewEvents(causalNewEvents);

      eventHistory.add(ags);
      LOG.debug("New AGS=\n" + ags.toString());

      boolean isNewAEC = true;
      AbstractEventConsequence aec = ags.getAbstractEventConsequence();
      for (AbstractEventConsequence recordedAEC : eventImpacts) {
        if (recordedAEC.isIdentical(aec)) {
          isNewAEC = false;
          break;
        }
      }
      if (isNewAEC) {
        LOG.debug("NEW EVENT CONSEQUENCES=" + aec.toString());
        eventImpacts.add(aec);
      }
    } else {
      if (eventHistory.get(index).getAbstractGlobalStateAfter() == null) {
        eventHistory.get(index).setAbstractGlobalStateAfter(globalStateAfter);
      }
      if (eventHistory.get(index).getCausalAbsNewEvents() == null) {
        eventHistory.get(index).setCausalNewEvents(causalNewEvents);
      }
    }
    return isNewAGS;
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

  public LocalState findLocalStateChange(LocalState[] prevGS, Transition currentEv) {
    AbstractGlobalStates ags = new AbstractGlobalStates(prevGS, currentEv);
    for (AbstractGlobalStates historicalAGS : eventHistory) {
      if (historicalAGS.equals(ags)) {
        return historicalAGS.getExecutingNodeAfterState();
      }
    }
    return null;
  }

  protected abstract void backtrackExecutedPath();

  protected abstract int isRuntimeCrashRebootSymmetric(Transition nextTransition);

  protected Transition transformStringToTransition(LinkedList<Transition> currentEnabledTransitions,
      String nextEvent) {
    Transition t = null;
    if (nextEvent.startsWith("nodecrash")) {
      int id = Integer.parseInt(nextEvent.substring(13).trim());
      NodeCrashTransition crashTransition =
          new NodeCrashTransition(ReductionAlgorithmsModelChecker.this, id);
      boolean absExist = false;
      for (Transition tt : currentEnabledTransitions) {
        if (tt instanceof AbstractNodeCrashTransition) {
          absExist = true;
          crashTransition
              .setVectorClock(((AbstractNodeCrashTransition) tt).getPossibleVectorClock(id));
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
          startTransition
              .setVectorClock(((AbstractNodeStartTransition) tt).getPossibleVectorClock(id));
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
      if (event instanceof PacketSendTransition
          && dmckQueue.get(index) instanceof PacketSendTransition
          && event.getTransitionId() == dmckQueue.get(index).getTransitionId()) {
        return dmckQueue.get(index);
      } else if (event instanceof NodeCrashTransition
          && dmckQueue.get(index) instanceof AbstractNodeCrashTransition) {
        AbstractNodeCrashTransition absCrashEvent =
            (AbstractNodeCrashTransition) dmckQueue.get(index);
        int crashId = ((NodeCrashTransition) event).getId();
        NodeCrashTransition crashEvent = new NodeCrashTransition(this, crashId);
        crashEvent.setVectorClock(absCrashEvent.getPossibleVectorClock(crashId));
        return crashEvent;
      } else if (event instanceof NodeStartTransition
          && dmckQueue.get(index) instanceof AbstractNodeStartTransition) {
        AbstractNodeStartTransition absRebootEvent =
            (AbstractNodeStartTransition) dmckQueue.get(index);
        int rebootId = ((NodeStartTransition) event).getId();
        NodeStartTransition rebootEvent = new NodeStartTransition(this, rebootId);
        rebootEvent.setVectorClock(absRebootEvent.getPossibleVectorClock(rebootId));
        return rebootEvent;
      }
    }
    return null;
  }

  @Override
  public void waitForCausalNewEvents(PacketSendTransition event) throws InterruptedException {
    // 1. Get current abstract global states.
    LocalState[] prevOnlineState = getLatestGlobalState();
    AbstractGlobalStates currentAGS = new AbstractGlobalStates(prevOnlineState, event);

    // 2. Get expected causal new events.
    ArrayList<String> expectedCausalNewEvents = null;
    int counter = 0;
    for (AbstractGlobalStates historicalAGS : eventHistory) {
      if (currentAGS.equals(historicalAGS)) {
        LOG.debug("matched historicalAGS=" + historicalAGS.toString());
        expectedCausalNewEvents = historicalAGS.getCausalAbsNewEvents();
        break;
      }
      counter++;
    }

    // 3. Wait for the expectedCausalNewEvents
    if (expectedCausalNewEvents == null) {
      LOG.warn("DMCK can predict the global state after the event is executed, "
          + "but DMCK cannot get the expected causal new events.");
      waitNodeSteady(event.getPacket().getToId());
    } else {
      if (expectedCausalNewEvents.size() > 0) {
        boolean waitLonger = true;
        int matchEvents = 0;
        String log = "As after effect of executing event=" + event.toString()
            + " wait for expected causal new events=" + expectedCausalNewEvents.size() + ":\n";
        for (String newAbsEv : expectedCausalNewEvents) {
          log += newAbsEv + "\n";
        }
        log += "Found in eventHistory, ags=" + counter + "\n";
        log += "Here is the EventHistory AGS=" + eventHistory.get(counter).toString();
        LOG.debug(log);
        while (waitLonger) {
          for (Transition queuedEvent : currentEnabledTransitions) {
            if (queuedEvent instanceof PacketSendTransition) {
              PacketSendTransition msgEvent = (PacketSendTransition) queuedEvent;
              if (expectedCausalNewEvents.contains(getAbstractEvent(queuedEvent))
                  && event.getPacket().getToId() == msgEvent.getPacket().getFromId()) {
                matchEvents++;
                if (matchEvents == expectedCausalNewEvents.size()) {
                  waitLonger = false;
                }
              }
            }
          }
          updateSAMCQueueWithoutLog();
        }
        LOG.debug("DMCK has intercepted all causal new events that are expected.");
      } else {
        LOG.debug("DMCK does not wait for any expected causal new events.");
      }
      setNodeSteady(event.getId(), true);
    }
  }

  public LocalState[] getLatestGlobalState() {
    if (prevLocalStates.size() == 0) {
      return getInitialGlobalStates();
    } else {
      return prevLocalStates.getLast();
    }
  }

  class PathTraversalWorker extends Thread {

    private LocalState[] currentGlobalState;
    private LocalState predictedLS;

    @Override
    public void run() {
      int workloadRetry = 10;
      // 1. Follow initial path if it exists.
      if (currentInitialPath != null && !currentInitialPath.isEmpty()) {
        LOG.info("Start with existing initial path first.");
        String tmp = "Current Initial Path:\n";
        for (Transition event : currentInitialPath) {
          tmp += event.toString() + "\n";
        }
        LOG.info(tmp);
        collectDebug(tmp);
        int transitionCounter = 0;
        for (Transition expectedEvent : currentInitialPath) {
          transitionCounter++;
          executeMidWorkload();
          updateSAMCQueueWithoutLog();

          // 2a. Transform expected event into real event from and remove it from DMCK Queue
          Transition nextEvent = null;
          int retryCounter = 0;
          while (retryCounter < 20) {
            nextEvent = retrieveEventFromQueue(currentEnabledTransitions, expectedEvent);
            if (nextEvent != null) {
              break;
            }
            retryCounter = waitForExpectedEvent(retryCounter);
          }
          if (nextEvent == null) {
            LOG.error("ERROR: Expected to execute " + expectedEvent
                + ", but the event was not in DMCK Queue.");
            recordEventToPathFile("Expected event cannot be found in DMCK Queue. "
                + "DMCK was looking for event with id=" + expectedEvent.toString());
            if (!initialPathSecondAttempt.contains(currentInitialPath.toPathMeta())) {
              currentUnnecessaryInitialPaths.addFirst(currentInitialPath);
              LOG.warn(
                  "Try this initial path once again, but at low priority. Total Low Priority Initial Path="
                      + (unnecessaryInitialPaths.size() + currentUnnecessaryInitialPaths.size())
                      + " Total Initial Path Second Attempt=" + initialPathSecondAttempt.size());
              initialPathSecondAttempt.add(currentInitialPath.toPathMeta());
            }
            loadNextInitialPath(true, false);
            LOG.warn("---- Quit of Path Execution because an error ----");
            resetTest();
            return;
          } else {
            // 3a. Execute the real event.
            executeEvent(nextEvent, transitionCounter <= directedInitialPath.size());
          }
        }
      }
      // 2b. If current initial path is null then continue path execution with FIFO
      LOG.info("Finding new path/Continuing from Initial Path");
      boolean hasWaited = waitEndExploration == 0;
      while (true) {
        executeMidWorkload();
        updateSAMCQueueWithoutLog();
        boolean terminationPoint = checkTerminationPoint(currentEnabledTransitions);

        // 3b. DMCK checks whether it reaches termination point or not
        if (terminationPoint && hasWaited) {
          collectDebugData(localStates);
          LOG.info("---- End of Path Execution ----");

          // Performance evaluation
          endTimePathExecution = new Timestamp(System.currentTimeMillis());
          collectPerformancePerEventMetrics();
          collectPerformancePerPathMetrics();

          boolean verifiedResult = verifier.verify();
          String detail = verifier.verificationDetail();
          saveResult(verifiedResult, detail);
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
          LOG.info("---- End of Path Evaluation ----");
          resetTest();
          break;
        } else if (terminationPoint) {
          try {
            if (dmckName.equals("raftModelChecker") && waitForNextLE
                && waitedForNextLEInDiffTermCounter < 20) {
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

        // 4b. Decide the next event based on guided path or FIFO
        Transition nextEvent;
        boolean isDirectedEvent = false;
        if (hasDirectedInitialPath && !hasFinishedDirectedInitialPath
            && currentInitialPath == null) {
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
              "No Transition to execute, but DMCK has not reached termination point. workloadRetry="
                  + workloadRetry);
          try {
            Thread.sleep(steadyStateTimeout);
          } catch (InterruptedException e) {
            LOG.error("", e);
          }
          continue;
        } else {
          loadNextInitialPath(true, true);
          recordEventToPathFile("Duplicated path.");
          resetTest();
          break;
        }
      }
    }

    protected void updateCurrentGlobalState() {
      if (quickEventReleaseMode) {
        if (prevLocalStates.size() == 0) {
          currentGlobalState = getInitialGlobalStates();
        } else {
          int nodeId = currentExploringPath.getLast().getId();
          currentGlobalState = copyLocalState(prevLocalStates.getLast());
          if (isQuickEventStep) {
            currentGlobalState[nodeId] = predictedLS.clone();
            collectDebug("QUICK EVENT: predictedLS=" + predictedLS.toString() + "\n");
            addLocalStatesUpdate(nodeId, currentGlobalState[nodeId]);
          } else {
            currentGlobalState[nodeId] = localStates[nodeId].clone();
            collectDebug("SLOW EVENT: updatedLS=" + localStates[nodeId].toString() + "\n");
          }
        }
      } else {
        currentGlobalState = copyLocalState(localStates);
      }
    }

    protected void updateIsQuickEventStep() {
      if (quickEventReleaseMode) {
        predictedLS =
            findLocalStateChange(prevLocalStates.getLast(), currentExploringPath.getLast());
        isQuickEventStep = predictedLS != null;
        if (isQuickEventStep) {
          LOG.debug("IS QUICK EVENT, PredictedLS=" + predictedLS.toString());
        } else {
          LOG.debug("IS SLOW EVENT");
        }
      }
    }

    protected void executeEvent(Transition nextEvent, boolean isDirectedEvent) {
      if (isDirectedEvent) {
        LOG.debug("NEXT TRANSITION IS DIRECTED BY INITIAL PATH=" + nextEvent.toString());
      } else {
        LOG.debug("NEXT TRANSITION=" + nextEvent.toString());
        exploredBranchRecorder.createChild(nextEvent.getTransitionId());
        exploredBranchRecorder.traverseDownTo(nextEvent.getTransitionId());
      }

      // Transform abstract event to real event
      if (nextEvent instanceof AbstractNodeOperationTransition) {
        AbstractNodeOperationTransition nodeOperationTransition =
            (AbstractNodeOperationTransition) nextEvent;

        if (nodeOperationTransition.getId() > -1) {
          nextEvent = ((AbstractNodeOperationTransition) nextEvent)
              .getRealNodeOperationTransition(nodeOperationTransition.getId());
          LOG.debug("DMCK is going to follow the suggestion to execute=" + nextEvent.toString());
        } else {
          nextEvent =
              ((AbstractNodeOperationTransition) nextEvent).getRealNodeOperationTransition();
        }
        nodeOperationTransition.setId(((NodeOperationTransition) nextEvent).getId());
      }

      updateCurrentGlobalState();

      currentExploringPath.add(nextEvent);
      prevOnlineStatus.add(isNodeOnline.clone());
      prevLocalStates.add(copyLocalState(currentGlobalState));

      // Collect Logs
      collectDebugData(prevLocalStates.getLast());

      // Remove real next event from DMCK queue
      removeEventFromQueue(currentEnabledTransitions, nextEvent);

      // Get List of Prev Events in Queue
      ArrayList<String> prevQueue = new ArrayList<String>();
      for (Transition ev : currentEnabledTransitions) {
        String absEv = getAbstractEvent(ev);
        prevQueue.add(absEv);
      }

      // Let DMCK predict the global state changes
      updateIsQuickEventStep();

      // If current event will be released quickly,
      // DMCK needs to guarantee that the current localStates
      // is the same with what is recorded, before enable another event.
      if (isQuickEventStep) {
        localStates = currentGlobalState.clone();
      }

      // Collect Logs
      collectDebugNextTransition(nextEvent);

      if (nextEvent.apply()) {
        recordEventToPathFile(nextEvent.toString());
        updateSAMCQueueAfterEventExecution(nextEvent);
        updateSAMCQueueWithoutLog();
      }

      // Add lists of state changes together to the local states update.
      addOrIgnorePerBatchUpdates();

      if (isSAMC) {
        Transition concreteEvent = null;
        if (nextEvent instanceof NodeCrashTransition) {
          concreteEvent = ((NodeCrashTransition) nextEvent).clone();
        } else if (nextEvent instanceof NodeStartTransition) {
          concreteEvent = ((NodeStartTransition) nextEvent).clone();
        } else if (nextEvent instanceof PacketSendTransition
            && (reductionAlgorithms.contains("symmetry") || quickEventReleaseMode)) {
          concreteEvent = ((PacketSendTransition) nextEvent).clone();
        }

        // Get List of Latest Events in Queue
        ArrayList<String> newCausalEvents = new ArrayList<String>();
        for (Transition ev : currentEnabledTransitions) {
          String absEvent = getAbstractEvent(ev);
          boolean isNewEv = true;
          int i = 0;
          while (i < prevQueue.size()) {
            if (prevQueue.get(i).equals(absEvent)) {
              isNewEv = false;
              break;
            }
            i++;
          }
          if (isNewEv) {
            newCausalEvents.add(absEvent);
          } else {
            prevQueue.remove(i);
          }
        }

        // Add new AGS into eventHistory
        if (concreteEvent != null && !isQuickEventStep) {
          addEventToHistory(currentGlobalState, copyLocalState(localStates), concreteEvent,
              newCausalEvents);
        }
      }
    }
  }
}
