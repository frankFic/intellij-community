/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.maven.indices.MavenArtifactSearchResult
import org.jetbrains.idea.maven.indices.MavenArtifactSearcher
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager
import org.jetbrains.idea.maven.model.MavenArtifactInfo
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.plugins.gradle.codeInsight.AbstractGradleCompletionContributor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner

import com.intellij.patterns.PlatformPatterns.psiElement

/**
 * @author Vladislav.Soroka
 * @since 10/31/13
 */
class MavenDependenciesGradleCompletionContributor : AbstractGradleCompletionContributor() {
  init {
    // map-style notation:
    // e.g.:
    //    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'
    //    runtime([group:'junit', name:'junit-dep', version:'4.7'])
    //    compile(group:'junit', name:'junit-dep', version:'4.7')
    extend(CompletionType.BASIC, IN_MAP_DEPENDENCY_NOTATION, object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(params: CompletionParameters,
                                  context: ProcessingContext,
                                  result: CompletionResultSet) {
        val parent = params.position.parent?.parent
        if (parent !is GrNamedArgument || parent.parent !is GrNamedArgumentsOwner) {
          return
        }
        result.stopHere()

        if (GROUP_LABEL == parent.labelName) {
          val m = MavenProjectIndicesManager.getInstance(parent.project)
          for (groupId in m.groupIds) {
            result.addElement(LookupElementBuilder.create(groupId).withIcon(AllIcons.Nodes.PpLib))
          }
        }
        else if (NAME_LABEL == parent.labelName) {
          val groupId = findNamedArgumentValue(parent.parent as GrNamedArgumentsOwner, GROUP_LABEL) ?: return

          val m = MavenProjectIndicesManager.getInstance(parent.project)
          for (artifactId in m.getArtifactIds(groupId)) {
            result.addElement(LookupElementBuilder.create(artifactId).withIcon(AllIcons.Nodes.PpLib))
          }
        }
        else if (VERSION_LABEL == parent.labelName) {
          val namedArgumentsOwner = parent.parent as GrNamedArgumentsOwner
          val groupId = findNamedArgumentValue(namedArgumentsOwner, GROUP_LABEL) ?: return
          val artifactId = findNamedArgumentValue(namedArgumentsOwner, NAME_LABEL) ?: return

          val m = MavenProjectIndicesManager.getInstance(parent.project)
          for (version in m.getVersions(groupId, artifactId)) {
            result.addElement(LookupElementBuilder.create(version).withIcon(AllIcons.Nodes.PpLib))
          }
        }
      }
    })

    // group:name:version notation
    // e.g.:
    //    compile 'junit:junit:4.11'
    //    compile('junit:junit:4.11')
    extend(CompletionType.BASIC, IN_METHOD_DEPENDENCY_NOTATION, object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(params: CompletionParameters,
                                  context: ProcessingContext,
                                  result: CompletionResultSet) {
        val parent = params.position.parent
        if (parent !is GrLiteral || parent.parent !is GrArgumentList) return

        result.stopHere()

        val searchText = CompletionUtil.findReferenceOrAlphanumericPrefix(params)
        val searcher = MavenArtifactSearcher()
        val searchResults = searcher.search(params.position.project, searchText, MAX_RESULT)
        for (searchResult in searchResults) {
          for (artifactInfo in searchResult.versions) {
            val buf = StringBuilder()
            MavenId.append(buf, artifactInfo.groupId)
            MavenId.append(buf, artifactInfo.artifactId)
            MavenId.append(buf, artifactInfo.version)

            result.addElement(LookupElementBuilder.create(buf.toString()).withIcon(AllIcons.Nodes.PpLib))
          }
        }
      }
    })
  }

  companion object {
    private val GROUP_LABEL = "group"
    private val NAME_LABEL = "name"
    private val VERSION_LABEL = "version"
    private val DEPENDENCIES_SCRIPT_BLOCK = "dependencies"
    private val MAX_RESULT = 1000

    private val DEPENDENCIES_CALL_PATTERN = psiElement()
      .inside(true, psiElement(GrMethodCallExpression::class.java).with(
        object : PatternCondition<GrMethodCallExpression>("withInvokedExpressionText") {
          override fun accepts(expression: GrMethodCallExpression, context: ProcessingContext): Boolean {
            if (checkExpression(expression)) return true
            return checkExpression(PsiTreeUtil.getParentOfType(expression, GrMethodCallExpression::class.java))
          }

          private fun checkExpression(expression: GrMethodCallExpression?): Boolean {
            if (expression == null) return false
            val grExpression = expression.invokedExpression
            return DEPENDENCIES_SCRIPT_BLOCK == grExpression.text
          }
        }))

    private val IN_MAP_DEPENDENCY_NOTATION = psiElement()
      .and(AbstractGradleCompletionContributor.GRADLE_FILE_PATTERN)
      .withParent(GrLiteral::class.java)
      .withSuperParent(2, psiElement(GrNamedArgument::class.java))
      .and(DEPENDENCIES_CALL_PATTERN)

    private val IN_METHOD_DEPENDENCY_NOTATION = psiElement()
      .and(AbstractGradleCompletionContributor.GRADLE_FILE_PATTERN)
      .and(DEPENDENCIES_CALL_PATTERN)
  }
}
