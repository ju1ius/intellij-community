// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.CommonBundle;
import com.intellij.ide.DataManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actions.IndentSelectionAction;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;

public class EditorTypingAndNavigationUndoTest extends EditorUndoTestCase {
  public void testUndoTypeIn() {
    typeInText("test");
    checkEditorState("test", 4, 0, 0);
    undoFirstEditor();
    checkEditorState("", 0, 0, 0);
    redoFirstEditor();
    checkEditorState("test", 4, 0, 0);
  }

  public void testMoveCaretUndo() {
    typeInText("test");
    checkEditorState("test", 4, 0, 0);
    moveCaret(4, LEFT, false);
    checkEditorState("test", 0, 0, 0);
    undoFirstEditor();
    checkEditorState("test", 4, 0, 0);
    undoFirstEditor();

    checkEditorState("", 0, 0, 0);

    redoFirstEditor();
    checkEditorState("test", 4, 0, 0);
  }

  public void testMoveCaretUndoWithSelection() {
    typeInText("test");
    checkEditorState("test", 4, 0, 0);
    moveCaret(4, LEFT, true);
    checkEditorState("test", 0, 0, 4);
    undoFirstEditor();
    checkEditorState("test", 4, 0, 0);

    undoFirstEditor();

    checkEditorState("", 0, 0, 0);

    redoFirstEditor();
    checkEditorState("test", 4, 0, 0);
  }

  public void testMoveOnly() {
    final Document document = getFirstEditor().getDocument();
    ApplicationManager.getApplication().runWriteAction(() -> document.setText("test"));

    moveCaret(4, RIGHT, false);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      moveCaret(2, LEFT, false);
      checkEditorState("test", 2, 0, 0);
      typeInText("test");
    });
    checkEditorState("tetestst", 6, 0, 0);
    undoFirstEditor();
    checkEditorState("test", 4, 0, 0);
    assertUndoInFirstEditorNotAvailable();
  }

  public void testEnter() {
    typeInText("abc");
    checkEditorState("abc", 3, 0, 0);
    enter();
    checkEditorState("abc\n", 4, 0, 0);
    typeInText("def");
    checkEditorState("abc\ndef", 7, 0, 0);
    enter();
    checkEditorState("abc\ndef\n", 8, 0, 0);
    typeInText("eee");
    checkEditorState("abc\ndef\neee", 11, 0, 0);

    undoFirstEditor();
    checkEditorState("abc\ndef\n", 8, 0, 0);

    undoFirstEditor();
    checkEditorState("abc\ndef", 7, 0, 0);

    undoFirstEditor();
    checkEditorState("abc\n", 4, 0, 0);

    undoFirstEditor();
    checkEditorState("abc", 3, 0, 0);

    undoFirstEditor();
    checkEditorState("", 0, 0, 0);
  }

  public void testRedoClearedAfterChange() {
    typeInText("test");
    enter();
    typeInText("test");
    undoFirstEditor();
    typeInText("test");
    assertRedoInFirstEditorNotAvailable();
  }

  public void testNavigationContextWasNotChanged() {

    typeInText("test");
    moveCaret(LEFT, false);
    moveCaret(RIGHT, false);

    undoFirstEditor();

    checkEditorState("", 0, 0, 0);
  }

  public void testNavigationActionsOutsideCommand() {
    typeInText("test");
    moveCaretOutsideCommand(2);
    undoFirstEditor();
    checkEditorState("test", 4, 0, 0);
  }

  public void testBeforeContext() {
    typeInText("testtesttest");
    moveCaret(4, LEFT, false);
    moveCaret(4, LEFT, false);

    typeInText("1111");

    undoFirstEditor();

    checkEditorState("testtesttest", 4, 0, 0);
  }

  public void testStripTrailingSpacesNoCommand() {
    typeInText("    test");
    enter();
    stripTrailingSpaces(false);
    undoFirstEditor();
    checkEditorState("    test", 8, 0, 0);
  }

  public void testMoveCaretNotInsideCommand() {
    typeInText("12345");
    enter();
    moveCaretOutsideCommand(3);
    typeInText("67890");
    undoFirstEditor();
    checkEditorText("12345\n", getFirstEditor());
  }

  public void testUndoInEditorsIsAvailableAfterRootChanges() throws IOException {
    typeInText("test");
    enter();
    typeInText("test");
    undoFirstEditor();

    assertUndoInFirstEditorIsAvailable();
    assertRedoInFirstEditorIsAvailable();

    addContentRoot();

    assertUndoInFirstEditorIsAvailable();
    assertRedoInFirstEditorIsAvailable();
  }

  public void testUndoRedoRestoredModificationStamp() {
    long beforeType = getFirstEditor().getDocument().getModificationStamp();
    typeInText("test");
    long afterType = getFirstEditor().getDocument().getModificationStamp();

    undoFirstEditor();
    assertEquals(beforeType, getFirstEditor().getDocument().getModificationStamp());

    redoFirstEditor();
    assertEquals(afterType, getFirstEditor().getDocument().getModificationStamp());
  }

  public void testUndoRetainsCaretVisible() {
    Editor editor = getFirstEditor();
    EditorTestUtil.setEditorVisibleSize(editor, 10, 3);
    typeInText("a\nb\nc\nd\n");
    executeEditorAction(editor, IdeActions.ACTION_SELECT_ALL);
    executeEditorAction(editor, IdeActions.ACTION_EDITOR_COPY);
    executeEditorAction(editor, IdeActions.ACTION_EDITOR_TEXT_START);
    executeEditorAction(editor, IdeActions.ACTION_EDITOR_PASTE);
    undoFirstEditor();
    VisualPosition caretPosition = editor.getCaretModel().getVisualPosition();
    Point caretXY = editor.visualPositionToXY(caretPosition);
    assertTrue(editor.getScrollingModel().getVisibleAreaOnScrollingFinished().contains(caretXY));
  }

  private void stripTrailingSpaces(boolean startCommand) {
    final Runnable command =
      () -> CommandProcessor.getInstance().runUndoTransparentAction(() -> WriteCommandAction.runWriteCommandAction(null, () -> {
        ((DocumentImpl)getFirstEditor().getDocument()).stripTrailingSpaces(getProject());
      }));
    if (startCommand) {
      executeCommand(command, "Save");
    }
    else {
      command.run();
    }
  }

  public void testEditingView() {
    editView();
    assertUndoNotAvailable(myView);
  }

  private void editView() {
    WriteCommandAction.runWriteCommandAction(null, () -> myView.getDocument().insertString(0, "text"));
  }

  public void testBulkStateChangedOnlyOnce() {
    final String hugeText = StringUtil.repeat(" asdlkjfh lkjsadf kjhasdklfjhalsdkjfh laskdjhf lkajdh\n", 1000);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      getFirstEditor().getDocument().insertString(0, hugeText);
      getFirstEditor().getSelectionModel().setSelection(0, getFirstEditor().getDocument().getTextLength());
    });

    CommandProcessor.getInstance().executeCommand(
      getProject(), () -> new IndentSelectionAction().getHandler()
        .execute(getFirstEditor(), null, DataManager.getInstance().getDataContext(getFirstEditor().getContentComponent())),
      "", this);

    final int[] bulkStarted = new int[1];
    final int[] bulkFinished = new int[1];
    getProject().getMessageBus().connect().subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
      @Override
      public void updateStarted(@NotNull final Document doc) {
        bulkStarted[0]++;
      }

      @Override
      public void updateFinished(@NotNull Document doc) {
        bulkFinished[0]++;
      }
    });

    undoFirstEditor();
    assertEquals(1, bulkStarted[0]);
    assertEquals(1, bulkFinished[0]);
  }

  public void testRestoringStateAfterEditorRecreation() {
    typeInText("abc\n\n\n\n\n\n\n");
    executeEditorAction(getFirstEditor(), IdeActions.ACTION_EDITOR_TEXT_START);
    typeInText(" ");
    executeEditorAction(getFirstEditor(), IdeActions.ACTION_EDITOR_TEXT_END);

    // recreate editor
    Document document = getFirstEditor().getDocument();
    EditorFactory.getInstance().releaseEditor(getFirstEditor());
    myEditors[0] = EditorFactory.getInstance().createEditor(document, myProject);

    UIUtil.dispatchAllInvocationEvents(); // remove references to original editor from event queue
    System.gc(); // make sure weak references to original editor are cleared

    undoFirstEditor();
    checkEditorText(" abc\n\n\n\n\n\n\n");
    undoFirstEditor();
    checkEditorText("abc\n\n\n\n\n\n\n");
  }

  public void testSaneConfirmationMessageForNullCommandName() {
    Ref<String> message = new Ref<>();
    Messages.setTestDialog(new TestDialog() {
      @Override
      public int show(String m) {
        message.set(m);
        return Messages.YES;
      }
    });

    CommandProcessor.getInstance().executeCommand(myProject,
                                                  () -> WriteAction.run(() -> getFirstEditor().getDocument().insertString(0, " ")),
                                                  null, null, UndoConfirmationPolicy.REQUEST_CONFIRMATION);

    undoFirstEditor();
    checkEditorText("");
    assertEquals(CommonBundle.message("undo.command.confirmation.text", ActionsBundle.message("action.undo.description.empty")) + "?",
                 message.get());
    redoFirstEditor();
    checkEditorText(" ");
    assertEquals(CommonBundle.message("redo.command.confirmation.text", ActionsBundle.message("action.redo.description.empty")) + "?",
                 message.get());
  }
}
