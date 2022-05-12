package io.getstream.chat.android.command.dokka.plugin

import io.getstream.chat.android.command.dokka.task.DokkaTemplateTask
import io.getstream.chat.android.command.utils.registerExt
import org.gradle.api.Plugin
import org.gradle.api.Project

private const val COMMAND_NAME = "dokka-template"
private const val CONFIG_CLOJURE_NAME = "dokkaTemplate"

class DokkaTemplatePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension: DokkaTemplateExtension =
            project.extensions.create(CONFIG_CLOJURE_NAME, DokkaTemplateExtension::class.java)

        project.tasks.registerExt<DokkaTemplateTask>(COMMAND_NAME) {
            this.config = extension
        }
    }
}
