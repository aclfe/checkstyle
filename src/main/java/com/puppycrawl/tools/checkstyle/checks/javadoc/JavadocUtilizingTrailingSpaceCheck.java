///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2026 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
///////////////////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle.checks.javadoc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.puppycrawl.tools.checkstyle.FileStatefulCheck;
import com.puppycrawl.tools.checkstyle.api.DetailNode;
import com.puppycrawl.tools.checkstyle.api.JavadocCommentsTokenTypes;

/**
 * <div>
 * Checks that Javadoc lines efficiently utilize the available horizontal space.
 * </div>
 *
 * <p>
 * The check focuses strictly on line length:
 * </p>
 * <ul>
 * <li>It flags lines that break prematurely before the configured
 * {@code lineLimit} when content from the following line could have been moved up.</li>
 * <li>It flags lines that exceed {@code lineLimit}, except when the line contains
 * only an unbreakable element such as a Javadoc tag or URL that starts the line.</li>
 * </ul>
 */
@FileStatefulCheck
public class JavadocUtilizingTrailingSpaceCheck extends AbstractJavadocCheck {

    /**
     * Message key for too short Javadoc line.
     */
    public static final String MSG_TOO_SHORT = "javadoc.utilizing.trailing.space.too.short";

    /**
     * Message key for too long Javadoc line.
     */
    public static final String MSG_TOO_LONG = "javadoc.utilizing.trailing.space.too.long";

    /**
     * Pattern that recognizes URLs.
     */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://|ftp://");

    /**
     * Default maximum line length.
     */
    private static final int DEFAULT_LINE_LIMIT = 80;

    /**
     * Configurable line length limit.
     */
    private int lineLimit = DEFAULT_LINE_LIMIT;

    /**
     * Whether we are currently inside a pre block.
     */
    private boolean insidePreBlock;

    /**
     * Collects logical Javadoc lines for the current Javadoc comment.
     */
    private final List<JavadocLine> lines = new ArrayList<>();

    /**
     * Current line being built.
     */
    private JavadocLine currentLine;

    @Override
    public int[] getDefaultJavadocTokens() {
        return new int[] {
            JavadocCommentsTokenTypes.JAVADOC_CONTENT,
        };
    }

    @Override
    public int[] getRequiredJavadocTokens() {
        return getAcceptableJavadocTokens();
    }

    /**
     * Setter to specify the line length limit.
     *
     * @param limit the maximum length to target for Javadoc lines
     */
    public void setLineLimit(int limit) {
        lineLimit = limit;
    }

    @Override
    public void beginJavadocTree(DetailNode rootAst) {
        insidePreBlock = false;
        lines.clear();
        currentLine = null;
        processJavadoc(rootAst);
    }

    @Override
    public void finishJavadocTree(DetailNode rootAst) {
        finalizeCurrentLine();

        for (int i = 0; i < lines.size(); i++) {
            final JavadocLine line = lines.get(i);

            if (!line.shouldBeChecked || !line.hasContent) {
                continue;
            }

            final int length = line.length;

            if (length > lineLimit) {
                if (!line.startsWithUnbreakable) {
                    log(line.lineNumber, MSG_TOO_LONG, lineLimit, length);
                }
            } else if (canPullFromNextLine(i, length)) {
                log(line.lineNumber, MSG_TOO_SHORT, lineLimit);
            }
        }
    }

    @Override
    public void visitJavadocToken(DetailNode ast) {
        // No-op: processing is done in beginJavadocTree/finishJavadocTree.
    }

    /**
     * Processes the entire Javadoc tree to build line information.
     *
     * @param root the root node
     */
    private void processJavadoc(DetailNode root) {
        DetailNode child = root.getFirstChild();
        while (child != null) {
            processNode(child);
            child = child.getNextSibling();
        }
    }

    /**
     * Processes a single node in the Javadoc tree.
     *
     * @param node the node to process
     */
    private void processNode(DetailNode node) {
        final int type = node.getType();

        switch (type) {
            case JavadocCommentsTokenTypes.NEWLINE:
                finalizeCurrentLine();
                break;

            case JavadocCommentsTokenTypes.LEADING_ASTERISK:
                break;

            case JavadocCommentsTokenTypes.TEXT:
                processText(node);
                break;

            case JavadocCommentsTokenTypes.TAG_NAME:
            case JavadocCommentsTokenTypes.PARAMETER_NAME:
                processTagContent(node);
                break;

            case JavadocCommentsTokenTypes.JAVADOC_INLINE_TAG:
                processInlineTag(node);
                break;

            case JavadocCommentsTokenTypes.HTML_ELEMENT:
                processHtmlElement(node);
                break;

            case JavadocCommentsTokenTypes.JAVADOC_BLOCK_TAG:
                processBlockTag(node);
                break;

            default:
                processChildren(node);
                break;
        }
    }

    /**
     * Processes children of a node.
     *
     * @param node the parent node
     */
    private void processChildren(DetailNode node) {
        DetailNode child = node.getFirstChild();
        while (child != null) {
            processNode(child);
            child = child.getNextSibling();
        }
    }

    /**
     * Processes a block tag like @param, @return.
     *
     * @param node the block tag node
     */
    private void processBlockTag(DetailNode node) {
        finalizeCurrentLine();

        final int lineNo = node.getLineNumber();
        ensureCurrentLine(lineNo);

        currentLine.isBlockTagStart = true;

        processChildren(node);

        if (!currentLine.hasContentAfterUnbreakable) {
            currentLine.shouldBeChecked = false;
        }
    }

    /**
     * Finalizes the current line and adds it to the list.
     */
    private void finalizeCurrentLine() {
        if (currentLine != null && currentLine.length > 0) {
            lines.add(currentLine);
        }
        currentLine = null;
    }

    /**
     * Ensures a current line exists for the given line number.
     *
     * @param lineNumber the line number
     */
    private void ensureCurrentLine(int lineNumber) {
        if (currentLine == null || currentLine.lineNumber != lineNumber) {
            if (currentLine != null) {
                lines.add(currentLine);
            }
            currentLine = new JavadocLine(lineNumber);
            currentLine.shouldBeChecked = !insidePreBlock;
        }
    }

    /**
     * Processes a TAG_NAME or PARAMETER_NAME node within a block tag.
     *
     * @param node the node
     */
    private void processTagContent(DetailNode node) {
        final String text = node.getText();
        final int lineNo = node.getLineNumber();
        final int colNo = node.getColumnNumber();

        ensureCurrentLine(lineNo);

        final int endCol = colNo + text.length();
        if (endCol > currentLine.length) {
            currentLine.length = endCol;
        }

        if (!currentLine.hasContent) {
            currentLine.hasContent = true;
            currentLine.startsWithUnbreakable = true;
        }
    }

    /**
     * Processes a TEXT node.
     *
     * @param node the TEXT node
     */
    private void processText(DetailNode node) {
        final String text = node.getText();
        final int lineNo = node.getLineNumber();
        final int colNo = node.getColumnNumber();

        ensureCurrentLine(lineNo);

        final String trimmed = text.trim();
        if (!trimmed.isEmpty()) {
            final int endCol = colNo + text.length();
            if (endCol > currentLine.length) {
                currentLine.length = endCol;
            }

            if (!currentLine.hasContent) {
                currentLine.hasContent = true;

                if (URL_PATTERN.matcher(trimmed).lookingAt()) {
                    currentLine.startsWithUnbreakable = true;
                    currentLine.firstWord = trimmed;
                } else {
                    extractFirstWord(trimmed);
                }
            } else {
                if (currentLine.startsWithUnbreakable) {
                    currentLine.hasContentAfterUnbreakable = true;
                }
                if (currentLine.firstWord == null) {
                    extractFirstWord(trimmed);
                }
            }
        } else if (!text.isEmpty()) {
            final int endCol = colNo + text.length();
            if (endCol > currentLine.length) {
                currentLine.length = endCol;
            }
        }
    }

    /**
     * Extracts and sets the first word from text for pull calculations.
     *
     * @param trimmedText trimmed text to extract from
     */
    private void extractFirstWord(String trimmedText) {
        if (currentLine.firstWord == null) {
            final String[] words = trimmedText.split("\\s+", 2);
            if (words.length > 0 && !words[0].isEmpty()) {
                currentLine.firstWord = words[0];
            }
        }
    }

    /**
     * Processes an inline tag like {@code {@link ...}}.
     *
     * @param node the inline tag node
     */
    private void processInlineTag(DetailNode node) {
        final int lineNo = node.getLineNumber();
        final int colNo = node.getColumnNumber();

        ensureCurrentLine(lineNo);

        final String tagText = getFullNodeText(node);
        final int endCol = colNo + tagText.length();

        if (endCol > currentLine.length) {
            currentLine.length = endCol;
        }

        if (!currentLine.hasContent) {
            currentLine.startsWithUnbreakable = true;
            currentLine.firstWord = tagText;
        } else {
            currentLine.hasContentAfterUnbreakable = true;
        }

        currentLine.hasContent = true;
    }

    /**
     * Processes an HTML element.
     *
     * @param node the HTML element node
     */
    private void processHtmlElement(DetailNode node) {
        final String tagName = getHtmlTagName(node);
        final int lineNo = node.getLineNumber();

        if ("pre".equalsIgnoreCase(tagName)) {
            insidePreBlock = !insidePreBlock;
        }

        ensureCurrentLine(lineNo);

        if (!currentLine.hasContent && isStructuralTag(tagName)) {
            currentLine.shouldBeChecked = false;
        }

        final String visibleText = getVisibleHtmlText(node);
        if (!visibleText.isEmpty()) {
            final int colNo = node.getColumnNumber();
            final int endCol = colNo + getFullNodeText(node).length();

            if (endCol > currentLine.length) {
                currentLine.length = endCol;
            }

            if (!isStructuralTag(tagName)) {
                currentLine.hasContent = true;
                extractFirstWord(visibleText.trim());
            }
        }
    }

    /**
     * Checks if we can pull content from the next line.
     *
     * @param currentIndex current line index
     * @param currentLength current line length
     * @return true if content can be pulled
     */
    private boolean canPullFromNextLine(int currentIndex, int currentLength) {
        for (int i = currentIndex + 1; i < lines.size(); i++) {
            final JavadocLine nextLine = lines.get(i);

            if (!nextLine.hasContent) {
                continue;
            }

            if (nextLine.isBlockTagStart) {
                return false;
            }

            if (nextLine.firstWord != null) {
                final int newLength = currentLength + 1 + nextLine.firstWord.length();
                return newLength <= lineLimit;
            }

            return false;
        }

        return false;
    }

    /**
     * Gets the full text of a node and its children.
     *
     * @param node the node
     * @return the full text
     */
    private static String getFullNodeText(DetailNode node) {
        final StringBuilder sb = new StringBuilder();
        collectNodeText(node, sb);
        return sb.toString();
    }

    /**
     * Recursively collects text from a node.
     *
     * @param node the node
     * @param sb the string builder
     */
    private static void collectNodeText(DetailNode node, StringBuilder sb) {
        if (node.getFirstChild() == null) {
            sb.append(node.getText());
        } else {
            DetailNode child = node.getFirstChild();
            while (child != null) {
                collectNodeText(child, sb);
                child = child.getNextSibling();
            }
        }
    }

    /**
     * Gets the HTML tag name from an element.
     *
     * @param node the HTML element node
     * @return the tag name
     */
    private static String getHtmlTagName(DetailNode node) {
        DetailNode child = node.getFirstChild();
        while (child != null) {
            final int type = child.getType();
            if (type == JavadocCommentsTokenTypes.HTML_TAG_START
                    || type == JavadocCommentsTokenTypes.HTML_TAG_END) {
                DetailNode tagChild = child.getFirstChild();
                while (tagChild != null) {
                    if (tagChild.getType() == JavadocCommentsTokenTypes.TAG_NAME) {
                        return tagChild.getText();
                    }
                    tagChild = tagChild.getNextSibling();
                }
            }
            child = child.getNextSibling();
        }
        return "";
    }

    /**
     * Checks if a tag is structural (should be ignored).
     *
     * @param tagName the tag name
     * @return true if structural
     */
    private static boolean isStructuralTag(String tagName) {
        return "p".equalsIgnoreCase(tagName)
                || "div".equalsIgnoreCase(tagName)
                || "ul".equalsIgnoreCase(tagName)
                || "ol".equalsIgnoreCase(tagName)
                || "li".equalsIgnoreCase(tagName)
                || "pre".equalsIgnoreCase(tagName)
                || "table".equalsIgnoreCase(tagName)
                || "tr".equalsIgnoreCase(tagName)
                || "td".equalsIgnoreCase(tagName)
                || "th".equalsIgnoreCase(tagName)
                || "blockquote".equalsIgnoreCase(tagName)
                || "h1".equalsIgnoreCase(tagName)
                || "h2".equalsIgnoreCase(tagName)
                || "h3".equalsIgnoreCase(tagName)
                || "h4".equalsIgnoreCase(tagName)
                || "h5".equalsIgnoreCase(tagName)
                || "h6".equalsIgnoreCase(tagName);
    }

    /**
     * Gets visible text from an HTML element.
     *
     * @param node the HTML element
     * @return the visible text
     */
    private static String getVisibleHtmlText(DetailNode node) {
        final StringBuilder sb = new StringBuilder();
        collectVisibleText(node, sb);
        return sb.toString();
    }

    /**
     * Recursively collects visible text.
     *
     * @param node the node
     * @param sb the string builder
     */
    private static void collectVisibleText(DetailNode node, StringBuilder sb) {
        if (node.getType() == JavadocCommentsTokenTypes.TEXT) {
            sb.append(node.getText());
        } else {
            DetailNode child = node.getFirstChild();
            while (child != null) {
                collectVisibleText(child, sb);
                child = child.getNextSibling();
            }
        }
    }

    /**
     * Represents a logical line in a Javadoc comment.
     */
    private static final class JavadocLine {
        /** The source line number. */
        private final int lineNumber;
        /** The length of the line (end column). */
        private int length;
        /** Whether the line has meaningful content. */
        private boolean hasContent;
        /** Whether the line should be checked for violations. */
        private boolean shouldBeChecked = true;
        /** Whether the line starts with an unbreakable element. */
        private boolean startsWithUnbreakable;
        /** Whether there is content after the unbreakable element. */
        private boolean hasContentAfterUnbreakable;
        /** The first word of the line (for pull calculations). */
        private String firstWord;
        /** Whether this line starts a new block tag. */
        private boolean isBlockTagStart;

        /**
         * Creates a JavadocLine.
         *
         * @param lineNumber the source line number
         */
        private JavadocLine(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }
}
