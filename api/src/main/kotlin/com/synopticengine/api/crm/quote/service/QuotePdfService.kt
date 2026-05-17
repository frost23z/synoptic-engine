package com.synopticengine.api.crm.quote.service

import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.synopticengine.api.crm.quote.web.QuoteResponse
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class QuotePdfService {
    fun generate(quote: QuoteResponse): ByteArray {
        val out = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 36f, 36f, 54f, 36f)
        PdfWriter.getInstance(document, out)
        document.open()

        val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)
        val headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f)
        val bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10f)

        document.add(Paragraph(quote.title, titleFont).also { it.spacingAfter = 12f })

        quote.expiredAt?.let { document.add(Paragraph("Expires: $it", bodyFont)) }
        quote.terms?.let { document.add(Paragraph("Terms: $it", bodyFont)) }

        val table =
            PdfPTable(5).also { t ->
                t.widthPercentage = 100f
                t.setWidths(floatArrayOf(40f, 15f, 15f, 15f, 15f))
                t.setSpacingBefore(12f)
            }
        listOf("Product", "Qty", "Unit Price", "Discount", "Total").forEach { h ->
            table.addCell(
                PdfPCell(Phrase(h, headerFont)).also { c ->
                    c.horizontalAlignment = Element.ALIGN_CENTER
                },
            )
        }

        quote.items.forEach { item ->
            table.addCell(PdfPCell(Phrase(item.productId?.toString() ?: "-", bodyFont)))
            table.addCell(
                PdfPCell(Phrase(item.quantity.toString(), bodyFont)).also {
                    it.horizontalAlignment = Element.ALIGN_CENTER
                },
            )
            table.addCell(
                PdfPCell(Phrase(item.unitPrice.toPlainString(), bodyFont)).also {
                    it.horizontalAlignment = Element.ALIGN_RIGHT
                },
            )
            table.addCell(
                PdfPCell(Phrase(item.discount.toPlainString(), bodyFont)).also {
                    it.horizontalAlignment = Element.ALIGN_RIGHT
                },
            )
            table.addCell(
                PdfPCell(Phrase(item.lineTotal.toPlainString(), bodyFont)).also {
                    it.horizontalAlignment = Element.ALIGN_RIGHT
                },
            )
        }
        document.add(table)

        document.add(Paragraph("\nSubtotal: ${quote.subTotal}", bodyFont))
        document.add(Paragraph("Discount: ${quote.discount}", bodyFont))
        document.add(Paragraph("Tax: ${quote.tax}", bodyFont))
        document.add(
            Paragraph("Grand Total: ${quote.grandTotal}", bodyFont).also {
                it.alignment = Element.ALIGN_RIGHT
            },
        )

        document.close()
        return out.toByteArray()
    }
}
