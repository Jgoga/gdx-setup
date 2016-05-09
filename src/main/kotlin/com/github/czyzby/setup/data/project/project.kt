package com.github.czyzby.setup.data.project

import com.badlogic.gdx.Files
import com.badlogic.gdx.utils.GdxRuntimeException
import com.github.czyzby.setup.data.files.*
import com.github.czyzby.setup.data.gradle.GradleFile
import com.github.czyzby.setup.data.gradle.RootGradleFile
import com.github.czyzby.setup.data.langs.Java
import com.github.czyzby.setup.data.platforms.Android
import com.github.czyzby.setup.data.platforms.Assets
import com.github.czyzby.setup.data.platforms.Platform
import com.github.czyzby.setup.data.templates.Template
import com.github.czyzby.setup.views.AdvancedData
import com.github.czyzby.setup.views.BasicProjectData
import com.github.czyzby.setup.views.ExtensionsData
import com.github.czyzby.setup.views.LanguagesData
import com.kotcrab.vis.ui.util.OsUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Contains data about the generated project.
 * @author MJ
 */
class Project(val basic: BasicProjectData, val platforms: Map<String, Platform>, val advanced: AdvancedData,
              val languages: LanguagesData, val extensions: ExtensionsData, val template: Template) {
    private val gradleFiles: Map<String, GradleFile>
    val files = mutableListOf<ProjectFile>()
    val rootGradle: RootGradleFile
    val properties = mutableMapOf(
            "org.gradle.daemon" to "true",
            "org.gradle.jvmargs" to "-Xms128m -Xmx512m",
            "org.gradle.configureondemand" to "true")
    val postGenerationTasks = mutableListOf<(Project) -> Unit>()
    val gwtInherits = mutableSetOf<String>()
    val reflected = mutableSetOf<String>()

    init {
        gradleFiles = mutableMapOf<String, GradleFile>()
        rootGradle = RootGradleFile(this)
        platforms.forEach { gradleFiles[it.key] = it.value.createGradleFile(this) }
    }

    fun hasPlatform(id: String): Boolean = platforms.containsKey(id)

    fun getGradleFile(id: String): GradleFile = gradleFiles.get(id)!!

    fun generate() {
        addBasicFiles()
        addJvmLanguagesSupport()
        addExtensions()
        template.apply(this)
        addPlatforms()
        addSkinAssets()
        saveProperties()
        saveFiles()
        // Invoking post-generation tasks:
        postGenerationTasks.forEach { it(this) }
    }

    private fun addBasicFiles() {
        // Adding global assets folder:
        files.add(SourceDirectory(Assets.ID, ""))
        // Adding .gitignore:
        files.add(CopiedFile(path = ".gitignore", original = path("generator", "gitignore")))
    }

    private fun addJvmLanguagesSupport() {
        Java().initiate(this) // Java is supported by default.
        languages.getSelectedLanguages().forEach {
            it.initiate(this)
            properties[it.id + "Version"] = languages.getVersion(it.id)
        }
        languages.appendSelectedLanguagesVersions(this)
    }

    private fun addExtensions() {
        extensions.getSelectedOfficialExtensions().forEach { it.initiate(this) }
        extensions.getSelectedThirdPartyExtensions().forEach { it.initiate(this) }
    }

    private fun addPlatforms() {
        platforms.values.forEach { it.initiate(this) }
        SettingsFile(platforms.values).save(basic.destination)
    }

    private fun saveFiles() {
        rootGradle.save(basic.destination)
        gradleFiles.values.forEach { it.save(basic.destination) }
        files.forEach { it.save(basic.destination) }
    }

    private fun saveProperties() {
        // Adding LibGDX version property:
        properties["gdxVersion"] = advanced.gdxVersion
        PropertiesFile(properties).save(basic.destination)
    }

    private fun addSkinAssets() {
        if (advanced.generateSkin) {
            // Adding raw assets directory:
            files.add(SourceDirectory("raw", "ui"))
            // Adding GUI assets directory:
            files.add(SourceDirectory(Assets.ID, "ui"))
            // Adding JSON file:
            files.add(CopiedFile(projectName = Assets.ID, path = path("ui", "skin.json"),
                    original = path("generator", "assets", "ui", "skin.json")))
            // Android does not support classpath fonts. Explicitly copying Arial if Android is not included:
            if (hasPlatform(Android.ID)) {
                arrayOf("png", "fnt").forEach {
                    val path = path("com", "badlogic", "gdx", "utils", "arial-15.$it")
                    files.add(CopiedFile(projectName = Assets.ID, path = path, original = path, fileType = Files.FileType.Classpath))
                }
            }

            // Copying raw assets - internal files listing doesn't work, so we're hard-coding raw/ui content:
            arrayOf("check.png", "check-on.png", "dot.png", "knob-h.png", "knob-v.png", "line-h.png", "line-v.png",
                    "pack.json", "rect.png", "select.9.png", "square.png", "tree-minus.png", "tree-plus.png",
                    "window-border.9.png", "window-resize.9.png").forEach {
                files.add(CopiedFile(projectName = "raw", path = "ui${File.separator}$it",
                        original = path("generator", "raw", "ui", it)))
            }

            // Appending "pack" task to root Gradle:
            postGenerationTasks.add({
                basic.destination.child(rootGradle.path).writeString("""
// Run `gradle pack` task to generate skin.atlas file at assets/ui.
import com.badlogic.gdx.tools.texturepacker.TexturePacker
task pack << {
  // Note that if you need multiple atlases, you can duplicate the
  // TexturePacker.process invocation and change paths to generate
  // additional atlases with this task.
  TexturePacker.process(
    'raw/ui',           // Raw assets path.
    'assets/ui',        // Output directory.
    'skin'              // Name of the generated atlas (without extension).
  )
}""", true, "UTF-8");
            })
        }
    }

    fun includeGradleWrapper(logger: ProjectLogger) {
        if (advanced.addGradleWrapper) {
            arrayOf("gradlew", "gradlew.bat", path("gradle", "wrapper", "gradle-wrapper.jar"),
                    path("gradle", "wrapper", "gradle-wrapper.properties")).forEach {
                CopiedFile(path = it, original = path("generator", it)).save(basic.destination)
            }
            basic.destination.child("gradlew").file().setExecutable(true)
            basic.destination.child("gradlew.bat").file().setExecutable(true)
            logger.logNls("copyGradle")
        }
        val gradleTasks = advanced.gradleTasks
        if (gradleTasks.isNotEmpty()) {
            logger.logNls("runningGradleTasks")
            val commands = determineGradleCommand() + advanced.gradleTasks
            logger.log(commands.joinToString(separator = " "))
            val process = ProcessBuilder(*commands).directory(basic.destination.file()).redirectErrorStream(true).start()
            val stream = BufferedReader(InputStreamReader(process.inputStream))
            var line = stream.readLine();
            while (line != null) {
                logger.log(line)
                line = stream.readLine();
            }
            process.waitFor()
            if (process.exitValue() != 0) {
                throw GdxRuntimeException("Gradle process ended with non-zero value.")
            }
        }
    }

    private fun determineGradleCommand(): Array<String> {
        return if (OsUtils.isWindows()) {
            arrayOf("cmd", "/c", if (advanced.addGradleWrapper) "gradlew" else "gradle")
        } else {
            arrayOf(if (advanced.addGradleWrapper) "./gradlew" else "gradle")
        }
    }
}

interface ProjectLogger {
    fun log(message: String)
    fun logNls(bundleLine: String)
}
