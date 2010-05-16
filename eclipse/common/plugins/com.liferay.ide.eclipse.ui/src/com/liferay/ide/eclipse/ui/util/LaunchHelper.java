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

package com.liferay.ide.eclipse.ui.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jdt.internal.debug.ui.classpath.ClasspathEntry;
import org.eclipse.jdt.internal.debug.ui.classpath.ClasspathModel;
import org.eclipse.jdt.internal.debug.ui.classpath.IClasspathEntry;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;

/**
 * @author Greg Amerson
 */
@SuppressWarnings( {
	"restriction", "unchecked"
})
public abstract class LaunchHelper implements IDebugEventSetListener {

	protected String[] launchArgs = new String[0];

	protected boolean launchCaptureInConsole = true;
	
	protected String launchConfigTypeId;

	protected boolean launchInBackground = true;

	protected boolean launchIsPrivate = true;

	protected boolean launchSync = true;

	protected String mainClass;

	protected String mode = ILaunchManager.RUN_MODE;

	protected ILaunch runningLaunch;


	public LaunchHelper(String launchConfigTypeId) {
		this.launchConfigTypeId = launchConfigTypeId;
	}

	public ILaunchConfigurationWorkingCopy createLaunchConfiguration()
		throws CoreException {

		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();

		ILaunchConfigurationType type = manager.getLaunchConfigurationType(this.launchConfigTypeId);

		String name =
			DebugPlugin.getDefault().getLaunchManager().generateUniqueLaunchConfigurationNameFrom(
				getNewLaunchConfigurationName());

		ILaunchConfigurationWorkingCopy launchConfig = type.newInstance(null, name);

		launchConfig.setAttribute(IDebugUIConstants.ATTR_LAUNCH_IN_BACKGROUND, isLaunchInBackground());
		launchConfig.setAttribute(IDebugUIConstants.ATTR_CAPTURE_IN_CONSOLE, isLaunchCaptureInConsole());
		launchConfig.setAttribute(IDebugUIConstants.ATTR_PRIVATE, isLaunchIsPrivate());

		IRuntimeClasspathEntry[] classpath = getClasspath(launchConfig);
		
		List mementos = new ArrayList(classpath.length);

		for (int i = 0; i < classpath.length; i++) {
			IRuntimeClasspathEntry entry = classpath[i];

			mementos.add(entry.getMemento());
		}

		launchConfig.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, false);
		launchConfig.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH, mementos);

		if (mainClass != null) {
			launchConfig.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, mainClass);
		}

		if (launchArgs != null && launchArgs.length > 0) {
			StringBuilder sb = new StringBuilder();

			for (int i = 0; i < launchArgs.length; i++) {
				sb.append("\"" + launchArgs[i] + "\" ");
			}

			launchConfig.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, sb.toString());
		}

		return launchConfig;
	}

	public String[] getLaunchArgs() {
		return launchArgs;
	}

	public String getMainClass() {
		return mainClass;
	}

	public String getMode() {
		return mode;
	}

	public void handleDebugEvents(DebugEvent[] events) {
		for (DebugEvent event : events) {
			if (event.getSource() instanceof IProcess) {
				if (((IProcess) event.getSource()).getLaunch().equals(this.runningLaunch) &&
					event.getKind() == DebugEvent.TERMINATE) {

					synchronized (this) {
						DebugPlugin.getDefault().removeDebugEventListener(this);

						this.notifyAll();
					}
				}
			}
		}
	}

	public boolean isLaunchCaptureInConsole() {
		return launchCaptureInConsole;
	}

	public boolean isLaunchInBackground() {
		return launchInBackground;
	}

	public boolean isLaunchIsPrivate() {
		return launchIsPrivate;
	}

	public boolean isLaunchRunning() {
		return this.runningLaunch != null && !this.runningLaunch.isTerminated();
	}

	public boolean isLaunchSync() {
		return this.launchSync;
	}

	public void launch(ILaunchConfiguration config, String mode, IProgressMonitor monitor)
		throws CoreException {

		if (config == null) {
			throw new IllegalArgumentException("Launch config cannot be null");
		}

		if (isLaunchSync()) {
			DebugPlugin.getDefault().addDebugEventListener(this);
		}

		ILaunch launch = config.launch(mode, monitor);

		if (isLaunchSync()) {
			runningLaunch = launch;

			try {
				synchronized (this) {
					this.wait();
				}
			}
			catch (InterruptedException e) {
				runningLaunch.terminate();
			}
			finally {
				runningLaunch = null;
			}
		}
	}

	public void launch(IProgressMonitor monitor)
		throws CoreException {

		ILaunchConfigurationWorkingCopy config = createLaunchConfiguration();

		launch(config, mode, monitor);
	}

	public void setLaunchArgs(String[] launchArgs) {
		this.launchArgs = launchArgs;
	}

	public void setLaunchCaptureInConsole(boolean launchCaptureInConsole) {
		this.launchCaptureInConsole = launchCaptureInConsole;
	}

	public void setLaunchInBackground(boolean launchInBackground) {
		this.launchInBackground = launchInBackground;
	}

	public void setLaunchIsPrivate(boolean launchIsPrivate) {
		this.launchIsPrivate = launchIsPrivate;
	}

	public void setLaunchSync(boolean sync) {
		this.launchSync = sync;
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	protected abstract void addUserEntries(ClasspathModel model)
		throws CoreException;

	protected IRuntimeClasspathEntry[] getClasspath(ILaunchConfigurationWorkingCopy config)
		throws CoreException {

		ClasspathModel model = new ClasspathModel();

		config.setAttribute(
			IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER, getClasspathProviderAttributeValue());

		config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, true);

		IRuntimeClasspathEntry[] defaultEntries = JavaRuntime.computeUnresolvedRuntimeClasspath(config);

		IRuntimeClasspathEntry entry;

		for (int i = 0; i < defaultEntries.length; i++) {
			entry = defaultEntries[i];

			switch (entry.getClasspathProperty()) {

			case IRuntimeClasspathEntry.USER_CLASSES:
				model.addEntry(ClasspathModel.USER, entry);

				break;

			default:
				model.addEntry(ClasspathModel.BOOTSTRAP, entry);

				break;

			}
		}

		addUserEntries(model);

		return getClasspathEntries(model);
	}

	protected IRuntimeClasspathEntry[] getClasspathEntries(ClasspathModel model) {
		IClasspathEntry[] boot = model.getEntries(ClasspathModel.BOOTSTRAP);

		IClasspathEntry[] user = model.getEntries(ClasspathModel.USER);

		List entries = new ArrayList(boot.length + user.length);

		IClasspathEntry bootEntry;

		IRuntimeClasspathEntry entry;

		for (int i = 0; i < boot.length; i++) {
			bootEntry = boot[i];

			entry = null;

			if (bootEntry instanceof ClasspathEntry) {
				entry = ((ClasspathEntry) bootEntry).getDelegate();
			}
			else if (bootEntry instanceof IRuntimeClasspathEntry) {
				entry = (IRuntimeClasspathEntry) boot[i];
			}

			if (entry != null) {
				if (entry.getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES) {
					entry.setClasspathProperty(IRuntimeClasspathEntry.BOOTSTRAP_CLASSES);
				}

				entries.add(entry);
			}
		}

		IClasspathEntry userEntry;

		for (int i = 0; i < user.length; i++) {
			userEntry = user[i];

			entry = null;

			if (userEntry instanceof ClasspathEntry) {
				entry = ((ClasspathEntry) userEntry).getDelegate();
			}
			else if (userEntry instanceof IRuntimeClasspathEntry) {
				entry = (IRuntimeClasspathEntry) user[i];
			}

			if (entry != null) {
				entry.setClasspathProperty(IRuntimeClasspathEntry.USER_CLASSES);

				entries.add(entry);
			}
		}

		return (IRuntimeClasspathEntry[]) entries.toArray(new IRuntimeClasspathEntry[entries.size()]);
	}

	protected String getClasspathProviderAttributeValue() {
		return null;
	}

	protected IVMInstall getDefaultVMInstall(ILaunchConfiguration config) {
		IVMInstall defaultVMInstall;

		try {
			defaultVMInstall = JavaRuntime.computeVMInstall(config);
		}
		catch (CoreException e) {
			// core exception thrown for non-Java project
			defaultVMInstall = JavaRuntime.getDefaultVMInstall();
		}

		return defaultVMInstall;
	}

	protected String getNewLaunchConfigurationName() {
		return this.getClass().getName();
	}

}
