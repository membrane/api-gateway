package com.predic8.membrane.annot.model;

import java.util.ArrayList;
import java.util.List;

public class Model {
	private List<MainInfo> mains = new ArrayList<MainInfo>();

	public List<MainInfo> getMains() {
		return mains;
	}

	public void setMains(List<MainInfo> mains) {
		this.mains = mains;
	}
}