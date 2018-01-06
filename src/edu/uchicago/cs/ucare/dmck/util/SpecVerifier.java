package edu.uchicago.cs.ucare.dmck.util;

import edu.uchicago.cs.ucare.dmck.server.ModelCheckingServerAbstract;

public abstract class SpecVerifier {

	public ModelCheckingServerAbstract modelCheckingServer;

	public boolean verify() {
		return true;
	}

	public String verificationDetail() {
		return "No Verification has been set";
	}

}