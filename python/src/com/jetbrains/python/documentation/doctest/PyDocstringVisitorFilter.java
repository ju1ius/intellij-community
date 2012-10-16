package com.jetbrains.python.documentation.doctest;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.validation.DocStringAnnotator;
import com.jetbrains.python.validation.HighlightingAnnotator;
import com.jetbrains.python.validation.ParameterListAnnotator;
import com.jetbrains.python.validation.ReturnAnnotator;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 *
 * filter out some python inspections and annotations if we're in docstring substitution
 */
public class PyDocstringVisitorFilter implements PythonVisitorFilter {
  @Override
  public boolean isSupported(@NotNull final Class visitorClass, @NotNull final PsiFile file) {
    //inspections
    if (visitorClass == PyArgumentListInspection.class) {
      return false;
    }
    if (visitorClass == PyDocstringInspection.class || visitorClass == PyStatementEffectInspection.class ||
        visitorClass == PyUnboundLocalVariableInspection.class || visitorClass == PyUnnecessaryBackslashInspection.class ||
        visitorClass == PyUnresolvedReferencesInspection.class) {
      return false;
    }
    //annotators
    if (visitorClass == DocStringAnnotator.class || visitorClass == ParameterListAnnotator.class || visitorClass == ReturnAnnotator.class ||
        visitorClass == HighlightingAnnotator.class)
      return false;
    return true;
  }
}
