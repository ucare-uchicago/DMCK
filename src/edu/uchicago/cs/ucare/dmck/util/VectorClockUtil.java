package edu.uchicago.cs.ucare.dmck.util;

import edu.uchicago.cs.ucare.dmck.event.Event;

public class VectorClockUtil {

	public static int isConcurrent(Event x, Event y) {
		return isConcurrent(x.getVectorClock(), y.getVectorClock());
	}

	public static int isConcurrent(int[][] x, int[][] y) {
		if (doesHappenBefore(x, y)) {
			return -1;
		} else if (doesHappenBefore(y, x)) {
			return 1;
		} else {
			return 0;
		}
	}

	public static String toString(int[][] x) {
		String result = "";
		if (x == null) {
			return null;
		}
		for (int i = 0; i < x.length; ++i) {
			if (i != 0) {
				result += " ";
			}
			result += "[";
			for (int j = 0; j < x.length; ++j) {
				if (j != 0) {
					result += ",";
				}
				result += x[i][j];
			}
			result += "]";
		}
		return result;
	}

	// return true if x happens before y
	// return false otherwise
	private static boolean doesHappenBefore(int[][] x, int[][] y) {
		boolean oneLessVal = false;
		for (int i = 0; i < x.length; ++i) {
			for (int j = 0; j < x[0].length; ++j) {
				if (x[i][j] > y[i][j]) {
					return false;
				}
				oneLessVal = oneLessVal || (x[i][j] < y[i][j]);
			}
		}
		return oneLessVal;
	}

}
