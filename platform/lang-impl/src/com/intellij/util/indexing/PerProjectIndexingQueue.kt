// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.roots.IndexableFilesIterator
import it.unimi.dsi.fastutil.longs.LongArraySet
import it.unimi.dsi.fastutil.longs.LongSet
import it.unimi.dsi.fastutil.longs.LongSets
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private class PerProviderSinkFactory(private val uncommittedListener: UncommittedFilesListener) {
  interface UncommittedFilesListener {
    fun onUncommittedCountChanged(cntDirty: Int)
    fun commit(iterator: IndexableFilesIterator, files: Collection<VirtualFile>, scanningId: Long)
  }

  private val activeSinksCount: AtomicInteger = AtomicInteger()
  private val cancelActiveSinks: AtomicBoolean = AtomicBoolean()
  private val cntFilesDirty = AtomicInteger()

  inner class PerProviderSinkImpl(private val iterator: IndexableFilesIterator,
                                  private val scanningId: Long) : PerProjectIndexingQueue.PerProviderSink {
    private var files: PersistentSet<VirtualFile> = persistentSetOf()
    private var closed = false

    init {
      activeSinksCount.incrementAndGet()
    }

    override fun addFile(file: VirtualFile) {
      LOG.assertTrue(!closed, "Should not invoke 'addFile' after 'close'")
      if (cancelActiveSinks.get()) {
        ProgressManager.getGlobalProgressIndicator()?.cancel()
        ProgressManager.checkCanceled()
      }

      files = files.add(file)
      val cntDirty = cntFilesDirty.incrementAndGet()
      uncommittedListener.onUncommittedCountChanged(cntDirty)
    }

    private fun commit() {
      LOG.assertTrue(!closed, "Should not invoke 'commit' after 'close'")

      if (files.isNotEmpty()) {
        val cntDirty = cntFilesDirty.addAndGet(-files.size)
        LOG.assertTrue(cntDirty >= 0, "cntFilesDirty should be positive or 0: $cntDirty")
        uncommittedListener.commit(iterator, files, scanningId)
        files = files.clear()
      }
    }

    override fun close() {
      try {
        if (!closed) {
          commit()
        }
      }
      finally {
        closed = true
        activeSinksCount.decrementAndGet()
      }
    }
  }

  fun newSink(provider: IndexableFilesIterator, scanningId: Long): PerProjectIndexingQueue.PerProviderSink {
    if (cancelActiveSinks.get()) {
      ProgressManager.getGlobalProgressIndicator()?.cancel()
      ProgressManager.checkCanceled()
    }

    return PerProviderSinkImpl(provider, scanningId)
  }

  fun cancelAllProducersAndWait() {
    cancelActiveSinks.set(true)
    ProgressIndicatorUtils.awaitWithCheckCanceled {
      PingProgress.interactWithEdtProgress()
      LockSupport.parkNanos(50_000_000)
      activeSinksCount.get() == 0
    }
    LOG.assertTrue(cntFilesDirty.get() == 0, "Should contain no dirty files. But got: " + cntFilesDirty.get())
  }

  fun resumeProducers() {
    cancelActiveSinks.set(false)
  }

  fun getUncommittedDirtyFilesCount(): Int = cntFilesDirty.get()

  companion object {
    private val LOG = logger<PerProviderSinkFactory>()
  }
}

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class PerProjectIndexingQueue(private val project: Project) {
  /**
   *  Not thread safe. These classes are cheap to construct and use - don't share instances.
   *  <p>
   *  Always use try-with-resources when creating instances of this interface, otherwise [cancelAllTasksAndWait] may never end waiting
   */
  interface PerProviderSink : AutoCloseable {
    fun addFile(file: VirtualFile)
    override fun close()
  }

  private val sinkFactory = PerProviderSinkFactory(object : PerProviderSinkFactory.UncommittedFilesListener {
    override fun onUncommittedCountChanged(cntDirty: Int) = publishEstimatedFilesCount(cntDirty)
    override fun commit(iterator: IndexableFilesIterator, files: Collection<VirtualFile>, scanningId: Long) = addFiles(iterator, files,
                                                                                                                 scanningId)
  })

  private val estimatedFilesCount: MutableStateFlow<Int> = MutableStateFlow(0)

  // guarded by [lock]. Must be in consistent state under write lock (see [lock] comment)
  // Total count of VirtualFile in filesSoFar. This is (arguable) performance optimization
  private val cntFilesSoFar = AtomicInteger()

  // guarded by [lock]. Must be in consistent state under write lock (see [lock] comment)
  // Files that will be re-indexed
  private var filesSoFar: MutableSet<VirtualFile> = ConcurrentHashMap.newKeySet()

  // guarded by [lock]. Must be in consistent state under write lock (see [lock] comment)
  // Ids of scannings from which [filesSoFar] came
  private var scanningIds: LongSet = createSetForScanningIds()

  // Code under read lock still runs in parallel, so all the counters (e.g. [cntFilesSoFar]) and collections (e.g. [filesSoFar]) still have
  // to be thread-safe. It is only required that the state must be consistent under the write lock (e.g. [cntFilesSoFar] corresponds to total
  // count of files in [filesSoFar])
  private val lock = ReentrantReadWriteLock()

  @Volatile
  private var allowFlushing: Boolean = true

  // `private` because we want clients to use [PerProviderSink] which forces them to report files one by one.
  // Accepting `List<VirtualFile>` delays the moment when we know that many files have changed, and we need a dumb mode.
  // Accepting [VirtualFile] without intermediate buffering ([PerProviderSink] is essentially a non-thread safe buffer) and adding
  // them directly to [filesSoFar] will likely slow down the process (though this assumption is not properly verified)
  private fun addFiles(iterator: IndexableFilesIterator, files: Collection<VirtualFile>, scanningId: Long) {
    lock.read {
      filesSoFar.addAll(files)
      cntFilesSoFar.addAndGet(files.size)
      scanningIds.add(scanningId)
    }
  }

  private fun publishEstimatedFilesCount(cntUncommittedDirty: Int) {
    val newValue = (cntUncommittedDirty + cntFilesSoFar.get())
    estimatedFilesCount.value = newValue
  }

  fun flushNow(reason: String) {
    if (!allowFlushing) {
      LOG.info("Flushing is not allowed at the moment")
      return
    }
    val (filesInQueue, totalFiles, scanningIds) = getAndResetQueuedFiles()
    if (totalFiles > 0) {
      // note that DumbModeWhileScanningTrigger will not finish dumb mode until scanning is finished
      UnindexedFilesIndexer(project, filesInQueue, reason, scanningIds).queue(project)
    }
    else {
      LOG.info("Finished for " + project.name + ". No files to index with loading content.")
    }
  }

  fun clear() {
    getFilesAndClear()
  }

  @VisibleForTesting
  fun <T> getFilesSubmittedDuring(block: () -> T): Pair<T, Collection<VirtualFile>> {
    allowFlushing = false
    try {
      val result: T = block()
      return Pair(result, getFilesAndClear())
    }
    finally {
      allowFlushing = true
    }
  }

  private fun getFilesAndClear(): Collection<VirtualFile> {
    val files = getAndResetQueuedFiles()
    return files.fileSet
  }

  private data class QueuedFiles(
    val fileSet: Set<VirtualFile>,
    val numberOfFiles: Int,
    val scanningIds: LongSet
  )

  private fun getAndResetQueuedFiles(): QueuedFiles {
    try {
      return lock.write {
        val filesInQueue = filesSoFar
        filesSoFar = ConcurrentHashMap.newKeySet()
        val totalFiles = cntFilesSoFar.getAndSet(0)
        val idsOfScannings = scanningIds
        scanningIds = createSetForScanningIds()
        return@write QueuedFiles(filesInQueue, totalFiles, LongSets.unmodifiable(idsOfScannings))
      }
    }
    finally {
      publishEstimatedFilesCount(sinkFactory.getUncommittedDirtyFilesCount())
    }
  }

  /**
   * Creates new instance of **thread-unsafe** [PerProviderSink]
   * Will throw [ProcessCanceledException] if the queue is suspended via [cancelAllTasksAndWait]
   */
  fun getSink(provider: IndexableFilesIterator, scanningId: Long): PerProviderSink {
    return sinkFactory.newSink(provider, scanningId)
  }

  /**
   * Cancels all the created [PerProviderSink] and waits until all the Sinks are finished (invoke [PerProviderSink.commit()]).
   * New invocations of [PerProjectIndexingQueue.getSink()] will throw [ProcessCanceledException].
   * Use [resumeQueue] to resume the queue.
   * Does nothing if the queue is already suspended.
   */
  fun cancelAllTasksAndWait() {
    sinkFactory.cancelAllProducersAndWait()
  }

  /**
   * Resumes the queue after [cancelAllTasksAndWait] invocation.
   * Does nothing if the queue is already resumed.
   */
  fun resumeQueue() {
    sinkFactory.resumeProducers()
  }

  fun estimatedFilesCount(): StateFlow<Int> = estimatedFilesCount

  companion object {
    private val LOG = logger<PerProjectIndexingQueue>()
    private fun createSetForScanningIds(): LongSet = LongSets.synchronize(LongArraySet(4))
  }

  @TestOnly
  class TestCompanion(private val q: PerProjectIndexingQueue) {
    fun getAndResetQueuedFiles(): Pair<Set<VirtualFile>, Int> {
      return q.getAndResetQueuedFiles().let { Pair(it.fileSet, it.numberOfFiles) }
    }
  }
}