/*******************************************************************************
 * Copyright (c) 2000-2010 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 *******************************************************************************/

package com.liferay.ide.eclipse.project.core;

import com.liferay.ide.eclipse.core.CorePlugin;

import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 * 
 * @author Greg Amerson
 */
public class ProjectCorePlugin extends CorePlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.liferay.ide.eclipse.project.core";

	// The shared instance
	private static ProjectCorePlugin plugin;

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static ProjectCorePlugin getDefault() {
		return plugin;
	}

	/**
	 * The constructor
	 */
	public ProjectCorePlugin() {
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext context)
		throws Exception {
		
		super.start(context);
		
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext
	 * )
	 */
	public void stop(BundleContext context)
		throws Exception {
		
		plugin = null;
		
		super.stop(context);
	}

	public static void logError(String msg, Exception e) {
		getDefault().getLog().log(createErrorStatus(msg, e));
	}

}
