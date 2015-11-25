/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.netbeans.modules.editor.emptyspaces;

import java.awt.Color;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.document.EditorDocumentUtils;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.text.NbDocument;
import org.openide.util.*;

/**
 *
 * @author Tomas Zezula
 */
public class SpacesFactory implements HighlightsLayerFactory, DocumentListener {

    private static final String LAYER_NAME = "EmptySpaces"; //NOI18N
    private static final String ATTR_COLOR = "esl-color";   //NOI18N
    static final String ATTR_BAG = "esl-bag";               //NOI18N

    public HighlightsLayer[] createLayers(final Context cntxt) {
        final Document doc = cntxt.getDocument();
        final JTextComponent comp = cntxt.getComponent();
        final FileObject file = EditorDocumentUtils.getFileObject(doc);
        final FontColorSettings settings = MimeLookup
                .getLookup(file != null ? MimePath.get(file.getMIMEType()) : MimePath.EMPTY)
                .lookup(FontColorSettings.class);
        AttributeSet color = settings.getFontColors("trailing-whitespace"); //NOI18N
        if (color == null) {
            color = defaultColor(comp);
        }
        final HighlightsLayer layer = HighlightsLayer.create(LAYER_NAME, ZOrder.TOP_RACK, true, compute(getContainer(doc, color),doc));   //NOI18N
        return new HighlightsLayer[] {layer};
    }

    private OffsetsBag getContainer(final Document doc, AttributeSet color) {
        OffsetsBag curBag = (OffsetsBag) doc.getProperty(ATTR_BAG);
        if (curBag == null) {
            curBag = new OffsetsBag(doc);
            doc.addDocumentListener(WeakListeners.document(this, doc));
            doc.putProperty(ATTR_COLOR, color);
            doc.putProperty(ATTR_BAG, curBag);
        }
        return curBag;
    }

    private static AttributeSet defaultColor(final JTextComponent comp) {
        Color c = comp.getBackground();
        if (c == null) {
            c = Color.WHITE;
        }
        return AttributesUtilities.createImmutable(StyleConstants.Background, isDark(c) ? c.brighter() : c.darker());
    }

    private static boolean isDark (final Color c) {
        int res = (c.getBlue() + c.getGreen() + c.getRed())/3;
        return res < 128;
    }

    private static AttributeSet getAttributes(final Document doc) {
        return (AttributeSet) doc.getProperty(ATTR_COLOR);
    }

    private OffsetsBag compute(final OffsetsBag bag, final Document doc) {
        final OffsetsBag[] result = new OffsetsBag[1];
        doc.render(new Runnable() {
            public void run() {
                try {
                    final String text = doc.getText(0, doc.getLength());
                    result[0] = compute(bag, text, 0, getAttributes(doc));
                } catch (BadLocationException ex) {
                    Logger.getLogger(SpacesFactory.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        assert result[0] != null;
        return result[0];
    }

    private OffsetsBag compute (final OffsetsBag bag, final String text, final int shift, final AttributeSet attrs) {
        int start = -1;
        for (int i=0; i<text.length(); i++) {
            char c = text.charAt(i);
            if (start == -1 && Character.isWhitespace(c) && c != '\n') {
                start = i;
            } else if (start > -1 && c == '\n') {
                bag.addHighlight(shift+start, shift+i, attrs);
                start = -1;
            } else if (!Character.isWhitespace(c)){
                start = -1;
            }
        }
        if   (start != -1) {
            bag.addHighlight(shift+start, shift+text.length(), attrs);
        }
        return bag;
    }

    public void insertUpdate(DocumentEvent e) {
        handleChange(e);
    }

    public void removeUpdate(DocumentEvent e) {
        handleChange(e);
    }

    public void changedUpdate(DocumentEvent e) {
        handleChange(e);
    }

    private void handleChange(final DocumentEvent e) {
        final StyledDocument doc = (StyledDocument) e.getDocument();
        final OffsetsBag curBag = (OffsetsBag) doc.getProperty(ATTR_BAG);
        doc.render(new Runnable() {
            public void run() {
                try {
                    final int start = e.getOffset();
                    int ln = NbDocument.findLineNumber(doc, start);
                    final int lstart = NbDocument.findLineOffset(doc, ln);
                    final int end = start + e.getLength();
                    ln = NbDocument.findLineNumber(doc, end);
                    int lend;
                    try {
                        lend = NbDocument.findLineOffset(doc, ln+1);
                    } catch (IndexOutOfBoundsException ex) {
                        lend = doc.getLength();
                    }
                    final int llength = lend - lstart;
                    curBag.removeHighlights(lstart, lend, true);
                    final String text = doc.getText(lstart, llength);
                    compute(curBag, text, lstart, getAttributes(doc));
                } catch (BadLocationException ex) {
                    Logger.getLogger(SpacesFactory.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }
}
