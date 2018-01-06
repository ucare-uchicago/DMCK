package edu.uchicago.cs.ucare.dmck.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class LocalState implements Serializable {

	private static final long serialVersionUID = -1387531586954202891L;

	protected volatile Map<String, Serializable> keyValuePairs;

	public LocalState() {
		keyValuePairs = new HashMap<String, Serializable>();
	}

	public synchronized void setKeyValue(String key, Serializable value) {
		keyValuePairs.put(key, value);
	}

	public synchronized Object getValue(String key) {
		return keyValuePairs.get(key);
	}

	public synchronized void deleteKey(String key) {
		keyValuePairs.remove(key);
	}

	public String[] getAllKeys() {
		return keyValuePairs.keySet().toArray(new String[keyValuePairs.keySet().size()]);
	}

	public String getRaftStateName() {
		String result = "";
		switch ((int) getValue("state")) {
		case 0:
			result = "FOLLOWER";
			break;
		case 1:
			result = "CANDIDATE";
			break;
		case 2:
			result = "LEADER";
			break;
		case 3:
			result = "HARD-CRASH";
			break;
		default:
			result = "UNSET";
		}
		return result;
	}

	@Override
	public String toString() {
		String result = "[";
		Set<String> keys = keyValuePairs.keySet();
		for (Iterator<String> i = keys.iterator(); i.hasNext();) {
			String key = (String) i.next();
			result += key + "=" + keyValuePairs.get(key);
			if (i.hasNext()) {
				result += ", ";
			}
		}
		result += "]";
		return result;
	}

	public static String toString(LocalState[] localstates) {
		String result = "";
		for (LocalState ls : localstates) {
			result += ls.toString() + "\n";
		}
		return result;
	}

	public synchronized LocalState clone() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(this);

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (LocalState) ois.readObject();
		} catch (IOException e) {
			return null;
		} catch (ClassNotFoundException e) {
			return null;
		}
	}
}
