/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.resources;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public class ChannelResourceInspection extends BaseInspection{

    @NotNull
    public String getID(){
        return "ChannelOpenedButNotSafelyClosed";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "channel.opened.not.closed.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final PsiExpression expression = (PsiExpression) infos[0];
        final PsiType type = expression.getType();
        assert type != null;
        final String text = type.getPresentableText();
        return InspectionGadgetsBundle.message(
                "channel.opened.not.closed.problem.descriptor", text);
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ChannelResourceVisitor();
    }

    private static class ChannelResourceVisitor extends BaseInspectionVisitor{

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            if(!isChannelFactoryMethod(expression)){
                return;
            }
            final PsiElement parent = expression.getParent();
            final PsiVariable boundVariable;
            if (parent instanceof PsiAssignmentExpression) {
                final PsiAssignmentExpression assignment =
                        (PsiAssignmentExpression) parent;
                final PsiExpression lhs = assignment.getLExpression();
                if (!(lhs instanceof PsiReferenceExpression)) {
                    return;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) lhs;
                final PsiElement referent = referenceExpression.resolve();
                if (referent == null || !(referent instanceof PsiVariable)) {
                    return;
                }
                boundVariable = (PsiVariable) referent;
            } else if (parent instanceof PsiVariable) {
                boundVariable = (PsiVariable) parent;
            } else {
                boundVariable = null;
            }
            final PsiStatement statement =
                    PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
            if (statement == null) {
                return;
            }
            PsiStatement nextStatement =
                    PsiTreeUtil.getNextSiblingOfType(statement,
                            PsiStatement.class);
            while (!(nextStatement instanceof PsiTryStatement)) {
                if (!(nextStatement instanceof PsiDeclarationStatement)) {
                    registerError(expression, expression);
                    return;
                }
                if (boundVariable != null) {
                    if (VariableAccessUtils.variableIsUsed(boundVariable,
                            nextStatement)) {
                        registerError(expression, expression);
                        return;
                    }
                }
                nextStatement =
                        PsiTreeUtil.getNextSiblingOfType(nextStatement,
                                PsiStatement.class);
            }
            PsiTryStatement tryStatement = (PsiTryStatement) nextStatement;
            if (boundVariable != null &&
                    resourceIsClosedInFinally(tryStatement, boundVariable)) {
                return;
            }
            if (isChannelFactoryClosedInFinally(expression, tryStatement)) {
                return;
            }
            registerError(expression, expression);
        }

        private static boolean isChannelFactoryClosedInFinally(
                PsiMethodCallExpression expression,
                PsiTryStatement tryStatement) {
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) qualifier;
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return false;
            }
            PsiVariable variable = (PsiVariable) target;
            return resourceIsClosedInFinally(tryStatement, variable);
        }

        private static boolean isChannelFactoryMethod(
                PsiMethodCallExpression expression){
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(!HardcodedMethodConstants.GET_CHANNEL.equals(methodName)) {
                return false;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier == null) {
                return false;
            }
            return TypeUtils.expressionHasTypeOrSubtype(qualifier,
		            "java.net.Socket",
		            "java.net.DatagramSocket",
		            "java.net.ServerSocket",
		            "java.io.FileInputStream",
		            "java.io.FileOutputStream",
		            "java.io.RandomAccessFile",
                    "com.sun.corba.se.pept.transport.EventHandler",
                    "sun.nio.ch.InheritedChannel");
        }

        private static boolean resourceIsClosedInFinally(
                @NotNull PsiTryStatement tryStatement,
                @NotNull PsiVariable boundVariable){
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if(finallyBlock == null){
                return false;
            }
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if(tryBlock == null){
                return false;
            }
            final CloseVisitor visitor = new CloseVisitor(boundVariable);
            finallyBlock.accept(visitor);
            return visitor.containsClose();
        }
    }

    private static class CloseVisitor extends JavaRecursiveElementVisitor{

        private boolean containsClose = false;
        private PsiVariable objectToClose;

        private CloseVisitor(PsiVariable objectToClose){
            this.objectToClose = objectToClose;
        }

        @Override public void visitElement(@NotNull PsiElement element){
            if(!containsClose){
                super.visitElement(element);
            }
        }

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call){
            if(containsClose){
                return;
            }
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(!HardcodedMethodConstants.CLOSE.equals(methodName)){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(!(qualifier instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) qualifier;
            final PsiElement referent = referenceExpression.resolve();
            if(objectToClose.equals(referent)){
                containsClose = true;
            }
        }

        public boolean containsClose(){
            return containsClose;
        }
    }
}