// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.scripting.importing

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionContributor
import org.jetbrains.kotlin.idea.core.script.ScriptModel
import org.jetbrains.kotlin.idea.core.script.configureGradleScriptsK2
import org.jetbrains.kotlin.idea.gradleJava.loadGradleDefinitions
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptDefinitionsContributor
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptDefinitionsSource
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import java.util.*

class KotlinDslSyncListener(val coroutineScope: CoroutineScope) : ExternalSystemTaskNotificationListener {
    companion object {
        val instance: KotlinDslSyncListener?
            get() =
                ExternalSystemTaskNotificationListener.EP_NAME.findExtension(KotlinDslSyncListener::class.java)
    }

    internal val tasks = WeakHashMap<ExternalSystemTaskId, KotlinDslGradleBuildSync>()

    override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        if (!id.isGradleRelatedTask()) return

        if (workingDir == null) return
        val task = KotlinDslGradleBuildSync(workingDir, id)
        synchronized(tasks) { tasks[id] = task }

        // project may be null in case of new project
        val project = id.findProject() ?: return
        task.project = project
        GradleBuildRootsManager.getInstance(project)?.markImportingInProgress(workingDir)
    }

    override fun onEnd(id: ExternalSystemTaskId) {
        if (!id.isGradleRelatedTask()) return

        val sync = synchronized(tasks) { tasks.remove(id) } ?: return

        // project may be null in case of new project
        val project = id.findProject() ?: return

        if (sync.gradleHome == null) {
            sync.gradleHome = GradleInstallationManager.getInstance()
                .getGradleHome(project, sync.workingDir)
                ?.path
        }

        if (sync.javaHome == null) {
            sync.javaHome = ExternalSystemApiUtil
                .getExecutionSettings<GradleExecutionSettings>(project, sync.workingDir, GradleConstants.SYSTEM_ID)
                .javaHome
        }

        sync.javaHome = sync.javaHome?.takeIf { JdkUtil.checkForJdk(it) }
            ?: run {
                // roll back to specified in GRADLE_JVM if for some reason sync.javaHome points to corrupted SDK
                val gradleJvm = GradleSettings.getInstance(project).getLinkedProjectSettings(sync.workingDir)?.gradleJvm
                try {
                    ExternalSystemJdkUtil.getJdk(project, gradleJvm)?.homePath
                } catch (e: Exception) {
                    null
                }
            }

        if (KotlinPluginModeProvider.isK2Mode()) {
            val definitions = loadGradleDefinitions(sync.workingDir, sync.gradleHome, sync.javaHome, project)
            GradleScriptDefinitionsSource.getInstance(project)?.updateDefinitions(definitions)

            val scripts = sync.models.mapNotNull {
                val path = Path.of(it.file)
                VirtualFileManager.getInstance().findFileByNioPath(path)?.let { virtualFile ->
                    ScriptModel(virtualFile, it.classPath, it.sourcePath, it.imports)
                }
            }.toSet()
            coroutineScope.launch { configureGradleScriptsK2(sync.javaHome, project, scripts) }
        } else {
            @Suppress("DEPRECATION")
            ScriptDefinitionContributor.find<GradleScriptDefinitionsContributor>(project)?.reloadIfNeeded(
                sync.workingDir, sync.gradleHome, sync.javaHome
            )
        }

        saveScriptModels(project, sync)
    }

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        if (!id.isGradleRelatedTask()) return

        val sync = synchronized(tasks) { tasks[id] } ?: return
        sync.failed = true
    }

    override fun onCancel(id: ExternalSystemTaskId) {
        if (!id.isGradleRelatedTask()) return

        val sync = synchronized(tasks) { tasks[id] } ?: return

        // project may be null in case of new project
        val project = id.findProject() ?: return

        GradleBuildRootsManager.getInstance(project)?.markImportingInProgress(sync.workingDir, false)

        if (sync.failed) {
            reportErrors(project, sync)
        }
    }

    private fun ExternalSystemTaskId.isGradleRelatedTask() = projectSystemId == GradleConstants.SYSTEM_ID &&
            (type == RESOLVE_PROJECT /*|| type == EXECUTE_TASK*/)
}
