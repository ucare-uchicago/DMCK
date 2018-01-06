package edu.uchicago.cs.ucare.dmck.transition;

@SuppressWarnings("serial")
abstract public class NodeOperationTransition extends Transition {

	public int id;
	public int[][] vectorClock;

	public int getId() {
		return id;
	}

	public void setVectorClock(int[][] vectorClock) {
		int column = vectorClock[0].length;
		this.vectorClock = new int[vectorClock.length][column];
		for (int i = 0; i < vectorClock.length; ++i) {
			for (int j = 0; j < column; ++j) {
				this.vectorClock[i][j] = vectorClock[i][j];
			}
		}
	}

	@Override
	public int[][] getVectorClock() {
		return vectorClock;
	}

	public abstract String toStringForFutureExecution();

}
