package io.github.hlcaptain.symbols.gradle

import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

internal inline fun <reified T : Task> TaskContainer.registerOrConfigure(
    taskName: String,
    crossinline configureFn: T.() -> Unit,
): TaskProvider<T> =
    when (taskName) {
        in names -> named(taskName, T::class.java)
        else -> register(taskName, T::class.java)
    }.apply {
        configure { it.configureFn() }
    }
