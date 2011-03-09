/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.plugin.membrane.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;

public class RuleDetailsComposite extends GridPanel {

	Label labelTitle;

	Label labelTargetHost;
	Label labelTargetPort;

	Label labelMethod;
	Label labelListenPort;
	Label labelPath;
	Label labelHost;

	Group ruleGroup;
	Group ruleTargetGroup;

	private String targetHost = "";

	private int totalImageWidth;

	private char[] hostCharacters = new char[0];

	private int hostCharactersLength;

	public RuleDetailsComposite(Composite parent) {
		super(parent, 10, 2);
		
		Composite compositeText = createCompositeText();

		GridData wgd = new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
		wgd.grabExcessHorizontalSpace = true;
		
		
		labelTitle = new Label(compositeText, SWT.NONE);
		labelTitle.setText("Rule Description");
		labelTitle.setLayoutData(wgd);

		createLabelSeparator(compositeText, wgd);

		new Label(compositeText, SWT.NONE).setLayoutData(wgd);

		ruleGroup = createRuleGroup(compositeText);

		labelHost = new Label(ruleGroup, SWT.NONE);
		labelHost.setLayoutData(wgd);
		
		labelListenPort = new Label(ruleGroup, SWT.NONE);
		labelListenPort.setLayoutData(wgd);
		
		labelMethod = new Label(ruleGroup, SWT.NONE);
		labelMethod.setLayoutData(wgd);
		
		labelPath = new Label(ruleGroup, SWT.NONE);
		labelPath.setLayoutData(wgd);
		
		ruleTargetGroup = createRuleTargetGroup(compositeText);

		labelTargetHost = new Label(ruleTargetGroup, SWT.NONE);
		labelTargetHost.setLayoutData(wgd);
		
		labelTargetPort = new Label(ruleTargetGroup, SWT.NONE);
		labelTargetPort.setLayoutData(wgd);
		
		final Composite compositeCanvas = new Composite(this, SWT.NONE);
		compositeCanvas.setLayoutData(new GridData(GridData.FILL_BOTH));
		compositeCanvas.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_BLUE));

		final FillLayout fillLayout = new FillLayout(SWT.VERTICAL);
		compositeCanvas.setLayout(fillLayout);

		final Image imgNet1 = getImageFromRegistry(ImageKeys.IMAGE_INTERNET);
		final Image imgLogo = getImageFromRegistry(ImageKeys.IMAGE_MEMBRANE);
		final Image imgService = getImageFromRegistry(ImageKeys.IMAGE_SERVICE);
		final Image imgConsumer = getImageFromRegistry(ImageKeys.IMAGE_CONSUMER);

		final int consumerImageWidth = imgConsumer.getImageData().width;
		final int membraneImageWidth = imgLogo.getImageData().width;
		final int serviceImageWidth = imgService.getImageData().width;
		final int internetImageWidth = imgNet1.getImageData().width;
		final int internetImageHeight = imgNet1.getImageData().height;

		totalImageWidth = consumerImageWidth + membraneImageWidth + 2 * internetImageWidth + serviceImageWidth + 16;

		Canvas canvas = new Canvas(compositeCanvas, SWT.DOUBLE_BUFFERED);
		canvas.addPaintListener(new PaintListener() {
			public void paintControl(final PaintEvent event) {
				
				int startX = compositeCanvas.getSize().x / 2 - totalImageWidth / 2;
				event.gc.drawImage(imgConsumer, 0, 0, consumerImageWidth, imgConsumer.getImageData().height, startX, compositeCanvas.getSize().y / 4 - 50, consumerImageWidth, imgConsumer.getImageData().height);
				event.gc.drawImage(imgNet1, 0, 0, internetImageWidth, internetImageHeight, startX + 100, compositeCanvas.getSize().y / 4, internetImageWidth, internetImageHeight);
				event.gc.drawImage(imgLogo, 0, 0, membraneImageWidth, imgLogo.getImageData().height, startX + 158, compositeCanvas.getSize().y / 4 - 50, membraneImageWidth, imgLogo.getImageData().height);
				event.gc.drawString(labelListenPort.getText(), startX + 167, compositeCanvas.getSize().y / 4 + imgLogo.getImageData().height / 2, true);
				event.gc.drawString(labelMethod.getText(), startX + 167, compositeCanvas.getSize().y / 4 + imgLogo.getImageData().height / 2 + 15, true);
				event.gc.drawImage(getImageFromRegistry(ImageKeys.IMAGE_INTERNET), 0, 0, internetImageWidth, internetImageHeight, startX + 264, compositeCanvas.getSize().y / 4, internetImageWidth, internetImageHeight);
				event.gc.drawImage(imgService, 0, 0, serviceImageWidth, imgService.getImageData().height, startX + 326, compositeCanvas.getSize().y / 4 - 50, serviceImageWidth, imgService.getImageData().height);

				hostCharactersLength = 0;
				for (char c : hostCharacters) {
					hostCharactersLength += event.gc.getCharWidth(c);
				}
				event.gc.drawString(targetHost, startX + 372 - hostCharactersLength / 2, compositeCanvas.getSize().y / 4 + 10, true);
				event.gc.drawString(labelTargetPort.getText(), startX + 336, compositeCanvas.getSize().y / 4 + 105, true);
				
			}
		});

		layout();
	}

	private void createLabelSeparator(Composite compositeText, GridData gd) {
		Label label = new Label(compositeText, SWT.SEPARATOR | SWT.HORIZONTAL);
		label.setLayoutData(gd);
	}

	private Group createRuleGroup(Composite groupComposite) {
		Group group = new Group(groupComposite, SWT.NONE);
		group.setText("Rule Key");
		
		GridData gData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
		gData.grabExcessHorizontalSpace = true;
		group.setLayoutData(gData);
		
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		
		group.setLayout(layout);
		return group;
	}

	private Group createRuleTargetGroup(Composite groupComposite) {
		Group group = new Group(groupComposite, SWT.NONE);
		group.setText("Target");
		GridData gData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
		gData.grabExcessHorizontalSpace = true;
		gData.heightHint = 68;

		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		
		group.setLayoutData(gData);
		group.setLayout(layout);
		
		return group;
	}

	private Image getImageFromRegistry(String imageKey) {
		return MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(imageKey).createImage();
	}

	private Composite createCompositeText() {
		Composite composite = new Composite(this, SWT.NONE);
		
		GridData layoutData = new GridData(GridData.FILL_VERTICAL);
		layoutData.widthHint = 220;
		composite.setLayoutData(layoutData);

		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		composite.setLayout(layout);
		return composite;
	}

	public void configure(Rule rule) {
		if (rule == null) {
			reset();
			return;
		}
		if (rule instanceof ForwardingRule) {
			displayForwardingRuleDetails((ForwardingRule) rule);
		} else if (rule instanceof ProxyRule) {
			displayProxyRuleDetails((ProxyRule) rule);
		}
		update();
	}

	private void displayForwardingRuleDetails(final ForwardingRule rule) {
		Display.getCurrent().asyncExec(new Runnable() {
			public void run() {
				labelTitle.setText("Forwarding Rule Description");				
				labelTargetHost.setText("Target Host:   " + rule.getTargetHost());
				targetHost = rule.getTargetHost();
				hostCharacters = new char[targetHost.length()];
				labelTargetPort.setText("Target Port:   " + rule.getTargetPort());
				labelListenPort.setText("Listen Port:   " + rule.getKey().getPort());
				labelMethod.setText("Method:   " + rule.getKey().getMethod());
				labelPath.setText("Path:   " + rule.getKey().getPath());				
				labelHost.setText("Host:   " + rule.getKey().getHost());
			}
		});
	}

	private void displayProxyRuleDetails(final ProxyRule rule) {
		Display.getCurrent().asyncExec(new Runnable() {
			public void run() {
				labelTitle.setText("Proxy Rule Description");
				labelTargetHost.setText("");
				targetHost = "";
				labelTargetPort.setText("");
				labelListenPort.setText("Listen Port:   " + rule.getKey().getPort());
				labelMethod.setText("");
				labelPath.setText("");
				labelHost.setText("");
			}
		});
	}

	private void reset() {
		Display.getCurrent().asyncExec(new Runnable() {
			public void run() {
				labelTargetHost.setText("");
				targetHost = "";
				hostCharacters = new char[targetHost.length()];
				labelTargetPort.setText("");
				labelListenPort.setText("");
				labelMethod.setText("");
				labelPath.setText("");
				labelHost.setText("");
			}
		});
	}
}
