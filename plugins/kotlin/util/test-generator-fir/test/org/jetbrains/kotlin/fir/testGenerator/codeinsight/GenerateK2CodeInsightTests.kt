// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.k2.AbstractK2ExpressionTypeTest
import org.jetbrains.kotlin.idea.k2.AbstractKotlinFirBreadcrumbsTest
import org.jetbrains.kotlin.idea.k2.AbstractKotlinFirJoinLinesTest
import org.jetbrains.kotlin.idea.k2.AbstractKotlinFirPairMatcherTest
import org.jetbrains.kotlin.idea.k2.hierarchy.AbstractFirHierarchyTest
import org.jetbrains.kotlin.idea.k2.hints.AbstractKtCallChainHintsProviderTest
import org.jetbrains.kotlin.idea.k2.hints.AbstractKtLambdasHintsProvider
import org.jetbrains.kotlin.idea.k2.hints.AbstractKtParameterHintsProviderTest
import org.jetbrains.kotlin.idea.k2.hints.AbstractKtRangesHintsProviderTest
import org.jetbrains.kotlin.idea.k2.hints.AbstractKtReferenceTypeHintsProviderTest
import org.jetbrains.kotlin.idea.k2.moveUpDown.AbstractFirMoveLeftRightTest
import org.jetbrains.kotlin.idea.k2.moveUpDown.AbstractKotlinFirMoveStatementTest
import org.jetbrains.kotlin.idea.k2.quickDoc.AbstractFirRenderingKDocTest
import org.jetbrains.kotlin.idea.k2.structureView.AbstractKotlinGoToSuperDeclarationsHandlerTest
import org.jetbrains.kotlin.idea.k2.surroundWith.AbstractKotlinFirSurroundWithTest
import org.jetbrains.kotlin.idea.k2.unwrap.AbstractKotlinFirUnwrapRemoveTest
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.*
import org.jetbrains.kotlin.testGenerator.model.Patterns.DIRECTORY
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_OR_KTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.TEST
import org.jetbrains.kotlin.testGenerator.model.Patterns.forRegex

internal fun MutableTWorkspace.generateK2CodeInsightTests() {
    generateK2InspectionTests()
    generateK2FixTests()
    generateK2IntentionTests()
    generateK2StructureViewTests()
    generateK2PostfixTemplateTests()
    generateK2LiveTemplateTests()
    generateK2LineMarkerTests()

    testGroup("code-insight/kotlin.code-insight.k2", category = CODE_INSIGHT) {
        testClass<AbstractKotlinGoToSuperDeclarationsHandlerTest> {
            model("gotoSuperDeclarationsHandler", pattern = Patterns.KT_WITHOUT_DOTS, passTestDataPath = false)
        }
        testClass<AbstractKotlinFirSurroundWithTest> {
            model("../../../idea/tests/testData/codeInsight/surroundWith/if", testMethodName = "doTestWithIfSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/ifElse", testMethodName = "doTestWithIfElseSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/ifElseExpression", testMethodName = "doTestWithIfElseExpressionSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/ifElseExpressionBraces", testMethodName = "doTestWithIfElseExpressionBracesSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/not", testMethodName = "doTestWithNotSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/parentheses", testMethodName = "doTestWithParenthesesSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/stringTemplate", testMethodName = "doTestWithStringTemplateSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/when", testMethodName = "doTestWithWhenSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/tryCatch", testMethodName = "doTestWithTryCatchSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/tryCatchExpression", testMethodName = "doTestWithTryCatchExpressionSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/tryCatchFinally", testMethodName = "doTestWithTryCatchFinallySurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/tryCatchFinallyExpression", testMethodName = "doTestWithTryCatchFinallyExpressionSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/tryFinally", testMethodName = "doTestWithTryFinallySurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/tryFinallyExpression", testMethodName = "doTestWithTryFinallyExpressionSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/functionLiteral", testMethodName = "doTestWithFunctionLiteralSurrounder")
            model("../../../idea/tests/testData/codeInsight/surroundWith/withIfExpression", testMethodName = "doTestWithSurroundWithIfExpression")
            model("../../../idea/tests/testData/codeInsight/surroundWith/withIfElseExpression", testMethodName = "doTestWithSurroundWithIfElseExpression")
        }
        testClass<AbstractKotlinFirBreadcrumbsTest> {
            model("../../../idea/tests/testData/codeInsight/breadcrumbs", pattern = KT_OR_KTS)
        }
        testClass<AbstractKotlinFirPairMatcherTest> {
            model("../../../idea/tests/testData/codeInsight/pairMatcher")
        }
        testClass<AbstractKotlinFirUnwrapRemoveTest> {
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/removeExpression", testMethodName = "doTestExpressionRemover")
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/unwrapThen", testMethodName = "doTestThenUnwrapper")
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/unwrapElse", testMethodName = "doTestElseUnwrapper")
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/removeElse", testMethodName = "doTestElseRemover")
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/unwrapLoop", testMethodName = "doTestLoopUnwrapper")
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/unwrapTry", testMethodName = "doTestTryUnwrapper")
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/unwrapCatch", testMethodName = "doTestCatchUnwrapper")
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/removeCatch", testMethodName = "doTestCatchRemover")
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/unwrapFinally", testMethodName = "doTestFinallyUnwrapper")
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/removeFinally", testMethodName = "doTestFinallyRemover")
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/unwrapLambda", testMethodName = "doTestLambdaUnwrapper")
            model("../../../idea/tests/testData/codeInsight/unwrapAndRemove/unwrapFunctionParameter", testMethodName = "doTestFunctionParameterUnwrapper")
        }

        testClass<AbstractK2ExpressionTypeTest> {
            model("../../../idea/tests/testData/codeInsight/expressionType", pattern = KT or TEST)
        }

        testClass<AbstractKotlinFirMoveStatementTest> {
            model("../../../idea/tests/testData/codeInsight/moveUpDown/classBodyDeclarations", pattern = KT_OR_KTS, testMethodName = "doTestClassBodyDeclaration")
            model("../../../idea/tests/testData/codeInsight/moveUpDown/closingBraces", testMethodName = "doTestExpression")
            model("../../../idea/tests/testData/codeInsight/moveUpDown/expressions", pattern = KT_OR_KTS, testMethodName = "doTestExpression")
            model("../../../idea/tests/testData/codeInsight/moveUpDown/line", testMethodName = "doTestLine")
            model("../../../idea/tests/testData/codeInsight/moveUpDown/parametersAndArguments", testMethodName = "doTestExpression")
            model("../../../idea/tests/testData/codeInsight/moveUpDown/trailingComma", testMethodName = "doTestExpressionWithTrailingComma")
        }

        testClass<AbstractFirMoveLeftRightTest> {
            model("../../../idea/tests/testData/codeInsight/moveLeftRight")
        }

        val inlayHintsFileRegexp = forRegex("^([^_]\\w+)\\.kt$")
        testClass<AbstractKtReferenceTypeHintsProviderTest> {
            model("../../../idea/tests/testData/codeInsight/hints/types", pattern = inlayHintsFileRegexp)
        }
        testClass<AbstractKtLambdasHintsProvider> {
            model("../../../idea/tests/testData/codeInsight/hints/lambda")
        }
        testClass<AbstractKtRangesHintsProviderTest> {
            model("../../../idea/tests/testData/codeInsight/hints/ranges")
        }
        testClass<AbstractKtParameterHintsProviderTest> {
            model("../../../idea/tests/testData/codeInsight/hints/arguments", pattern = inlayHintsFileRegexp)
        }
        testClass<AbstractKtCallChainHintsProviderTest> {
            model("../../../idea/tests/testData/codeInsight/hints/chainCall", pattern = inlayHintsFileRegexp)
        }

        testClass<AbstractFirRenderingKDocTest> {
            model("../../../idea/tests/testData/codeInsight/renderingKDoc")
        }

        testClass<AbstractFirHierarchyTest> {
            model("../../../idea/tests/testData/hierarchy/calls/callers", pattern = DIRECTORY, isRecursive = false, testMethodName = "doCallerHierarchyTest")
            model("../../../idea/tests/testData/hierarchy/calls/callersJava", pattern = DIRECTORY, isRecursive = false, testMethodName = "doCallerJavaHierarchyTest")
            model("../../../idea/tests/testData/hierarchy/calls/callees", pattern = DIRECTORY, isRecursive = false, testMethodName = "doCalleeHierarchyTest")
        }

        testClass<AbstractKotlinFirJoinLinesTest> {
            model("../../../idea/tests/testData/joinLines")
        }
    }
}