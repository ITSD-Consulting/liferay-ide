/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
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
 */

package com.liferay.ide.gradle.core;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;

import com.liferay.blade.gradle.tooling.ProjectInfo;
import com.liferay.ide.core.util.CoreUtil;
import com.liferay.ide.core.workspace.LiferayWorkspaceUtil;
import com.liferay.ide.server.core.portal.docker.IDockerServer;
import com.liferay.ide.server.core.portal.docker.PortalDockerRuntime;
import com.liferay.ide.server.core.portal.docker.PortalDockerServer;
import com.liferay.ide.server.util.LiferayDockerClient;
import com.liferay.ide.server.util.ServerUtil;

import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.IRuntimeType;
import org.eclipse.wst.server.core.IRuntimeWorkingCopy;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.IServerType;
import org.eclipse.wst.server.core.IServerWorkingCopy;
import org.eclipse.wst.server.core.ServerCore;

/**
 * @author Simon Jiang
 * @author Ethan Sun
 */
public class LiferayGradleDockerServer implements IDockerServer {

	public LiferayGradleDockerServer() {
	}

	@Override
	public boolean canPublishModule(IServer server, IModule module) {
		IProject project = module.getProject();

		boolean inLiferayWorkspace = LiferayWorkspaceUtil.inLiferayWorkspace(project);

		boolean gradleProject = GradleUtil.isGradleProject(project);

		if (inLiferayWorkspace && gradleProject) {
			return true;
		}

		return false;
	}

	@Override
	public void createDockerContainer(IProgressMonitor monitor) {
		IProject workspaceProject = LiferayWorkspaceUtil.getWorkspaceProject();

		if (Objects.isNull(workspaceProject)) {
			LiferayGradleCore.logError("Can not find valid liferay workspace project.");
		}

		try {
			GradleUtil.runGradleTask(workspaceProject, new String[] {"createDockerContainer"}, monitor);
		}
		catch (CoreException exception) {
			LiferayGradleCore.logError(
				"Failed to create liferay docker container for project " + workspaceProject.getName(), exception);
		}
	}

	@Override
	public void removeDockerContainer(IProgressMonitor monitor) {
		IProject workspaceProject = LiferayWorkspaceUtil.getWorkspaceProject();

		if (Objects.isNull(workspaceProject)) {
			LiferayGradleCore.logError("Can not find valid liferay workspace project.");
		}

		try {
			GradleUtil.runGradleTask(workspaceProject, new String[] {"removeDockerContainer"}, monitor);
		}
		catch (CoreException exception) {
			LiferayGradleCore.logError(
				"Failed to remove liferay docker container for project " + workspaceProject.getName(), exception);
		}
	}

	@Override
	public void removeDockerImage(IProgressMonitor monitor) {
		IProject workspaceProject = LiferayWorkspaceUtil.getWorkspaceProject();

		if (Objects.isNull(workspaceProject)) {
			LiferayGradleCore.logError("Can not find valid liferay workspace project.");
		}

		try {
			GradleUtil.runGradleTask(
				workspaceProject, new String[] {"cleanDockerImage"}, new String[] {"-x", "removeDockerContainer"},
				false, monitor);
		}
		catch (CoreException exception) {
			LiferayGradleCore.logError(
				"Failed to remove liferay docker image for project " + workspaceProject.getName(), exception);
		}
	}

	@Override
	public void startDockerContainer(IProgressMonitor monitor) {
		try {
			IProject workspaceProject = LiferayWorkspaceUtil.getWorkspaceProject();

			if (Objects.isNull(workspaceProject)) {
				LiferayGradleCore.logError("Can not find valid liferay workspace project.");
			}

			String[] tasks = {"removeDockerContainer", "cleanDockerImage", "createDockerContainer"};

			monitor.beginTask("startDockerContainer", 100);

			monitor.worked(20);

			GradleUtil.runGradleTask(workspaceProject, tasks, monitor);

			monitor.worked(40);

			ProjectInfo projectInfo = LiferayGradleCore.getToolingModel(ProjectInfo.class, workspaceProject);

			if ((projectInfo != null) && CoreUtil.isNotNullOrEmpty(projectInfo.getDockerImageId()) &&
				CoreUtil.isNotNullOrEmpty(projectInfo.getDockerContainerId())) {

				Image dockerImage = LiferayDockerClient.getDockerImageByName(projectInfo.getDockerImageId());

				if (Objects.isNull(dockerImage)) {
					return;
				}

				PortalDockerRuntime dockerRuntime = Stream.of(
					ServerCore.getRuntimes()
				).filter(
					runtime -> Objects.nonNull(runtime)
				).map(
					runtime -> (PortalDockerRuntime)runtime.loadAdapter(PortalDockerRuntime.class, monitor)
				).filter(
					runtime -> Objects.nonNull(runtime)
				).filter(
					runtime -> Objects.equals(
						projectInfo.getDockerImageId(), String.join(":", runtime.getImageRepo(), runtime.getImageTag()))
				).findAny(
				).orElseGet(
					null
				);

				if (Objects.nonNull(dockerRuntime) &&
					Objects.equals(
						projectInfo.getDockerImageId(),
						String.join(":", dockerRuntime.getImageRepo(), dockerRuntime.getImageTag()))) {

					IRuntimeType portalRuntimeType = ServerCore.findRuntimeType(PortalDockerRuntime.ID);

					IRuntimeWorkingCopy runtimeWC = portalRuntimeType.createRuntime(portalRuntimeType.getName(), null);

					ServerUtil.setRuntimeName(runtimeWC, -1, workspaceProject.getName());

					PortalDockerRuntime portalDockerRuntime = (PortalDockerRuntime)runtimeWC.loadAdapter(
						PortalDockerRuntime.class, null);

					String dockerImageId = projectInfo.getDockerImageId();

					portalDockerRuntime.setImageRepo(dockerImageId.split(":")[0]);

					portalDockerRuntime.setImageId(dockerImage.getId());

					portalDockerRuntime.setImageTag(dockerImageId.split(":")[1]);

					runtimeWC.save(true, null);

					Container dockerContainer = LiferayDockerClient.getDockerContainerByName(
						projectInfo.getDockerContainerId());

					if (Objects.isNull(dockerContainer)) {
						return;
					}

					IServerType serverType = ServerCore.findServerType(PortalDockerServer.ID);

					IServerWorkingCopy serverWC = serverType.createServer(serverType.getName(), null, runtimeWC, null);

					serverWC.setName(serverType.getName() + " " + workspaceProject.getName());

					Container container = LiferayDockerClient.getDockerContainerByName(
						projectInfo.getDockerContainerId());

					PortalDockerServer portalDockerServer = (PortalDockerServer)serverWC.loadAdapter(
						PortalDockerServer.class, null);

					portalDockerServer.setContainerName(projectInfo.getDockerContainerId());

					portalDockerServer.setContainerId(container.getId());

					serverWC.save(true, null);

					monitor.worked(60);
				}

				GradleUtil.runGradleTask(
					workspaceProject, new String[] {"startDockerContainer"},
					new String[] {"-x", "createDockerContainer"}, false, monitor);
			}
		}
		catch (Exception ce) {
			LiferayGradleCore.logError(ce);
		}
	}

	@Override
	public void stopDockerContainer(IProgressMonitor monitor) {
		IProject workspaceProject = LiferayWorkspaceUtil.getWorkspaceProject();

		if (Objects.isNull(workspaceProject)) {
			LiferayGradleCore.logError("Can not find valid liferay workspace project.");
		}

		try {
			GradleUtil.runGradleTask(
				LiferayWorkspaceUtil.getWorkspaceProject(), new String[] {"stopDockerContainer"}, monitor);
		}
		catch (CoreException exception) {
			LiferayGradleCore.logError(
				"Failed to stop liferay docker container for project " + workspaceProject.getName(), exception);
		}
	}

}