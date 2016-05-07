package com.github.czyzby.setup.views

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.github.czyzby.kiwi.util.common.Strings
import com.github.czyzby.lml.annotation.LmlActor
import com.kotcrab.vis.ui.widget.VisSelectBox
import com.kotcrab.vis.ui.widget.VisTextField
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel
import com.kotcrab.vis.ui.widget.spinner.Spinner

/**
 * Stores data from "advanced" tab.
 * @author MJ
 */
class AdvancedData {
    @LmlActor("version") private lateinit var versionField: VisTextField
    @LmlActor("gdxVersion") private lateinit var gdxVersionField: VisTextField
    @LmlActor("javaVersion") private lateinit var javaVersionField: Spinner
    @LmlActor("sdkVersion") private lateinit var sdkVersionField: Spinner
    @LmlActor("toolsVersion") private lateinit var toolsVersionField: VisTextField
    @LmlActor("gwtVersion") private lateinit var gwtVersionField: VisSelectBox<String>
    @LmlActor("gwtPlugin") private lateinit var gwtPluginVersionField: VisTextField
    @LmlActor("serverJavaVersion") private lateinit var serverJavaVersionField: Spinner
    @LmlActor("generateSkin") private lateinit var generateSkinButton: Button
    @LmlActor("gradleWrapper") private lateinit var gradleWrapperButton: Button
    @LmlActor("gradleTasks") private lateinit var gradleTasksField: VisTextField

    val version: String
        get() = versionField.text

    val gdxVersion: String
        get() = gdxVersionField.text

    val javaVersion: String
        get() = javaVersionField.model.text

    var androidSdkVersion: String
        get() = sdkVersionField.model.text
        set(value) {
            val model = sdkVersionField.model as IntSpinnerModel
            model.value = value.toInt()
            sdkVersionField.notifyValueChanged(false)
        }

    var androidToolsVersion: String
        get() = toolsVersionField.text
        set(value) {
            toolsVersionField.text = value
        }

    val gwtVersion: String
        get() = gwtVersionField.selected

    val gwtPluginVersion: String
        get() = gwtPluginVersionField.text

    val serverJavaVersion: String
        get() = serverJavaVersionField.model.text

    val generateSkin: Boolean
        get() = generateSkinButton.isChecked

    val addGradleWrapper: Boolean
        get() = gradleWrapperButton.isChecked

    val gradleTasks: List<String>
        get() = if (gradleTasksField.isEmpty) listOf<String>()
        else gradleTasksField.text.split(Regex(Strings.WHITESPACE_SPLITTER_REGEX)).filter { it.isNotBlank() }

    fun forceSkinGeneration() {
        generateSkinButton.isChecked = true
    }
}
