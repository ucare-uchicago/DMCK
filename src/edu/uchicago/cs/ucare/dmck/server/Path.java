package edu.uchicago.cs.ucare.dmck.server;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;

import edu.uchicago.cs.ucare.dmck.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.dmck.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.dmck.transition.TransitionTuple;

public class Path extends LinkedList<TransitionTuple> implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = 7359356166185399233L;

  private int myId;

  private int myParentId;

	public Path() {
		super();
	}

	public Path(Collection<TransitionTuple> transitionTuples) {
		super(transitionTuples);
	}

	public Path(Path transitionTuples) {
		super(transitionTuples);
	}

	public void setId(int id) {
		this.myId = id;
	}

	public int getId() {
		return myId;
	}

	public void setParentId(int parentId) {
		this.myParentId = parentId;
	}

	public int getParentId() {
		return myParentId;
	}

  public PathMeta toPathMeta() {
    return new PathMeta(myId, myParentId, Path.pathToString(this));
  }

	@Override
	public Path clone() {
		return new Path(this);
	}

	public Path getSerializable(int numNode) {
		Path retVal = new Path(new LinkedList<TransitionTuple>());

		for (TransitionTuple realTuple : this) {
			retVal.add(realTuple.getSerializable(numNode));
		}

		return retVal;
	}

	@Override
	public String toString() {
		return Path.pathToString(this);
	}

	public static String pathToString(Path initialPath) {
		String path = "";
		for (int i = 0; i < initialPath.size(); i++) {
			if (initialPath.get(i).transition instanceof PacketSendTransition) {
				if (i == 0) {
					path = String.valueOf(initialPath.get(i).transition.getTransitionId());
				} else {
					path += "," + String.valueOf(initialPath.get(i).transition.getTransitionId());
				}
			} else {
				if (i == 0) {
					path = ((NodeOperationTransition) initialPath.get(i).transition).toStringForFutureExecution();
				} else {
					path += ","
							+ ((NodeOperationTransition) initialPath.get(i).transition).toStringForFutureExecution();
				}
			}
		}
		return path;
	}
}
