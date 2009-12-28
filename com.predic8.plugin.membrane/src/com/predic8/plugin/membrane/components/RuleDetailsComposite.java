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

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;

public class RuleDetailsComposite extends Composite {

	Label labelTitle;
	
	Label labelTargetHost;
	Label labelTargetPort;

	Label labelMethod;
	Label labelListenPort;
	Label labelPath;
	Label labelHost;

	Group ruleOptionsRuleKeyGroup;
	Group ruleOptionsTargetGroup;

	private String targetHost = "";

	private int consumerImageWidth, consumerImageHeight;
	private int membraneImageWidth, membraneImageHeight;
	private int serviceImageWidth, serviceImageHeight;
	private int internetImageWidth, internetImageHeight;
	
	private int totalImageWidth; 
	
	private char[] hostCharacters = new char[0];
	
	private int hostCharactersLength;
	
	public RuleDetailsComposite(Composite parent) {
		super(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		setLayout(gridLayout);

		Composite compositeText = new Composite(this, SWT.NONE);
		compositeText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		GridLayout gridLayoutText = new GridLayout();
		gridLayoutText.numColumns = 1;
		compositeText.setLayout(gridLayoutText);

		Label labelDummy = new Label(compositeText, SWT.NONE);
		labelDummy.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		labelTitle = new Label(compositeText, SWT.NONE);
		labelTitle.setText("Rule Description");
		labelTitle.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		Label labelSeparator = new Label(compositeText, SWT.SEPARATOR | SWT.HORIZONTAL);
		GridData separatorGridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		separatorGridData.widthHint = 150;
		labelSeparator.setLayoutData(separatorGridData);

		Label labelDummy1 = new Label(compositeText, SWT.NONE);
		labelDummy1.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		
		Composite groupComposite = new Composite(compositeText, SWT.NONE);
		GridData groupCompositeGridData = new GridData(GridData.FILL_HORIZONTAL);
		groupComposite.setLayoutData(groupCompositeGridData);
		
		
		GridLayout gridLayoutGroup = new GridLayout();
		gridLayoutGroup.numColumns = 2;
		groupComposite.setLayout(gridLayoutGroup);
		
		
		ruleOptionsRuleKeyGroup = new Group(groupComposite, SWT.NONE);
		ruleOptionsRuleKeyGroup.setText("Rule Key");
		ruleOptionsRuleKeyGroup.setLayoutData(new GridData( GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		ruleOptionsRuleKeyGroup.setLayout(new RowLayout(SWT.VERTICAL));

		labelHost = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		labelListenPort = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		labelMethod = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		labelPath = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);

		ruleOptionsTargetGroup = new Group(groupComposite, SWT.NONE);
		ruleOptionsTargetGroup.setText("Target");
		GridData optTargetGroupGridData = new GridData(GridData.FILL_HORIZONTAL);
		optTargetGroupGridData.verticalAlignment = GridData.VERTICAL_ALIGN_BEGINNING;
		optTargetGroupGridData.heightHint = 68;
		
		ruleOptionsTargetGroup.setLayoutData(optTargetGroupGridData);
		ruleOptionsTargetGroup.setLayout(new RowLayout(SWT.VERTICAL));

		labelTargetHost = new Label(ruleOptionsTargetGroup, SWT.NONE);
		labelTargetPort = new Label(ruleOptionsTargetGroup, SWT.NONE);
		
		
		final Composite compositeCanvas = new Composite(this, SWT.NONE);
		compositeCanvas.setLayoutData(new GridData(GridData.FILL_BOTH));

		final FillLayout fillLayout = new FillLayout(SWT.VERTICAL);
		compositeCanvas.setLayout(fillLayout);

		ImageDescriptor descriptorInternet1 = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_INTERNET);
		final Image imageInternet1 = descriptorInternet1.createImage();

		ImageDescriptor descriptorInternet2 = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_INTERNET);
		final Image imageInternet2 = descriptorInternet2.createImage();

		ImageDescriptor descriptorMembrane = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_MEMBRANE);
		final Image imageMembrane = descriptorMembrane.createImage();

		ImageDescriptor descriptorService = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_SERVICE);
		final Image imageService = descriptorService.createImage();

		ImageDescriptor descriptorConsumer = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_CONSUMER);
		final Image imageConsumer = descriptorConsumer.createImage();

		consumerImageWidth = imageConsumer.getImageData().width;
		consumerImageHeight = imageConsumer.getImageData().height;
		membraneImageWidth = imageMembrane.getImageData().width;
		membraneImageHeight = imageMembrane.getImageData().height;
		serviceImageWidth = imageService.getImageData().width;
		serviceImageHeight = imageService.getImageData().height;
		internetImageWidth = imageInternet1.getImageData().width;
		internetImageHeight = imageInternet1.getImageData().height;
		
		totalImageWidth = consumerImageWidth + membraneImageWidth + 2 * internetImageWidth + serviceImageWidth + 16;
		
		Canvas canvas = new Canvas(compositeCanvas, SWT.DOUBLE_BUFFERED);
		canvas.addPaintListener(new PaintListener() {
			public void paintControl(final PaintEvent event) {
				int startX = compositeCanvas.getSize().x / 2 - totalImageWidth / 2;
				event.gc.drawImage(imageConsumer, 0, 0, consumerImageWidth, consumerImageHeight, startX, compositeCanvas.getSize().y / 4 - 50, consumerImageWidth, consumerImageHeight);
				event.gc.drawImage(imageInternet1, 0, 0, internetImageWidth, internetImageHeight, startX + 100, compositeCanvas.getSize().y / 4, internetImageWidth, internetImageHeight);
				event.gc.drawImage(imageMembrane, 0, 0, membraneImageWidth, membraneImageHeight, startX + 158, compositeCanvas.getSize().y / 4 - 50, membraneImageWidth, membraneImageHeight);
				event.gc.drawString(labelListenPort.getText(), startX + 167, compositeCanvas.getSize().y / 4 + imageMembrane.getImageData().height / 2 , true);
				event.gc.drawString(labelMethod.getText(), startX + 167, compositeCanvas.getSize().y / 4 + imageMembrane.getImageData().height /2 + 15 , true);
				event.gc.drawImage(imageInternet2, 0, 0, internetImageWidth, internetImageHeight, startX + 264, compositeCanvas.getSize().y / 4, internetImageWidth, internetImageHeight);
				event.gc.drawImage(imageService, 0, 0, serviceImageWidth, serviceImageHeight, startX + 326, compositeCanvas.getSize().y / 4 - 50, serviceImageWidth, serviceImageHeight);
				
				
				hostCharactersLength = 0;
				for (char c : hostCharacters) {
					hostCharactersLength += event.gc.getCharWidth(c); 
				}
				event.gc.drawString(targetHost, startX + 372 - hostCharactersLength/2, compositeCanvas.getSize().y / 4 + 10, true);
				event.gc.drawString(labelTargetPort.getText(), startX + 336,compositeCanvas.getSize().y / 4 + 105, true);
			}
		});

		redraw();
		pack();
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

		layout();
		ruleOptionsRuleKeyGroup.layout();
		ruleOptionsTargetGroup.layout();
		this.redraw();
	}

	private void displayForwardingRuleDetails(ForwardingRule rule) {
		labelTitle.setText("Forwarding Rule Description");
		labelTargetHost.setText("Target Host:   " + rule.getTargetHost());
		targetHost = rule.getTargetHost();
		
		hostCharacters = new char[targetHost.length()];
		
		labelTargetPort.setText("Target Port:   " + rule.getTargetPort());
		labelListenPort.setText("Listen Port:   " + rule.getRuleKey().getPort());
		labelMethod.setText("Method:   " + rule.getRuleKey().getMethod());
		labelPath.setText("Path:   " + rule.getRuleKey().getPath());
		labelHost.setText("Host:   " + rule.getRuleKey().getHost());
	}

	private void displayProxyRuleDetails(ProxyRule rule) {
		labelTitle.setText("Proxy Rule Description");
		labelTargetHost.setText("");
		targetHost = "";
		labelTargetPort.setText("");
		labelListenPort.setText("Listen Port:   " + rule.getRuleKey().getPort());
		labelMethod.setText("");
		labelPath.setText("");
		labelHost.setText("");
	}

	private void reset() {
		labelTargetHost.setText("");
		targetHost = "";
		
		hostCharacters = new char[targetHost.length()];
		
		labelTargetPort.setText("");
		labelListenPort.setText("");
		labelMethod.setText("" );
		labelPath.setText("" );
		labelHost.setText("" );
		layout();
		ruleOptionsRuleKeyGroup.layout();
		ruleOptionsTargetGroup.layout();
		this.redraw();
	}
}
