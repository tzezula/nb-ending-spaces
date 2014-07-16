/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.editor.emptyspaces;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.Pair;

/**
 *
 * @author Tomas Zezula
 */
public final class SpacesHint implements PropertyChangeListener, CaretListener {

    private static SpacesHint instance;

    private JTextComponent active;

    private SpacesHint() {
    }

    public void start() {
        EditorRegistry.addPropertyChangeListener(this);
        this.propertyChange(new PropertyChangeEvent(this, EditorRegistry.FOCUS_GAINED_PROPERTY, null, null));
    }

    @Override
    public void propertyChange(final PropertyChangeEvent evt) {
        if (EditorRegistry.FOCUS_GAINED_PROPERTY.equals(evt.getPropertyName())) {
            synchronized (this) {
                if (active != null) {
                    final Document doc = active.getDocument();
                    if (doc != null) {
                        HintsController.setErrors(
                            doc,
                            this.getClass().getSimpleName(),
                            Collections.<ErrorDescription>emptySet());
                    }
                    active.removeCaretListener(this);
                }
                active = EditorRegistry.lastFocusedComponent();
                if (active != null) {
                    active.addCaretListener(this);
                }
            }
        }
    }

    @Override
    public void caretUpdate(final CaretEvent e) {
        final int pos = e.getDot();
        final StyledDocument doc = getActiveDocument();
        if (doc != null) {
            final Collection<ErrorDescription> hints = new ArrayList<ErrorDescription>();
            final int[] se = new int[]{-1,-1,-1};
            doc.render(new Runnable() {
                public void run() {
                    final int line = NbDocument.findLineNumber(doc, pos);
                    final Element root = NbDocument.findLineRootElement(doc);
                    final Element lineElement = root.getElement(line);
                    se[1] = lineElement.getStartOffset();
                    se[2] = lineElement.getEndOffset();
                    se[0] = line;
                }
            });
            if (se[0] != -1) {
                final OffsetsBag bag = (OffsetsBag) doc.getProperty(SpacesFactory.ATTR_BAG);
                if (bag != null) {
                    final HighlightsSequence seq = bag.getHighlights(se[1], se[2]);
                    if (seq.moveNext()) {
                        final int start = seq.getStartOffset();
                        final int end = seq.getEndOffset();
                        hints.add(
                            ErrorDescriptionFactory.createErrorDescription(
                                Severity.HINT,
                                NbBundle.getMessage(SpacesHint.class, "MSG_EmptySpaces"),
                                Arrays.<Fix>asList(
                                    new RemoveFromLineFix(doc,start,end,pos),
                                    new RemoveAllFix(doc,bag,start,end,pos)),
                                doc,
                                se[0]+1));
                    }
                }
            }
            HintsController.setErrors(
                doc,
                this.getClass().getSimpleName(),
                hints);
        }
    }

    private synchronized StyledDocument getActiveDocument() {
        if (active == null) {
            return null;
        }
        final Document doc = active.getDocument();
        return doc instanceof StyledDocument ? (StyledDocument) doc : null;
    }


    public static synchronized SpacesHint getDefault() {
        if (instance == null) {
            instance = new SpacesHint();
        }
        return instance;
    }

    private static class RemoveFromLineFix implements Fix {
        private final StyledDocument doc;
        private final int start;
        private final int end;
        private final int pos;

        private RemoveFromLineFix (
                final StyledDocument doc,
                final int start,
                final int end,
                final int pos) {
            this.doc = doc;
            this.start = start;
            this.end = end;
            this.pos = pos;
        }

        public String getText() {
            return NbBundle.getMessage(SpacesHint.class, "MSG_RemoveFromLine");
        }

        public ChangeInfo implement() throws Exception {
            NbDocument.runAtomicAsUser(doc, new Runnable() {
                @Override
                public void run() {
                    try {
                        doc.remove(start, end-start);
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            });
            final Position position = doc.createPosition(
                pos >= start && pos <=end ? start : pos);
            return new ChangeInfo(position,position);
        }
    }

    private static class RemoveAllFix implements Fix {

        private final StyledDocument doc;
        private final OffsetsBag bag;
        private final int start;
        private final int end;
        private final int pos;

        private RemoveAllFix(
            final StyledDocument doc,
            final OffsetsBag bag,
            final int start,
            final int end,
            final int pos) {
            this.doc = doc;
            this.bag = bag;
            this.start = start;
            this.end = end;
            this.pos = pos;
        }

        public String getText() {
            return NbBundle.getMessage(SpacesHint.class, "MSG_RemoveAll");
        }

        public ChangeInfo implement() throws Exception {
            NbDocument.runAtomicAsUser(doc, new Runnable(){
                @Override
                public void run() {
                    final HighlightsSequence seq = bag.getHighlights(0, doc.getLength());
                    final Deque<Pair<Integer,Integer>> spaces = new ArrayDeque<Pair<Integer, Integer>>();
                    while (seq.moveNext()) {
                        final int start = seq.getStartOffset();
                        final int end = seq.getEndOffset();
                        spaces.offer(Pair.<Integer,Integer>of(start,end));
                    }
                    try {
                        while (!spaces.isEmpty()) {
                            final Pair<Integer,Integer> bounds = spaces.removeLast();
                            final int start = bounds.first();
                            final int end = bounds.second();
                            doc.remove(start, end-start);
                        }
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            });
            return null;
        }
    }
}
