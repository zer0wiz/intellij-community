// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleTestFixtureFactoryImpl

interface GradleTestFixtureFactory {

  fun createGradleTestFixture(
    className: String,
    methodName: String,
    gradleVersion: GradleVersion
  ): GradleTestFixture

  @ApiStatus.Experimental
  fun createFileTestFixture(
    relativePath: String,
    configure: FileTestFixture.Builder.() -> Unit
  ): FileTestFixture

  fun createGradleProjectTestFixture(
    projectName: String,
    gradleVersion: GradleVersion,
    configure: FileTestFixture.Builder.() -> Unit
  ): GradleProjectTestFixture

  companion object {
    private val ourInstance = GradleTestFixtureFactoryImpl()

    @JvmStatic
    fun getFixtureFactory(): GradleTestFixtureFactory {
      return ourInstance
    }
  }
}