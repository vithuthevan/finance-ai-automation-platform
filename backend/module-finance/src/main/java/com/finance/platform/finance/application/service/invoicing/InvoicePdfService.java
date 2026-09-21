package com.finance.platform.finance.application.service.invoicing;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {

	private final InvoiceHtmlRenderer htmlRenderer;

	public byte[] renderPdf(java.util.UUID invoiceId) {
		String html = htmlRenderer.buildHtmlInvoice(invoiceId);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.withHtmlContent(html, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (Exception ex) {
			throw new IllegalStateException("PDF generation failed: " + ex.getMessage(), ex);
		}
	}
}
