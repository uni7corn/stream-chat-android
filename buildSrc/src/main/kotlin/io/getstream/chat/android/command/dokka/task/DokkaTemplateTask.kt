package io.getstream.chat.android.command.dokka.task

import io.getstream.chat.android.command.dokka.plugin.DokkaTemplateExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Task called to update an specified changelog. It takes most recent section and attributed a version to it.
 * Use this after the release is done.
 */
open class DokkaTemplateTask : DefaultTask() {

    @Input
    lateinit var config: DokkaTemplateExtension

    @TaskAction
    private fun command() {
        val filePath = config.filePath
        println("File path: $filePath")

        val file = File(config.filePath)
        if (!file.exists()) throw IllegalArgumentException("The file $filePath doesn't exist")

        when {
            file.isFile -> {

            }

            file.isDirectory -> {

            }
        }
    }
}
