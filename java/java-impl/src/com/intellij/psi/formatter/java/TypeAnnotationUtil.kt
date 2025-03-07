// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

internal object TypeAnnotationUtil {
  private val KNOWN_TYPE_ANNOTATIONS: Set<String> = setOf(
    "org.jetbrains.annotations.NotNull",
    "org.jetbrains.annotations.Nullable"
  )

  @JvmStatic
  fun isTypeAnnotation(child: ASTNode): Boolean {
    val node = child.psi as? PsiAnnotation ?: return false
    val next = PsiTreeUtil.skipSiblingsForward(node, PsiWhiteSpace::class.java, PsiAnnotation::class.java)
    if (next is PsiKeyword) return false
    val psiReference: PsiJavaCodeReferenceElement = node.nameReferenceElement ?: return false

    if (psiReference.isQualified) {
      return KNOWN_TYPE_ANNOTATIONS.contains(getCanonicalTextOfTheReference(psiReference))
    }
    else {
      val referenceName = psiReference.referenceNameElement ?: return false
      val file = psiReference.containingFile as? PsiJavaFile ?: return false
      val referenceNameText = referenceName.text
      return getImportedTypeAnnotations(file).contains(referenceNameText)
    }
  }

  private fun getImportedTypeAnnotations(file : PsiJavaFile): Set<String> = CachedValuesManager.getCachedValue(file) {
      val importList = file.importList ?: return@getCachedValue CachedValueProvider.Result(emptySet(), PsiModificationTracker.MODIFICATION_COUNT)
      val filteredAnnotations = KNOWN_TYPE_ANNOTATIONS.filter { isAnnotationInImportList(it, importList) }
        .mapNotNull { fqn -> fqn.split(".").lastOrNull() }
        .toSet()
      CachedValueProvider.Result.create(filteredAnnotations, PsiModificationTracker.MODIFICATION_COUNT)
    }

  private fun isAnnotationInImportList(annotationFqn: String, importList: PsiImportList): Boolean {
    val packageName = StringUtil.getPackageName(annotationFqn)
    return importList.importStatements.any { statement: PsiImportStatement ->
      val referenceElement = statement.importReference ?: return@any false
      val referenceElementText = getCanonicalTextOfTheReference(referenceElement)
      referenceElementText == annotationFqn || statement.isOnDemand && referenceElementText.startsWith(packageName)
    }
  }

  private fun getCanonicalTextOfTheReference(importReference: PsiJavaCodeReferenceElement): String = importReference.text.let { referenceText ->
    referenceText
      .split(".")
      .joinToString(separator = ".") { pathPart -> pathPart.trim() }
  }
}