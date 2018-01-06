package edu.uchicago.cs.ucare.dmck.rpc;

import java.util.Map;

public interface SamcIpc {

	public void send(String recv, String msgName, Map<String, String> msg) throws Exception;

	// Block until there is that msgName
	public Map<String, String> receive(String msgName) throws Exception;

	// Block until there is at least one message
	public Map<String, Map<String, String>> receive() throws Exception;

	// Block until there is that message or time out
	// If time out, return null
	public Map<String, String> receive(String msgName, long timeout) throws Exception;

	// Block until there is at least one message or time out
	// If time out, return empty map (NOT NULL)
	public Map<String, Map<String, String>> receive(long timeout) throws Exception;

}
