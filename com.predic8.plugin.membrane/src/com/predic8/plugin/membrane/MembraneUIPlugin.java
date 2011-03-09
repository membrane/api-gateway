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

package com.predic8.plugin.membrane;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.predic8.plugin.membrane.resources.ImageKeys;

/**
 * The activator class controls the plug-in life cycle
 */
public class MembraneUIPlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.predic8.plugin.membrane";
	
	// The shared instance
	private static MembraneUIPlugin plugin;
	
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static MembraneUIPlugin getDefault() {
		return plugin;
	}

	public static ImageDescriptor getImageDescriptor(String imageFilePath) {
		return AbstractUIPlugin.imageDescriptorFromPlugin(PLUGIN_ID, imageFilePath);
	}
	
	@Override
	protected void initializeImageRegistry(ImageRegistry reg) {
		reg.put(ImageKeys.IMAGE_RULE, imageDescriptorFromPlugin(PLUGIN_ID, "icons/rule_icon.png"));
		
		reg.put(ImageKeys.IMAGE_RULE_PROXY, imageDescriptorFromPlugin(PLUGIN_ID, "icons/rule_reverse_proxy.png"));
		reg.put(ImageKeys.IMAGE_RULE_REVERSE_PROXY, imageDescriptorFromPlugin(PLUGIN_ID, "icons/rule_proxy.png"));
		
		reg.put(ImageKeys.IMAGE_PENDING, imageDescriptorFromPlugin(PLUGIN_ID, "icons/rule_pending.png"));
		reg.put(ImageKeys.IMAGE_FAILED, imageDescriptorFromPlugin(PLUGIN_ID, "icons/rule_failed.png"));
	
		reg.put(ImageKeys.IMAGE_COMPLETED, imageDescriptorFromPlugin(PLUGIN_ID, "icons/rule_completed.png"));
		reg.put(ImageKeys.IMAGE_BUG, imageDescriptorFromPlugin(PLUGIN_ID, "icons/bug.png"));
		reg.put(ImageKeys.IMAGE_ARROW_UNDO, imageDescriptorFromPlugin(PLUGIN_ID, "icons/arrow_undo.png"));
		reg.put(ImageKeys.IMAGE_ARROW_REDO, imageDescriptorFromPlugin(PLUGIN_ID, "icons/arrow_redo.png"));
		reg.put(ImageKeys.IMAGE_THUMB_DOWN, imageDescriptorFromPlugin(PLUGIN_ID, "icons/thumb_down.png"));
		reg.put(ImageKeys.IMAGE_STOP_ENABLED, imageDescriptorFromPlugin(PLUGIN_ID, "icons/stop_enabled.png"));
		reg.put(ImageKeys.IMAGE_STOP_DISABLED, imageDescriptorFromPlugin(PLUGIN_ID, "icons/stop_disabled.png"));
	
		reg.put(ImageKeys.IMAGE_INTERNET, imageDescriptorFromPlugin(PLUGIN_ID, "icons/internet.png"));
		reg.put(ImageKeys.IMAGE_SERVICE, imageDescriptorFromPlugin(PLUGIN_ID, "icons/service.png"));
		reg.put(ImageKeys.IMAGE_CONSUMER, imageDescriptorFromPlugin(PLUGIN_ID, "icons/consumer.png"));
		reg.put(ImageKeys.IMAGE_MEMBRANE, imageDescriptorFromPlugin(PLUGIN_ID, "icons/membrane.png"));
	
		reg.put(ImageKeys.IMAGE_FORMAT, imageDescriptorFromPlugin(PLUGIN_ID, "icons/format.png"));
		reg.put(ImageKeys.IMAGE_FLAG_GREEN, imageDescriptorFromPlugin(PLUGIN_ID, "icons/flag_green.png"));
	
		reg.put(ImageKeys.IMAGE_SAVE_MESSAGE, imageDescriptorFromPlugin(PLUGIN_ID, "icons/script_save.png"));
		
		reg.put(ImageKeys.IMAGE_ADD_RULE, imageDescriptorFromPlugin(PLUGIN_ID, "icons/add_rule.png"));
		reg.put(ImageKeys.IMAGE_WORLD_GO, imageDescriptorFromPlugin(PLUGIN_ID, "icons/world_go.png"));
		
		reg.put(ImageKeys.IMAGE_ARROW_ROTATE_CLOCKWISE, imageDescriptorFromPlugin(PLUGIN_ID, "icons/arrow_rotate_clockwise.png"));
		reg.put(ImageKeys.IMAGE_ARROW_ROTATE_COUNTER_CLOCKWISE, imageDescriptorFromPlugin(PLUGIN_ID, "icons/arrow_rotate_anticlockwise.png"));
	
		reg.put(ImageKeys.IMAGE_STOP_OPERATION, imageDescriptorFromPlugin(PLUGIN_ID, "icons/stop.png"));
	
		reg.put(ImageKeys.IMAGE_ARROW_REFRESH, imageDescriptorFromPlugin(PLUGIN_ID, "icons/arrow_refresh.png"));
	
		reg.put(ImageKeys.IMAGE_DOOR_IN, imageDescriptorFromPlugin(PLUGIN_ID, "icons/door_in.png"));
		reg.put(ImageKeys.IMAGE_FILTER, imageDescriptorFromPlugin(PLUGIN_ID, "icons/filter.gif"));
		reg.put(ImageKeys.IMAGE_SORTER, imageDescriptorFromPlugin(PLUGIN_ID, "icons/sort.gif"));
		
		reg.put(ImageKeys.IMAGE_FOLDER, imageDescriptorFromPlugin(PLUGIN_ID, "icons/folder.png"));
		
		reg.put(ImageKeys.IMAGE_DELETE_EXCHANGE, imageDescriptorFromPlugin(PLUGIN_ID, "icons/delete_exchange.png"));
	}
	
}
