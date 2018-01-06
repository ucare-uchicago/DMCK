package edu.uchicago.cs.ucare.dmck.transition;

import java.util.LinkedList;
import java.util.Random;

import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

@SuppressWarnings("serial")
public abstract class AbstractNodeOperationTransition extends NodeOperationTransition {

	protected final Random RANDOM = new Random(System.currentTimeMillis());
	protected boolean isRandom;

	protected ModelCheckingServerAbstract checker;

	public AbstractNodeOperationTransition(ModelCheckingServerAbstract checker) {
		id = -1;
		this.checker = checker;
		this.isRandom = false;
		possibleVectorClocks = new int[checker.numNode][][];
	}

	public AbstractNodeOperationTransition(int numNode) {
		id = -1;
		this.checker = null;
		this.isRandom = false;
		possibleVectorClocks = new int[numNode][][];
	}

	public AbstractNodeOperationTransition(ModelCheckingServerAbstract checker, boolean isRandom) {
		id = -1;
		this.checker = checker;
		this.isRandom = isRandom;
		possibleVectorClocks = new int[checker.numNode][][];
	}

	public void setRandom(boolean isRandom) {
		this.isRandom = isRandom;
	}

	public abstract NodeOperationTransition getRealNodeOperationTransition();

	public abstract NodeOperationTransition getRealNodeOperationTransition(int suggestExecuteNodeId);

	public abstract LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions(boolean[] onlineStatus);

	public abstract LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions();

	private int[][][] possibleVectorClocks; // numNode, sender, receiver

	public int[][] getPossibleVectorClock(int id) {
		return possibleVectorClocks[id];
	}

	public void setPossibleVectorClock(int id, int[][] vectorClock) {
		int column = vectorClock[0].length;
		possibleVectorClocks[id] = new int[vectorClock.length][column];
		for (int i = 0; i < vectorClock.length; ++i) {
			for (int j = 0; j < column; ++j) {
				possibleVectorClocks[id][i][j] = vectorClock[i][j];
			}
		}
	}

}
