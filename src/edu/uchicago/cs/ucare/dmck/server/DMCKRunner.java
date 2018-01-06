package edu.uchicago.cs.ucare.dmck.server;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.dmck.util.SpecVerifier;
import edu.uchicago.cs.ucare.dmck.util.WorkloadDriver;

public class DMCKRunner {

	final static Logger LOG = LoggerFactory.getLogger(DMCKRunner.class);

	static WorkloadDriver workloadDriver;
	static FileWatcher fileWatcher;
	static SpecVerifier verifier;
	static ModelCheckingServerAbstract dmck;

	static boolean pausePerPath = false;
	static String dmckName;
	static String workingDir;
	static String ipcDir;
	static String dmckDir;
	static String targetSysDir;
	static String directedInitialPath;
	static String expectedResultPath;
	static String explorationStrategy;
	static String fileWatcherClass;
	static String verifierClass;
	static String workloadDriverClass;
	static String testRecordDir;
	static String traversalRecordDir;
	static int numNode;
	static int numCrash;
	static int numReboot;
	static int numInitWorkload;
	static int numMidWorkload;

	static String ackName = "Ack";

	public static void main(String[] args) {
		if (args.length == 1) {
			if (args[0].equals("-p")) {
				pausePerPath = true;
			}
		}

		// load target-sys.conf to the DMCK
		loadTargetSysConfigFile();

		// prepare all components of the DMCK
		initializeDMCK();

		// DMCK starts exploring all necessary scenarios
		startExploration();

	}

	private static void loadTargetSysConfigFile() {
		try {
			FileInputStream fis = new FileInputStream("./target-sys.conf");
			Properties targetSysConfig = new Properties();
			targetSysConfig.load(fis);
			fis.close();

			// mandatory configuration in target-sys.conf
			dmckName = targetSysConfig.getProperty("dmck_name");
			workingDir = targetSysConfig.getProperty("working_dir");
			dmckDir = targetSysConfig.getProperty("dmck_dir");
			targetSysDir = targetSysConfig.getProperty("target_sys_dir");
			explorationStrategy = targetSysConfig.getProperty("exploring_strategy");
			fileWatcherClass = targetSysConfig.getProperty("file_watcher");
			verifierClass = targetSysConfig.getProperty("verifier");
			workloadDriverClass = targetSysConfig.getProperty("workload_driver");
			testRecordDir = targetSysConfig.getProperty("test_record_dir");
			traversalRecordDir = targetSysConfig.getProperty("traversal_record_dir");
			numNode = Integer.parseInt(targetSysConfig.getProperty("num_node"));

			// optional configuration in target-sys.conf
			ipcDir = targetSysConfig.getProperty("ipc_dir", "/tmp/ipc");
			directedInitialPath = targetSysConfig.getProperty("initial_path", "");
			expectedResultPath = targetSysConfig.getProperty("expected_result_path", "");
			numCrash = Integer.parseInt(targetSysConfig.getProperty("num_crash", "0"));
			numReboot = Integer.parseInt(targetSysConfig.getProperty("num_reboot", "0"));
			numInitWorkload = Integer.parseInt(targetSysConfig.getProperty("num_init_workload", "0"));
			numMidWorkload = Integer.parseInt(targetSysConfig.getProperty("num_mid_workload", "0"));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private static void initializeDMCK() {
		try {
			// prepare Workload Driver that starts each target system node
			Class<? extends WorkloadDriver> wdClass = (Class<? extends WorkloadDriver>) Class
					.forName(workloadDriverClass);
			Constructor<? extends WorkloadDriver> wdConstructor = wdClass.getConstructor(Integer.TYPE, String.class,
					String.class, String.class, String.class);
			workloadDriver = wdConstructor.newInstance(numNode, workingDir, ipcDir, dmckDir, targetSysDir);

			// prepare File Watcher that receive the event information from the Interception
			// Layer
			Class<? extends FileWatcher> fwClass = (Class<? extends FileWatcher>) Class.forName(fileWatcherClass);
			Constructor<? extends FileWatcher> fwConstructor = fwClass.getConstructor(String.class,
					ModelCheckingServerAbstract.class);
			fileWatcher = fwConstructor.newInstance(ipcDir, dmck);

			// prepare Verifier that check the global states correctness
			Class<? extends SpecVerifier> vClass = (Class<? extends SpecVerifier>) Class.forName(verifierClass);
			Constructor<? extends SpecVerifier> vConstructor = vClass.getConstructor();
			verifier = vConstructor.newInstance();
			workloadDriver.setVerifier(verifier);

			// determine the DMCK exploring strategy
			Class<? extends ModelCheckingServerAbstract> dmckStrategyClass = (Class<? extends ModelCheckingServerAbstract>) Class
					.forName(explorationStrategy);
			LOG.info("DMCK exploration strategy=" + explorationStrategy);

			if (GuideModelChecker.class.isAssignableFrom(dmckStrategyClass)) {
				if (directedInitialPath.isEmpty()) {
					throw new RuntimeException(
							"initial_path in target-sys.conf has not been specified. DMCK cannot proceed with GuideModelChecker strategy.");
				}
				LOG.info("DMCK follows path in " + directedInitialPath);
				File pathFile = new File(directedInitialPath);
				Constructor<? extends ModelCheckingServerAbstract> guideDMCKConstructor = dmckStrategyClass
						.getConstructor(String.class, FileWatcher.class, Integer.TYPE, String.class, File.class,
								String.class, WorkloadDriver.class, String.class);
				dmck = guideDMCKConstructor.newInstance(dmckName, fileWatcher, numNode, testRecordDir, pathFile,
						workingDir, workloadDriver, ipcDir);
			} else {
				Constructor<? extends ModelCheckingServerAbstract> dmckConstructor = dmckStrategyClass.getConstructor(
						String.class, FileWatcher.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, String.class,
						String.class, String.class, WorkloadDriver.class, String.class);
				dmck = dmckConstructor.newInstance(dmckName, fileWatcher, numNode, numCrash, numReboot, testRecordDir,
						traversalRecordDir, workingDir, workloadDriver, ipcDir);
			}

			// connect all the components together
			dmck.setDirectedInitialPath(directedInitialPath);
			dmck.setExpectedResultPath(expectedResultPath);
			dmck.setInitWorkload(numInitWorkload);
			dmck.setMidWorkload(numMidWorkload);

			fileWatcher.setDMCK(dmck);
			verifier.modelCheckingServer = dmck;

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void startExploration() {
		// last preparation before DMCK starts to explore the paths
		File waitingFlag = new File(workingDir + "/state/.waiting");
		File finishedFlag = new File(workingDir + "/state/.finished");
		File recordDir = new File(workingDir + "/record");
		int testId = recordDir.list().length + 1;

		try {
			// hook to stop the target system nodes when DMCK is suddenly stopped
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					dmck.stopEnsemble();
				}
			});

			// DMCK exploration cycle
			for (; !finishedFlag.exists(); ++testId) {
				// reset phase
				waitingFlag.delete();
				workloadDriver.resetTest(testId);
				dmck.setTestId(testId);
				dmck.startEnsemble();
				dmck.waitOnFirstSteadyStates();

				// DMCK waits for all events to be exercised
				while (!waitingFlag.exists()) {
					Thread.sleep(30);
				}

				// if it exists the while loop, DMCK stops the target system nodes
				dmck.stopEnsemble();

				// DMCK determines whether to stop or to continue paths exploration
				if (dmck.hasReproducedBug()) {
					LOG.info("DMCK has reproduced the expected bug.");
					break;
				} else if (dmck.hasNoMoreInterestingPath()) {
					LOG.info("There is no more interesting Initial Paths. Finished exploring all states.");
					break;
				}

				// pause DMCK exploration if developer request to pause path explorations after
				// every single path execution
				if (pausePerPath) {
					System.out.println("Press enter to continue...");
					System.in.read();
				}
			}

			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
