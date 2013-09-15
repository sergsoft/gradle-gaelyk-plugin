/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.plugins.gaelyk

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.gae.GaePlugin
import org.gradle.api.plugins.gae.GaePluginConvention
import org.gradle.api.plugins.gae.task.GaeRunTask
import org.gradle.api.plugins.gaelyk.template.GaelykControllerCreator
import org.gradle.api.plugins.gaelyk.template.GaelykFileCreator
import org.gradle.api.plugins.gaelyk.template.GaelykViewCreator
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.plugins.*
import org.gradle.api.plugins.gaelyk.tasks.*
import static org.gradle.api.plugins.gae.GaePlugin.GAE_RUN
import static org.gradle.api.plugins.JavaPlugin.CLASSES_TASK_NAME
import static org.gradle.api.plugins.WarPlugin.WAR_TASK_NAME
import org.gradle.api.Task

/**
 * <p>A {@link org.gradle.api.Plugin} that provides tasks for managing Gaelyk projects.</p>
 *
 * @author Benjamin Muschko
 */
class GaelykPlugin implements Plugin<Project> {
    static final String GAELYK_GROUP = "Gaelyk"
    static final String GAELYK_INSTALL_PLUGIN = "gaelykInstallPlugin"
    static final String GAELYK_UNINSTALL_PLUGIN = "gaelykUninstallPlugin"
    static final String GAELYK_LIST_INSTALLED_PLUGINS = "gaelykListInstalledPlugins"
    static final String GAELYK_LIST_PLUGINS = "gaelykListPlugins"
    static final String GAELYK_CREATE_CONTROLLER = "gaelykCreateController"
    static final String GAELYK_CREATE_VIEW = "gaelykCreateView"
    static final String GAELYK_PRECOMPILE_TEMPLATE = "gaelykPrecompileTemplates"
    static final String GAELYK_CONVERT_TEMPLATES = "gaelykConvertTemplates"
    static final String GAELYK_COPY_RUNTIME_LIBRARIES = "gaelykCopyRuntimeLibraries"

    static final String GROOVLET_DIRECTORY_RELATIVE_PATH = 'WEB-INF/groovy'
    static final String OUTPUT_DIRECTORY_RELATIVE_PATH = 'WEB-INF/classes'
    static final String LIBRARIES_DIRECTORY_RELATIVE_PATH = 'WEB-INF/lib'
    static final String APPENGINE_GENERATED_RELATIVE_PATH = 'WEB-INF/appengine-generated'
    
    static final String COMPILE_GROOVY_TASK_NAME = "compileGroovy"
    
    // needs to be repeated because GAE plugin must be able to run even if fatjar is not installed
    // this is needed e.g. when building gaelyk binary plugins
    static final String FATJAR_PREPARE_FILES = "fatJarPrepareFiles"
    static final String FATJAR_FAT_JAR = "fatJar"
    static final String FATJAR_SLIM_WAR = "slimWar"

    @Override
    public void apply(Project project) {
        project.plugins.apply(GroovyPlugin)
        project.plugins.apply(GaePlugin)

        GaelykPluginConvention gaelykPluginConvention = new GaelykPluginConvention()
        project.convention.plugins.gaelyk = gaelykPluginConvention

        configureGaelykInstallPluginTask(project)
        configureGaelykUninstallPluginTask(project)
        configureGaelykListInstalledPluginsTask(project)
        configureGaelykListPluginsTask(project)
        configureGaelykCreateControllerTask(project)
        configureGaelykCreateViewTask(project)
        configureConvertTemplatesToScript(project, gaelykPluginConvention)
        configureGaelykPrecompileTemplate(project, gaelykPluginConvention)
        configureFatJarPlugin(project, gaelykPluginConvention)
        configureGaePlugin(project, gaelykPluginConvention)
        configureMainSourceSet(project, gaelykPluginConvention)
        configureCleanTask(project, gaelykPluginConvention)
        configureGaelykCopyRuntimeLibraries(project, gaelykPluginConvention)
        
        project.gradle.taskGraph.whenReady {
            if  (!gaeRunIsInGraph(project) || !gaelykPluginConvention.rad) {
                GroovyCompile groovyCompileTask = project.tasks.findByName(COMPILE_GROOVY_TASK_NAME)
                ConvertTemplatesToScriptsTask convertTask = project.tasks.findByName(GAELYK_CONVERT_TEMPLATES)
                groovyCompileTask.source(new File(getWarConvention(project).webAppDir, GROOVLET_DIRECTORY_RELATIVE_PATH))
            }
        }
    }

    private void configureGaelykInstallPluginTask(final Project project) {
        project.tasks.withType(GaelykInstallPluginTask.class).whenTaskAdded { GaelykInstallPluginTask gaelykInstallPluginTask ->
            gaelykInstallPluginTask.conventionMapping.map("plugin") { getPluginProperty(project) }
        }

        GaelykInstallPluginTask gaelykInstallPluginTask = project.tasks.create(GAELYK_INSTALL_PLUGIN, GaelykInstallPluginTask.class)
        gaelykInstallPluginTask.description = "Installs Gaelyk plugin."
        gaelykInstallPluginTask.group = GAELYK_GROUP
    }

    private void configureGaelykUninstallPluginTask(final Project project) {
        project.tasks.withType(GaelykUninstallPluginTask.class).whenTaskAdded { GaelykUninstallPluginTask gaelykUninstallPluginTask ->
            gaelykUninstallPluginTask.conventionMapping.map("plugin") { getPluginProperty(project) }
        }

        GaelykUninstallPluginTask gaelykUninstallPluginTask = project.tasks.create(GAELYK_UNINSTALL_PLUGIN, GaelykUninstallPluginTask)
        gaelykUninstallPluginTask.description = "Uninstalls Gaelyk plugin."
        gaelykUninstallPluginTask.group = GAELYK_GROUP
    }

    private void configureGaelykListInstalledPluginsTask(final Project project) {
        GaelykListInstalledPluginsTask gaelykListInstalledPluginsTask = project.tasks.create(GAELYK_LIST_INSTALLED_PLUGINS, GaelykListInstalledPluginsTask)
        gaelykListInstalledPluginsTask.description = "Lists installed Gaelyk plugins."
        gaelykListInstalledPluginsTask.group = GAELYK_GROUP
    }

    private void configureGaelykListPluginsTask(final Project project) {
        GaelykListPluginsTask gaelykListPluginsTask = project.tasks.create(GAELYK_LIST_PLUGINS, GaelykListPluginsTask)
        gaelykListPluginsTask.description = "Lists available Gaelyk plugins from catalogue."
        gaelykListPluginsTask.group = GAELYK_GROUP
    }

    private void configureGaelykPrecompileTemplate(Project project, GaelykPluginConvention pluginConvention) {
        GroovyCompile groovyCompileTask = project.tasks.findByName(COMPILE_GROOVY_TASK_NAME)
        ConvertTemplatesToScriptsTask convertTask = project.tasks.findByName(GAELYK_CONVERT_TEMPLATES)
        
        project.tasks.withType(GroovyCompile).whenTaskAdded { GroovyCompile gaelykPrecompileTask ->
            if (gaelykPrecompileTask.name != GAELYK_PRECOMPILE_TEMPLATE) return
            
            
            gaelykPrecompileTask.conventionMapping.map("classpath") { groovyCompileTask.getClasspath() }
            gaelykPrecompileTask.conventionMapping.map("groovyClasspath") { groovyCompileTask.getGroovyClasspath() }
            gaelykPrecompileTask.conventionMapping.map("groovyOptions") { groovyCompileTask.getGroovyOptions() }
            gaelykPrecompileTask.conventionMapping.map("sourceCompatibility") { groovyCompileTask.getSourceCompatibility() }
            gaelykPrecompileTask.conventionMapping.map("targetCompatibility") { groovyCompileTask.getTargetCompatibility() }
            gaelykPrecompileTask.conventionMapping.map("options") { groovyCompileTask.getOptions() }
            gaelykPrecompileTask.conventionMapping.map("destinationDir") { groovyCompileTask.getDestinationDir() }
            
            gaelykPrecompileTask.conventionMapping.map("source") { project.fileTree(convertTask.getDestinationDir()) }
        }

        def gaelykPrecompileTemplatesTask = project.tasks.create(GAELYK_PRECOMPILE_TEMPLATE, GroovyCompile)
        gaelykPrecompileTemplatesTask.description = "Precompiles groovy templates scripts generated by convert task."
        gaelykPrecompileTemplatesTask.group = GAELYK_GROUP
        
        gaelykPrecompileTemplatesTask.dependsOn convertTask
        project.tasks.findByName(WAR_TASK_NAME).dependsOn(gaelykPrecompileTemplatesTask)
        project.tasks.findByName(FATJAR_PREPARE_FILES)?.dependsOn(gaelykPrecompileTemplatesTask)
    }
    
    private void configureConvertTemplatesToScript(final Project project, GaelykPluginConvention pluginConvention) {
        GroovyCompile groovyCompileTask = project.tasks.findByName(COMPILE_GROOVY_TASK_NAME)

        project.tasks.withType(ConvertTemplatesToScriptsTask).whenTaskAdded { ConvertTemplatesToScriptsTask convertTemplateToScript ->
            
            convertTemplateToScript.conventionMapping.map("classpath") { createRuntimeClasspath(project, convertTemplateToScript.getTemplateExtension()) }
            convertTemplateToScript.conventionMapping.map("sourceCompatibility") { groovyCompileTask.getSourceCompatibility() }
            convertTemplateToScript.conventionMapping.map("targetCompatibility") { groovyCompileTask.getTargetCompatibility() }
            convertTemplateToScript.conventionMapping.map("destinationDir") { new File(project.buildDir.absolutePath + ConvertTemplatesToScriptsTask.PRECOMPILE_TEMPLATE_STAGE_DIR) }
            convertTemplateToScript.conventionMapping.map("templateExtension") { pluginConvention.getTemplateExtension() }
            convertTemplateToScript.conventionMapping.map("source") { 
                project.fileTree(getWarConvention(project).webAppDir).matching {
                    include "**/*." + convertTemplateToScript.getTemplateExtension()
                } 
            }
        }

        def convertTemplateToScript = project.tasks.create(GAELYK_CONVERT_TEMPLATES, ConvertTemplatesToScriptsTask)
        convertTemplateToScript.description = "Converts templates to scripts."
        convertTemplateToScript.group = GAELYK_GROUP
        convertTemplateToScript.onlyIf { !gaeRunIsInGraph(project) || !pluginConvention.rad }
        convertTemplateToScript.dependsOn groovyCompileTask
    }

    private void configureGaelykCreateControllerTask(final Project project) {
        project.tasks.addRule("Pattern: $GAELYK_CREATE_CONTROLLER<ControllerName>: Creates a Gaelyk controller (Groovlet).") { String taskName ->
            createGaelykFile(project, taskName, GAELYK_CREATE_CONTROLLER, new GaelykControllerCreator())
        }
    }

    private void configureGaelykCreateViewTask(final Project project) {
        project.tasks.addRule("Pattern: $GAELYK_CREATE_VIEW<ViewName>: Creates a Gaelyk view (Groovy template).") { String taskName ->
            createGaelykFile(project, taskName, GAELYK_CREATE_VIEW, new GaelykViewCreator())
        }
    }

    private void createGaelykFile(final Project project, final String taskName, final String taskBaseName, final GaelykFileCreator gaelykFileCreator) {
        if(taskName.startsWith(taskBaseName) && taskName.length() > taskBaseName.length()) {
            project.task(taskName) << {
                String viewName = (taskName - taskBaseName)
                String viewDir = getDirProperty(project)
                gaelykFileCreator.setWebAppDir(getWarConvention(project).webAppDir)
                gaelykFileCreator.create(viewDir, viewName)
            }
        }
    }

    private String getDirProperty(final Project project) {
        project.hasProperty("dir") ? project.property("dir") : null
    }

    private String getPluginProperty(final Project project) {
        project.hasProperty("plugin") ? project.property("plugin") : null
    }

    private WarPluginConvention getWarConvention(Project project) {
        project.convention.getPlugin(WarPluginConvention)
    }

    /**
     * Creates classpath from classes directory and runtime classpath.
     *
     * @return Classpath
     */
    private FileCollection createRuntimeClasspath(Project project, String templateExtension) {
        FileCollection runtimeClasspath = project.files(project.sourceSets.main.output.classesDir)
        runtimeClasspath += project.configurations.runtime
        runtimeClasspath.filter { File it ->
            !it.name.startsWith(ConvertTemplatesToScriptsTask.getPrefix(templateExtension))
        }
    }

    private void configureFatJarPlugin(Project project, GaelykPluginConvention pluginConvention) {
        [FATJAR_PREPARE_FILES, FATJAR_FAT_JAR, FATJAR_SLIM_WAR].each { taskName ->
            project.tasks.findByName(taskName)?.onlyIf { !gaeRunIsInGraph(project) || !pluginConvention.rad }
        }
    }

    private def gaeRunIsInGraph(Project project) {
        GaeRunTask gaeRunTask = project.tasks.findByName(GAE_RUN)
        project.gradle.taskGraph.hasTask(gaeRunTask)
    }

    private void configureGaePlugin(Project project, GaelykPluginConvention pluginConvention) {
        project.plugins.withType(GaePlugin) {
            GaePluginConvention gaePluginConvention = project.convention.getPlugin(GaePluginConvention)

            gaePluginConvention.with {
                downloadSdk = true
                optimizeWar = true
            }

            project.afterEvaluate {
                if (pluginConvention.rad) {
                    gaePluginConvention.warDir = getWarConvention(project).webAppDir
                } else {
                    gaePluginConvention.jvmFlags += '-Dgaelyk.preferPrecompiled=true'
                }
            }
        }
    }

    private File getMainOutputDirectoryForRad(Project project, GaelykPluginConvention pluginConvention) {
        WarPluginConvention warPluginConvention = getWarConvention(project)
        new File(warPluginConvention.webAppDir, OUTPUT_DIRECTORY_RELATIVE_PATH)
    }

    private SourceSet getMainSourceSet(Project project) {
        JavaPluginConvention javaPluginConvention = project.convention.getPlugin(JavaPluginConvention)
        SourceSet mainSourceSet = javaPluginConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        return mainSourceSet
    }

    private void configureMainSourceSet(Project project, GaelykPluginConvention pluginConvention) {
        project.afterEvaluate {
            if (pluginConvention.rad) {
                def mainSourceSetOutput = getMainSourceSet(project).output
                def mainOutputDirectoryForRad = getMainOutputDirectoryForRad(project, pluginConvention)
                mainSourceSetOutput.classesDir = mainOutputDirectoryForRad
                mainSourceSetOutput.resourcesDir = mainOutputDirectoryForRad
            }
        }
    }

    private void configureCleanTask(Project project, GaelykPluginConvention pluginConvention) {
        project.afterEvaluate {
            if (pluginConvention.rad) {
                Delete task = project.tasks.findByName(BasePlugin.CLEAN_TASK_NAME)
                [OUTPUT_DIRECTORY_RELATIVE_PATH, LIBRARIES_DIRECTORY_RELATIVE_PATH, APPENGINE_GENERATED_RELATIVE_PATH].each {
                    task.delete(new File(getWarConvention(project).webAppDir, it))
                }
            }
        }
    }

    private void configureGaelykCopyRuntimeLibraries(Project project, GaelykPluginConvention pluginConvention) {
        Sync gaelykCopyRuntimeLibraries = project.tasks.create(GAELYK_COPY_RUNTIME_LIBRARIES, Sync)
        gaelykCopyRuntimeLibraries.description = "Synchronises runtime libraries in webapp directory."
        gaelykCopyRuntimeLibraries.group = GAELYK_GROUP
        gaelykCopyRuntimeLibraries.from project.configurations.findByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
        gaelykCopyRuntimeLibraries.onlyIf { pluginConvention.rad }

        project.plugins.withType(GaePlugin) {
            project.tasks.findByName(GaePlugin.GAE_RUN).dependsOn gaelykCopyRuntimeLibraries
        }

        project.afterEvaluate {
            def libDirectory = new File(getWarConvention(project).webAppDir, LIBRARIES_DIRECTORY_RELATIVE_PATH)
            gaelykCopyRuntimeLibraries.into libDirectory
        }
    }
}
