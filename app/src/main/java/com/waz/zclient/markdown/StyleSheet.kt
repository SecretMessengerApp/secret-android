/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.markdown

import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Paint
import com.jsy.common.acts.OpenUrlActivity
import com.waz.zclient.R
import com.waz.zclient.markdown.spans.GroupSpan
import com.waz.zclient.markdown.spans.commonmark.*
import com.waz.zclient.markdown.utils.isOuterMost
import com.jsy.res.utils.ViewUtils
import org.commonmark.node.*

/**
 * An instance of StyleSheet is used to define the text formatting styles to apply to each
 * markdown unit. The style sheet is queried by a renderer as it traverses the abstract
 * syntax tree constructed from a marked down document.
 */
class StyleSheet {

    /**
     * The base font size (in pixels) used for all markdown units unless otherwise specified.
     */
    var baseFontSize: Int = 17

    /**
     * The base font color used for all markdown units unless otherwise specified.
     */
    var baseFontColor: Int = Color.BLACK

    /**
     * The amount of spacing (in points) before a paragraph.
     */
    var paragraphSpacingBefore: Int = 6

    /**
     * The amount of spacing (in points) after a paragraph.
     */
    var paragraphSpacingAfter: Int = 6

    /**
     * The relative font size multiplers (values) for the various header levels (keys).
     * The header values range from 1 to 6.
     */
    var headingSizeMultipliers = mapOf(1 to 1.7f, 2 to 1.5f, 3 to 1.25f, 4 to 1.25f, 5 to 1.25f, 6 to 1.25f)

    /**
     * The color of a quote text.
     */
    var quoteColor: Int = Color.GRAY

    /**
     * The color of a quote stripe.
     */
    var quoteStripeColor: Int = Color.GRAY

    /**
     * The width (in points) of the quote stripe.
     */
    var quoteStripeWidth: Int = 2

    /**
     * The gap width (in points) between the quote stripe and the quote content text.
     */
    var quoteGapWidth: Int = 16

    /**
     * The amount of spacing (in points) before a quote.
     */
    var quoteSpacingBefore: Int = 16

    /**
     * The amount of spacing (in points) after a quote.
     */
    var quoteSpacingAfter: Int = 16

    /**
     * The color of list prefixes
     */
    var listPrefixColor: Int = Color.GRAY

    /**
     * The gap width (in points) between the list prefix and the list content text.
     */
    var listPrefixGapWidth: Int = 8

    /**
     * The amount of spacing (in points) before a list item.
     */
    var listItemSpacingBefore: Int = 4

    /**
     * The amount of spacing (in points) after a list item.
     */
    var listItemSpacingAfter: Int = 4

    /**
     * The color of all monospace code text.
     */
    var codeColor: Int = Color.GRAY

    /**
     * The indentation (in points) from the leading margin of all code blocks.
     */
    var codeBlockIndentation: Int = 0

    /**
     * The color of links.
     */
    var linkColor: Int = Color.BLUE

    /**
     * The handler called when a markdown link is tapped.
     */
    var onClickLink: (String) -> Unit = { }

    /**
     * The standard width of the leading margin of a list item, which is equal to 3 monospace
     * digits plus `listPrefixGapWidth`. This locates where the list content should begin
     * (including any wrapped content).
     */
    val listItemContentMargin: Int get() = 3 * maxDigitWidth.toInt() + listPrefixGapWidth

    val screenDensity: Float
    val maxDigitWidth: Float

    init {
        val p = Paint()
        p.textSize = baseFontSize.toFloat()
        screenDensity = Resources.getSystem().displayMetrics.density
        maxDigitWidth = "0123456789".toCharArray().map { c -> p.measureText("$c") }.max()!! * screenDensity
    }

    private val Int.scaled: Int get() = (this * screenDensity).toInt()

    /**
     * Configures the handler when a markdown link is clicked by presenting a confirmation
     * dialog from the given context.
     */
    fun configureLinkHandler(context: Context) {
        onClickLink = { url: String ->
            // show dialog to confirm if url should be open
            ViewUtils.showAlertDialog(context,
                context.getString(R.string.markdown_link_dialog_title),
                context.getString(R.string.markdown_link_dialog_message, url),
                context.getString(R.string.markdown_link_dialog_confirmation),
                context.getString(R.string.markdown_link_dialog_cancel),
                DialogInterface.OnClickListener { _, _ ->
                    OpenUrlActivity.startSelf(context, url)
                    /*val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(context, intent, null)*/
                },
                null
            )
        }
    }

    /**
     * Returns the configured `GroupSpan` for the given node, depending on the node type.
     */
    fun spanFor(node: Node): GroupSpan {
        when (node) {
            is Heading -> {
                return HeadingSpan(
                    node.level,
                    headingSizeMultipliers[node.level] ?: 1f,
                    paragraphSpacingBefore,
                    paragraphSpacingAfter
                )
            }
            is Paragraph -> {
                return if (node.isOuterMost)
                    ParagraphSpan(paragraphSpacingBefore, paragraphSpacingAfter)
                else
                    ParagraphSpan(0, 0)
            }
            is BlockQuote -> {
                return BlockQuoteSpan(
                    quoteColor,
                    quoteStripeColor,
                    quoteStripeWidth,
                    quoteGapWidth,
                    quoteSpacingBefore,
                    quoteSpacingAfter,
                    screenDensity
                )
            }
            is OrderedList -> {
                return OrderedListSpan(node.startNumber)
            }
            is BulletList -> {
                return BulletListSpan(node.bulletMarker)
            }
            is ListItem -> {
                return ListItemSpan()
            }
            is FencedCodeBlock -> {
                return FencedCodeBlockSpan(
                    codeColor,
                    codeBlockIndentation.scaled,
                    paragraphSpacingBefore,
                    paragraphSpacingAfter
                )
            }
            is IndentedCodeBlock -> {
                return IndentedCodeBlockSpan(
                    codeColor,
                    codeBlockIndentation.scaled,
                    paragraphSpacingBefore,
                    paragraphSpacingAfter
                )
            }
            is HtmlBlock -> {
                return HtmlBlockSpan(
                    codeColor,
                    codeBlockIndentation.scaled,
                    paragraphSpacingBefore,
                    paragraphSpacingAfter
                )
            }
            is Link -> {
                return LinkSpan(node.destination, linkColor, onClickLink)
            }
            is Image -> {
                return ImageSpan(node.destination, linkColor, onClickLink)
            }
            is Emphasis -> {
                return EmphasisSpan()
            }
            is StrongEmphasis -> {
                return StrongEmphasisSpan()
            }
            is Code -> {
                return CodeSpan(codeColor)
            }
            is HtmlInline -> {
                return HtmlInlineSpan(codeColor)
            }
            is Text -> {
                return TextSpan(baseFontSize, baseFontColor)
            }
            is SoftLineBreak -> {
                return SoftLineBreakSpan()
            }
            is HardLineBreak -> {
                return HardLineBreakSpan()
            }
            is ThematicBreak -> {
                return ThematicBreakSpan()
            }
            else -> {
                return DocumentSpan()
            }
        }
    }

}
