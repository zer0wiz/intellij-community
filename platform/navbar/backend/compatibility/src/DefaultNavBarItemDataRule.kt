// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.backend.compatibility

import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.CommonDataKeys.*
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.MODULE
import com.intellij.openapi.module.ModuleType
import com.intellij.platform.navbar.backend.NavBarItem
import com.intellij.platform.navbar.backend.NavBarItemProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore

internal class DefaultNavBarItemDataRule : GetDataRule {

  override fun getData(dataProvider: DataProvider): Pointer<out NavBarItem>? {
    return getNavBarItem(dataProvider)?.createPointer()
  }

  private fun getNavBarItem(dataProvider: DataProvider): NavBarItem? {
    val ctx = DataContext { dataId -> dataProvider.getData(dataId) }

    // leaf element -- either from old EP impls or default one
    // owner -- EP extension provided the leaf (if any)
    val (leaf, owner) = fromOldExtensions { ext -> ext.getLeafElement(ctx)?.let { it to ext } }
                        ?: fromDataContext(ctx)?.let { Pair(it, null) }
                        ?: return null

    if (leaf.isValid) {
      if (PsiUtilCore.getVirtualFile(leaf)
            ?.getUserData(NavBarModelExtension.IGNORE_IN_NAVBAR) == true) {
        return null
      }
      return PsiNavBarItem(leaf, owner)
    }
    else {
      // Narrow down the root element to the first interesting one
      MODULE.getData(ctx)
        ?.takeUnless { ModuleType.isInternal(it) }
        ?.let { return ModuleNavBarItem(it) }

      val projectItem = PROJECT.getData(ctx)
                          ?.let(::ProjectNavBarItem)
                        ?: return null

      val childItem = NavBarItemProvider.EP_NAME
                        .extensionList.asSequence()
                        .flatMap { ext -> ext.iterateChildren(projectItem) }
                        .firstOrNull()
                      ?: return projectItem

      val grandChildItem = NavBarItemProvider.EP_NAME
                             .extensionList.asSequence()
                             .flatMap { ext -> ext.iterateChildren(childItem) }
                             .firstOrNull()
                           ?: return childItem

      return grandChildItem
    }
  }

  private fun fromDataContext(ctx: DataContext): PsiElement? {
    val psiFile = PSI_FILE.getData(ctx)
    if (psiFile != null) {
      ensurePsiFromExtensionIsValid(psiFile, "Context PSI_FILE is invalid", psiFile.javaClass)
      return adjustWithAllExtensions(psiFile)
    }

    val fileSystemItem = PsiUtilCore.findFileSystemItem(PROJECT.getData(ctx), VIRTUAL_FILE.getData(ctx))
    if (fileSystemItem != null) {
      ensurePsiFromExtensionIsValid(fileSystemItem, "Context fileSystemItem is invalid", fileSystemItem.javaClass)
      return adjustWithAllExtensions(fileSystemItem)
    }

    return null
  }

}
