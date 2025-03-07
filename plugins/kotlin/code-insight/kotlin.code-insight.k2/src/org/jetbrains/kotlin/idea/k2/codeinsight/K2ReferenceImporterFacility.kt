// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.codeInsight.KotlinReferenceImporterFacility
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixService
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinImportQuickFixAction
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector

class K2ReferenceImporterFacility : KotlinReferenceImporterFacility {
    /**
     * N.B. This implementation is currently non-lazy, because there is no good way to combine [KaSession]
     * with a possibly suspended execution of a lazy sequence.
     *
     * Schematically, the lazy implementation looks like this:
     *
     * ```kt
     * fun getImportFixes() = sequence {
     *   analyze {
     *      yield(fix1)
     *
     *      // after the fix1 is yielded, lazy sequence is suspended here, the analyze call is not yet finished
     *
     *      yield(fix2)
     *   }
     * }
     * ```
     *
     * Now imagine that on the outside we have code like this:
     *
     * ```kt
     * analyze {
     *   val fix1 = getImportFixes().first()
     *   ... // the rest of the fixes are ignored
     * }
     *```
     *
     * In this situation, the inner [analyze] call inside of `getImportFixes` will never be finished
     * (meaning it will never execute all the possible validity checks the Analysis API side).
     *
     * That might lead to some exceptions like in KTIJ-28700.
     *
     * To avoid that, we sacrifice some possible performance, but make sure that the [analyze] call is properly finished instead.
     */
    override fun createImportFixesForExpression(expression: KtExpression): Sequence<KotlinImportQuickFixAction<*>> {
        val eagerlyComputedImportFixes = createImportFixesForExpressionLazy(expression).toList()

        return eagerlyComputedImportFixes.asSequence()
    }

    private fun createImportFixesForExpressionLazy(expression: KtExpression): Sequence<KotlinImportQuickFixAction<*>> = sequence {
        analyze(expression) {
            val file = expression.containingKtFile
            if (file.hasUnresolvedImportWhichCanImport(expression)) return@sequence

            val quickFixService = KotlinQuickFixService.getInstance()
            val diagnostics = expression
                .getDiagnostics(KtDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS)
                .filter { it.severity == Severity.ERROR && expression.textRange in it.psi.textRange }

            for (diagnostic in diagnostics) {
                val importFixes = quickFixService.getImportQuickFixesFor(diagnostic)
                for (importFix in importFixes) {
                    val element = importFix.element ?: continue

                    // obtained quick fix might be intended for an element different from `useSiteElement`, so we need to check again
                    if (!file.hasUnresolvedImportWhichCanImport(element)) {
                        yield(importFix)
                    }
                }
            }
        }
    }
}

context(KaSession)
private fun KtFile.hasUnresolvedImportWhichCanImport(element: PsiElement): Boolean {
    if (element !is KtSimpleNameExpression) return false
    val referencedName = element.getReferencedName()

    return importDirectives.any {
        (it.isAllUnder || it.importPath?.importedName?.asString() == referencedName) && !it.isResolved()
    }
}

context(KaSession)
private fun KtImportDirective.isResolved(): Boolean {
    val reference = importedReference?.getQualifiedElementSelector()?.mainReference
    return reference?.resolveToSymbol() != null
}