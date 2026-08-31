package com.finance.platform.reporting.application;

import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.finance.api.LedgerQueryFacade;
import com.finance.platform.finance.application.service.ClientAccessService;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.reporting.api.ReportingFacade;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportExportService {

	private final ReportingFacade reportingFacade;
	private final LedgerQueryFacade ledgerQueryFacade;
	private final ClientAccessService clientAccessService;
	private final AuditLogger auditLogger;

	public ExportFile exportPl(UUID clientId, LocalDate from, LocalDate to, String format) {
		requireSupportedFormat(format);
		ReportingFacade.PlSummary summary = reportingFacade.generatePlSummary(clientId, from, to);
		String base = fileName(summary.clientName(), "PnL", from, to);
		ExportFile file = isXlsx(format)
				? new ExportFile(base + ".xlsx", xlsxType(), toPlXlsx(summary))
				: new ExportFile(base + ".csv", csvType(), toPlCsv(summary).getBytes(StandardCharsets.UTF_8));
		auditExport(summary.clientId(), "PROFIT_AND_LOSS", from, to, file);
		return file;
	}

	public ExportFile exportIncome(UUID clientId, LocalDate from, LocalDate to, String format) {
		requireSupportedFormat(format);
		ReportingFacade.IncomeSummary summary = reportingFacade.generateIncomeSummary(clientId, from, to);
		Client client = clientAccessService.requireReportAccess(clientId);
		List<LedgerQueryFacade.ApprovedTransactionView> rows =
				ledgerQueryFacade.findApprovedTransactions(client.getFirmId(), clientId, from, to, "INCOME", null);
		String base = fileName(summary.clientName(), "Income", from, to);
		ExportFile file = isXlsx(format)
				? new ExportFile(base + ".xlsx", xlsxType(), toTransactionsXlsx("Income", summary.clientName(), from, to, summary.currencyCode(), rows))
				: new ExportFile(base + ".csv", csvType(), toTransactionsCsv(rows).getBytes(StandardCharsets.UTF_8));
		auditExport(clientId, "INCOME_TRANSACTIONS", from, to, file);
		return file;
	}

	public ExportFile exportExpenses(UUID clientId, LocalDate from, LocalDate to, String format) {
		requireSupportedFormat(format);
		ReportingFacade.ExpenseSummary summary = reportingFacade.generateExpenseSummary(clientId, from, to);
		Client client = clientAccessService.requireReportAccess(clientId);
		List<LedgerQueryFacade.ApprovedTransactionView> rows =
				ledgerQueryFacade.findApprovedTransactions(client.getFirmId(), clientId, from, to, "EXPENSE", null);
		String base = fileName(summary.clientName(), "Expenses", from, to);
		ExportFile file = isXlsx(format)
				? new ExportFile(base + ".xlsx", xlsxType(), toTransactionsXlsx("Expenses", summary.clientName(), from, to, summary.currencyCode(), rows))
				: new ExportFile(base + ".csv", csvType(), toTransactionsCsv(rows).getBytes(StandardCharsets.UTF_8));
		auditExport(clientId, "EXPENSE_TRANSACTIONS", from, to, file);
		return file;
	}

	private String toPlCsv(ReportingFacade.PlSummary summary) {
		StringBuilder csv = new StringBuilder("\uFEFF");
		csv.append("Client,").append(csvCell(summary.clientName())).append('\n');
		csv.append("Period,").append(summary.from()).append(',').append(summary.to()).append('\n');
		csv.append("Currency,").append(csvCell(summary.currencyCode())).append('\n');
		csv.append("Result,").append(csvCell(summary.resultType())).append('\n');
		csv.append('\n');
		csv.append("Section,Code,Category,Amount,Percent\n");
		for (ReportingFacade.CategoryAmount row : summary.incomeByCategory()) {
			appendCategory(csv, "INCOME", row);
		}
		csv.append("TOTAL INCOME,,,")
				.append(plain(summary.totalIncome()))
				.append('\n');
		for (ReportingFacade.CategoryAmount row : summary.expensesByCategory()) {
			appendCategory(csv, "EXPENSE", row);
		}
		csv.append("TOTAL EXPENSES,,,")
				.append(plain(summary.totalExpenses()))
				.append('\n');
		csv.append("NET PROFIT / LOSS,,,")
				.append(plain(summary.netResult()))
				.append('\n');
		return csv.toString();
	}

	private static void appendCategory(StringBuilder csv, String section, ReportingFacade.CategoryAmount row) {
		csv.append(section).append(',')
				.append(csvCell(row.categoryCode())).append(',')
				.append(csvCell(row.categoryName())).append(',')
				.append(plain(row.amount())).append(',')
				.append(plain(row.percentageOfTotal()))
				.append('\n');
	}

	private String toTransactionsCsv(List<LedgerQueryFacade.ApprovedTransactionView> rows) {
		StringBuilder csv = new StringBuilder("\uFEFF");
		csv.append("Date,Type,Party,Category Code,Category,Amount,Currency\n");
		for (LedgerQueryFacade.ApprovedTransactionView row : rows) {
			csv.append(row.transactionDate()).append(',')
					.append(csvCell(row.type())).append(',')
					.append(csvCell(row.partyName())).append(',')
					.append(csvCell(row.categoryCode())).append(',')
					.append(csvCell(row.categoryName())).append(',')
					.append(plain(row.amount())).append(',')
					.append(csvCell(row.currencyCode()))
					.append('\n');
		}
		return csv.toString();
	}

	private byte[] toPlXlsx(ReportingFacade.PlSummary summary) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Profit and Loss");
			CellStyle bold = boldStyle(workbook);
			CellStyle money = moneyStyle(workbook);
			int r = 0;
			r = writeLabel(sheet, r, "Client", summary.clientName(), bold);
			r = writeLabel(sheet, r, "Period", summary.from() + " to " + summary.to(), bold);
			r = writeLabel(sheet, r, "Currency", summary.currencyCode(), bold);
			r = writeLabel(sheet, r, "Result", summary.resultType(), bold);
			r++;
			writeHeading(sheet, r++, "INCOME", bold);
			r = writeCategoryBlock(sheet, r, summary.incomeByCategory(), money);
			r = writeTotal(sheet, r, "TOTAL INCOME", summary.totalIncome(), bold, money);
			r++;
			writeHeading(sheet, r++, "EXPENSES", bold);
			r = writeCategoryBlock(sheet, r, summary.expensesByCategory(), money);
			r = writeTotal(sheet, r, "TOTAL EXPENSES", summary.totalExpenses(), bold, money);
			r++;
			writeTotal(sheet, r, "NET PROFIT / LOSS", summary.netResult(), bold, money);
			for (int i = 0; i < 4; i++) {
				sheet.autoSizeColumn(i);
			}
			workbook.write(out);
			return out.toByteArray();
		} catch (IOException ex) {
			throw new BusinessException(ErrorCodes.REPORT_EXPORT_FAILED, "Unable to create spreadsheet export");
		}
	}

	private byte[] toTransactionsXlsx(
			String title,
			String clientName,
			LocalDate from,
			LocalDate to,
			String currency,
			List<LedgerQueryFacade.ApprovedTransactionView> rows
	) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet(title);
			CellStyle bold = boldStyle(workbook);
			CellStyle money = moneyStyle(workbook);
			int r = 0;
			r = writeLabel(sheet, r, "Client", clientName, bold);
			r = writeLabel(sheet, r, "Period", from + " to " + to, bold);
			r = writeLabel(sheet, r, "Currency", currency, bold);
			r++;
			Row header = sheet.createRow(r++);
			String[] titles = {"Date", "Type", "Party", "Category Code", "Category", "Amount", "Currency"};
			for (int i = 0; i < titles.length; i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(titles[i]);
				cell.setCellStyle(bold);
			}
			for (LedgerQueryFacade.ApprovedTransactionView row : rows) {
				Row excel = sheet.createRow(r++);
				excel.createCell(0).setCellValue(row.transactionDate() != null ? row.transactionDate().toString() : "");
				excel.createCell(1).setCellValue(nullToEmpty(row.type()));
				excel.createCell(2).setCellValue(nullToEmpty(row.partyName()));
				excel.createCell(3).setCellValue(nullToEmpty(row.categoryCode()));
				excel.createCell(4).setCellValue(nullToEmpty(row.categoryName()));
				setMoney(excel.createCell(5), row.amount(), money);
				excel.createCell(6).setCellValue(nullToEmpty(row.currencyCode()));
			}
			for (int i = 0; i < titles.length; i++) {
				sheet.autoSizeColumn(i);
			}
			workbook.write(out);
			return out.toByteArray();
		} catch (IOException ex) {
			throw new BusinessException(ErrorCodes.REPORT_EXPORT_FAILED, "Unable to create spreadsheet export");
		}
	}

	private void auditExport(UUID clientId, String reportType, LocalDate from, LocalDate to, ExportFile file) {
		Client client = clientAccessService.requireReportAccess(clientId);
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("reportType", reportType);
		after.put("from", from.toString());
		after.put("to", to.toString());
		after.put("format", file.fileName().endsWith(".xlsx") ? "XLSX" : "CSV");
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(client.getFirmId())
				.action(AuditAction.REPORT_EXPORTED)
				.resourceType(AuditResourceType.REPORT)
				.clientId(clientId)
				.afterState(after)
				.build());
	}

	private static int writeCategoryBlock(Sheet sheet, int rowIndex, List<ReportingFacade.CategoryAmount> rows, CellStyle money) {
		int r = rowIndex;
		for (ReportingFacade.CategoryAmount row : rows) {
			Row excel = sheet.createRow(r++);
			excel.createCell(0).setCellValue(nullToEmpty(row.categoryCode()));
			excel.createCell(1).setCellValue(nullToEmpty(row.categoryName()));
			setMoney(excel.createCell(2), row.amount(), money);
		}
		return r;
	}

	private static int writeTotal(Sheet sheet, int rowIndex, String label, BigDecimal amount, CellStyle bold, CellStyle money) {
		Row row = sheet.createRow(rowIndex);
		Cell labelCell = row.createCell(1);
		labelCell.setCellValue(label);
		labelCell.setCellStyle(bold);
		setMoney(row.createCell(2), amount, money);
		return rowIndex + 1;
	}

	private static void writeHeading(Sheet sheet, int rowIndex, String text, CellStyle bold) {
		Cell cell = sheet.createRow(rowIndex).createCell(0);
		cell.setCellValue(text);
		cell.setCellStyle(bold);
	}

	private static int writeLabel(Sheet sheet, int rowIndex, String label, String value, CellStyle bold) {
		Row row = sheet.createRow(rowIndex);
		Cell labelCell = row.createCell(0);
		labelCell.setCellValue(label);
		labelCell.setCellStyle(bold);
		row.createCell(1).setCellValue(value == null ? "" : value);
		return rowIndex + 1;
	}

	private static void setMoney(Cell cell, BigDecimal amount, CellStyle money) {
		cell.setCellValue((amount == null ? BigDecimal.ZERO : amount).doubleValue());
		cell.setCellStyle(money);
	}

	private static CellStyle moneyStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();
		style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
		return style;
	}

	private static CellStyle boldStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();
		Font font = workbook.createFont();
		font.setBold(true);
		style.setFont(font);
		return style;
	}

	static String fileName(String clientName, String report, LocalDate from, LocalDate to) {
		String safeClient = sanitize(clientName);
		return safeClient + "_" + report + "_" + from + "_" + to;
	}

	private static String sanitize(String value) {
		String raw = value == null || value.isBlank() ? "Client" : value;
		return raw.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
	}

	private static String csvCell(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	private static String plain(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).toPlainString();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static void requireSupportedFormat(String format) {
		if (format == null || format.isBlank() || format.equalsIgnoreCase("csv") || format.equalsIgnoreCase("xlsx")) {
			return;
		}
		throw new BusinessException(ErrorCodes.REPORT_EXPORT_FAILED, "Export format must be csv or xlsx");
	}

	private static boolean isXlsx(String format) {
		return format != null && format.equalsIgnoreCase("xlsx");
	}

	private static String xlsxType() {
		return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	}

	private static String csvType() {
		return "text/csv; charset=UTF-8";
	}

	public record ExportFile(String fileName, String contentType, byte[] content) {
	}
}
