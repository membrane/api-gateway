package com.predic8.membrane.annot.model;

import java.util.ArrayList;
import java.util.List;

import com.predic8.membrane.annot.MCMain;

/**
 * Keeps track of all information during one (or several incremental) compiler runs.
 * 
 * Collects all {@link MCMain}s found.
 */
public class Model {
	private List<MainInfo> mains = new ArrayList<MainInfo>();

	public List<MainInfo> getMains() {
		return mains;
	}

	public void setMains(List<MainInfo> mains) {
		this.mains = mains;
	}
}