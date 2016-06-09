/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubsHierarchy.ClassHierarchy;
import com.intellij.psi.stubsHierarchy.SmartClassAnchor;
import com.intellij.psi.stubsHierarchy.impl.Symbol.ClassSymbol;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Compact representation of total hierarchy of JVM classes.
 */
public class SingleClassHierarchy extends ClassHierarchy {
  private final BitSet myCoveredFiles;
  private final BitSet myAmbiguousSupers;
  private final List<StubClassAnchor> myCoveredClasses;
  private final StubClassAnchor[] myClassAnchors;
  private final StubClassAnchor[] myClassAnchorsByFileIds;
  private int[] mySubtypes;
  private int[] mySubtypeStarts;

  public SingleClassHierarchy(ClassSymbol[] classSymbols) {
    myCoveredFiles = calcCoveredFiles(classSymbols);
    myClassAnchors = ContainerUtil.map2Array(classSymbols, StubClassAnchor.class, symbol -> symbol.myClassAnchor);
    myClassAnchorsByFileIds = mkByFileId(myClassAnchors);
    excludeUncoveredFiles(classSymbols);
    connectSubTypes(classSymbols);
    myAmbiguousSupers = calcAmbiguousSupers(classSymbols);
    myCoveredClasses = Collections.unmodifiableList(ContainerUtil.filter(myClassAnchors, this::isCovered));
  }

  @NotNull
  private static BitSet calcAmbiguousSupers(ClassSymbol[] classSymbols) {
    BitSet ambiguousSupers = new BitSet();
    for (ClassSymbol symbol : classSymbols) {
      if (!symbol.isHierarchyIncomplete() && symbol.hasAmbiguousSupers()) {
        ambiguousSupers.set(symbol.myClassAnchor.myId);
      }
    }
    return ambiguousSupers;
  }

  @NotNull
  private static BitSet calcCoveredFiles(ClassSymbol[] classSymbols) {
    BitSet problematicFiles = new BitSet();
    BitSet coveredFiles = new BitSet();
    for (ClassSymbol symbol : classSymbols) {
      coveredFiles.set(symbol.myClassAnchor.myFileId);
      if (symbol.isHierarchyIncomplete()) {
        problematicFiles.set(symbol.myClassAnchor.myFileId);
      }
    }
    coveredFiles.andNot(problematicFiles);
    return coveredFiles;
  }

  private boolean isCovered(@NotNull StubClassAnchor anchor) {
    return myCoveredFiles.get(anchor.myFileId);
  }

  @Override
  @NotNull
  public List<StubClassAnchor> getCoveredClasses() {
    return myCoveredClasses;
  }

  @Override
  @NotNull
  public List<StubClassAnchor> getAllClasses() {
    return Collections.unmodifiableList(Arrays.asList(myClassAnchors));
  }

  @NotNull
  @Override
  public SmartClassAnchor[] getDirectSubtypeCandidates(@NotNull PsiClass psiClass) {
    PsiElement original = psiClass.getOriginalElement();
    if (original instanceof PsiClass) {
      psiClass = (PsiClass)original;
    }
    int fileId = Math.abs(FileBasedIndex.getFileId(psiClass.getContainingFile().getVirtualFile()));
    SmartClassAnchor anchor = forPsiClass(fileId, psiClass);
    return anchor == null ? StubClassAnchor.EMPTY_ARRAY : getDirectSubtypeCandidates(anchor);
  }

  @NotNull
  @Override
  public SmartClassAnchor[] getDirectSubtypeCandidates(@NotNull SmartClassAnchor anchor) {
    int symbolId = ((StubClassAnchor)anchor).myId;
    int start = subtypeStart(symbolId);
    int end = subtypeEnd(symbolId);
    int length = end - start;
    if (length == 0) {
      return StubClassAnchor.EMPTY_ARRAY;
    }
    StubClassAnchor[] result = new StubClassAnchor[length];
    for (int i = 0; i < length; i++) {
      result[i] = myClassAnchors[mySubtypes[start + i]];
    }
    return result;
  }

  @Override
  public boolean hasAmbiguousSupers(@NotNull SmartClassAnchor anchor) {
    return myAmbiguousSupers.get(((StubClassAnchor)anchor).myId);
  }

  @NotNull
  @Override
  public GlobalSearchScope restrictToUncovered(@NotNull GlobalSearchScope scope) {
    if (myCoveredClasses.isEmpty()) {
      return scope;
    }

    return new DelegatingGlobalSearchScope(scope, this) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        if (file instanceof VirtualFileWithId && myCoveredFiles.get(((VirtualFileWithId)file).getId())) {
          return false;
        }

        return super.contains(file);
      }
    };
  }

  private static StubClassAnchor[] mkByFileId(final StubClassAnchor[] classAnchors) {
    StubClassAnchor[] result = new StubClassAnchor[classAnchors.length];
    StubClassAnchor lastProcessedAnchor = null;
    int i = 0;
    for (StubClassAnchor classAnchor : classAnchors) {
      if (lastProcessedAnchor == null || lastProcessedAnchor.myFileId != classAnchor.myFileId) {
        result[i++] = classAnchor;
      }
      lastProcessedAnchor = classAnchor;
    }
    // compacting
    result = Arrays.copyOf(result, i);

    Arrays.sort(result, Comparator.comparing(a -> a.myFileId));
    return result;
  }

  private void connectSubTypes(ClassSymbol[] classSymbols) {
    int[] sizes = calculateSizes(classSymbols);

    int[] starts = new int[classSymbols.length];
    int count = 0;

    for (int i = 0; i < sizes.length; i++) {
      starts[i] = count;
      count += sizes[i];
    }
    int[] subtypes = new int[count];
    int[] filled = new int[sizes.length];

    for (int subTypeId = 0; subTypeId < classSymbols.length; subTypeId++) {
      ClassSymbol subType = classSymbols[subTypeId];
      for (ClassSymbol superType : subType.rawSuperClasses()) {
        int superTypeId = superType.myClassAnchor.myId;
        subtypes[starts[superTypeId] + filled[superTypeId]] = subTypeId;
        filled[superTypeId] += 1;
      }
    }

    this.mySubtypes = subtypes;
    this.mySubtypeStarts = starts;
  }

  private void excludeUncoveredFiles(ClassSymbol[] classSymbols) {
    for (ClassSymbol symbol : classSymbols) {
      if (!isCovered(symbol.myClassAnchor)) {
        symbol.markHierarchyIncomplete();
      }
    }
  }

  private static int[] calculateSizes(ClassSymbol[] classSymbols) {
    int[] sizes = new int[classSymbols.length];
    for (ClassSymbol subType : classSymbols) {
      for (ClassSymbol superType : subType.rawSuperClasses()) {
        int superTypeId = superType.myClassAnchor.myId;
        sizes[superTypeId] += 1;
      }
    }
    return sizes;
  }

  private int subtypeStart(int nameId) {
    return mySubtypeStarts[nameId];
  }

  private int subtypeEnd(int nameId) {
    return (nameId + 1 >= mySubtypeStarts.length) ? mySubtypes.length : mySubtypeStarts[nameId + 1];
  }

  private StubClassAnchor forPsiClass(int fileId, PsiClass psiClass) {
    StubClassAnchor anchor = getFirst(fileId, myClassAnchorsByFileIds);
    if (anchor == null) {
      return null;
    }
    int id = anchor.myId;
    while (id < myClassAnchors.length) {
      StubClassAnchor candidate = myClassAnchors[id];
      if (candidate.myFileId != fileId)
        return null;
      if (psiClass.isEquivalentTo(candidate.retrieveClass(psiClass.getProject())))
        return candidate;
      id++;
    }
    return null;
  }

  private static StubClassAnchor getFirst(int fileId, StubClassAnchor[] byFileIds) {
    int lo = 0;
    int hi = byFileIds.length - 1;
    while (lo <= hi) {
      int mid = lo + (hi - lo) / 2;
      if      (fileId < byFileIds[mid].myFileId) hi = mid - 1;
      else if (fileId > byFileIds[mid].myFileId) lo = mid + 1;
      else return byFileIds[mid];
    }
    return null;
  }
}
