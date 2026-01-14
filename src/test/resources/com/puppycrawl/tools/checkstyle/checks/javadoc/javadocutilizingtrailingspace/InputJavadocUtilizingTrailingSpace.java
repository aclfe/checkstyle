/*
JavadocUtilizingTrailingSpace
lineLimit = (default)80
violateExecutionOnNonTightHtml = (default)false

*/

package com.puppycrawl.tools.checkstyle.checks.javadoc.javadocutilizingtrailingspace;

public class InputJavadocUtilizingTrailingSpace {

    /**
     * <p>
     * This paragraph starts with an HTML tag and is ignored.
     * </p>
     */
    public void htmlStructuralIgnored() { }

    /**
     * @return
     * This line is intentionally short
     * and should be reported as too short.
     */
    public int tooShortWithFollowingContent() {
        return 0;
    }

    /**
     * {@link com.very.long.package.name.that.exceeds.limit.CompanyStatus}
     */
    public void longInlineTagAtStartAllowed() { }

    /**
     * This line has an inline {@link com.very.long.package.name.that.exceeds.limit.CompanyStatus}
     * reference in the middle and should be too long.
     */
    public void longInlineTagInMiddleViolation() { }

    /**
     * This line is wrapped correctly
     * {@link com.very.long.package.name.that.exceeds.limit.CompanyStatus}
     */
    public void fixedLongInlineTag() { }

    /**
     * <pre>
     * This is inside a pre block and should be ignored even if the line is very very very long.
     * </pre>
     */
    public void preBlockIgnored() { }

    /**
     * @param value
     * a parameter description that is short
     * but followed by another content line.
     */
    public void blockTagValueTooShort(int value) { }

    /**
     * http://example.com/this/is/a/very/long/url/that/exceeds/the/configured/limit
     */
    public void longUrlAtStartAllowed() { }

    /**
     * See the documentation at http://example.com/this/is/a/very/long/url/that/exceeds/the/configured/limit
     * for more details.
     */
    public void longUrlInMiddleViolation() { }
}

