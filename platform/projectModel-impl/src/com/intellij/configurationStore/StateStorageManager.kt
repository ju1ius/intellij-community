// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.*
import com.intellij.util.messages.Topic
import kotlinx.coroutines.runBlocking

val STORAGE_TOPIC = Topic("STORAGE_LISTENER", StorageManagerListener::class.java, Topic.BroadcastDirection.TO_PARENT)

interface StateStorageManager {
  val macroSubstitutor: PathMacroSubstitutor?
    get() = null

  val componentManager: ComponentManager?

  fun getStateStorage(storageSpec: Storage): StateStorage

  fun addStreamProvider(provider: StreamProvider, first: Boolean = false)

  fun removeStreamProvider(clazz: Class<out StreamProvider>)

  /**
   * Rename file
   * @param path System-independent full old path (/project/bar.iml or collapse $MODULE_FILE$)
   * *
   * @param newName Only new file name (foo.iml)
   */
  fun rename(path: String, newName: String)

  fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation): StateStorage?

  fun expandMacros(path: String): String
}

interface StorageCreator {
  val key: String

  fun create(storageManager: StateStorageManager): StateStorage
}

/**
 * Low-level method to save component manager state store. Intended for Java clients only.
 */
@JvmOverloads
fun saveComponentManager(componentManager: ComponentManager, isForceSavingAllSettings: Boolean = false) {
  runBlocking {
    componentManager.stateStore.save(isForceSavingAllSettings = isForceSavingAllSettings)
  }
}