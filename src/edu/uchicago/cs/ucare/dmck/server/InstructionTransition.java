package edu.uchicago.cs.ucare.dmck.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.dmck.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.dmck.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.SleepTransition;
import edu.uchicago.cs.ucare.dmck.transition.Transition;

abstract class InstructionTransition {

	abstract Transition getRealTransition(ModelCheckingServerAbstract checker);

}

class PacketSendInstructionTransition extends InstructionTransition {

	protected final Logger LOG = LoggerFactory.getLogger(this.getClass());;

	long packetId;

	public PacketSendInstructionTransition(long packetId) {
		this.packetId = packetId;
	}

	@Override
	Transition getRealTransition(ModelCheckingServerAbstract checker) {
		if (packetId == 0) {
			return (Transition) checker.currentEnabledTransitions.peekFirst();
		}
		for (int i = 0; i < 20; ++i) {
			for (Object t : checker.currentEnabledTransitions) {
				if (t instanceof PacketSendTransition) {
					PacketSendTransition p = (PacketSendTransition) t;
					if (p.getTransitionId() == packetId) {
						return p;
					}
				}
			}
			try {
				Thread.sleep(checker.steadyStateTimeout / 2);
				checker.updateSAMCQueue();
			} catch (InterruptedException e) {
				LOG.error("", e);
			}
		}
		throw new RuntimeException("No expected enabled packet for " + packetId);
	}

}

class NodeCrashInstructionTransition extends InstructionTransition {

	int id;

	protected NodeCrashInstructionTransition(int id) {
		this.id = id;
	}

	@Override
	Transition getRealTransition(ModelCheckingServerAbstract checker) {
		return new NodeCrashTransition(checker, id);
	}

}

class NodeStartInstructionTransition extends InstructionTransition {

	int id;

	protected NodeStartInstructionTransition(int id) {
		this.id = id;
	}

	@Override
	Transition getRealTransition(ModelCheckingServerAbstract checker) {
		return new NodeStartTransition(checker, id);
	}

}

class SleepInstructionTransition extends InstructionTransition {

	long sleep;

	protected SleepInstructionTransition(long sleep) {
		this.sleep = sleep;
	}

	@Override
	Transition getRealTransition(ModelCheckingServerAbstract checker) {
		return new SleepTransition(sleep);
		/*
		 * return new Transition() {
		 * 
		 * @Override public boolean apply() { try { Thread.sleep(sleep); } catch
		 * (InterruptedException e) { return false; } return true; }
		 * 
		 * @Override public int getTransitionId() { return 0; }
		 * 
		 * @Override public String toString(){ return "Sleep=" + sleep; }
		 * 
		 * @Override public int[][] getVectorClock() { throw new
		 * UnsupportedOperationException("Not implemented"); }
		 * 
		 * };
		 */
	}

}

class ExitInstructionTransaction extends InstructionTransition {

	@SuppressWarnings("serial")
	@Override
	Transition getRealTransition(ModelCheckingServerAbstract checker) {
		return new Transition() {

			@Override
			public long getTransitionId() {
				return 0;
			}

			@Override
			public boolean apply() {
				System.exit(0);
				return true;
			}

			@Override
			public int[][] getVectorClock() {
				throw new UnsupportedOperationException("Not implemented");
			}
		};
	}

}
