package com.finance.platform.reporting.application;

import com.finance.platform.reporting.api.ReportingFacade;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportExportService {

	private final ReportingFacade reportingFacade;

	public ExportFile exportPl(UUID clientId, LocalDate from, LocalDate to, String format) {
		ReportingFacade.PlSummary summary = reportingFacade.generatePlSummary(clientId, from, to);
		if ("xlsx".equalsIgnoreCase(format)) {
			return new ExportFile("pl-" + clientId + ".xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					toXlsx(summary));
		}
		return new ExportFile("pl-" + clientId + ".csv", "text/csv", toCsv(summary).getBytes(StandardCharsets.UTF_8));
	}

	private String toCsv(ReportingFacade.PlSummary summary) {
		StringBuilder csv = new StringBuilder();
		csv.append("Section,Category Code,Category Name,Amount\n");
		csv.append("TOTAL_INCOME,,,")
				.append(summary.totalIncome())
				.append('\n');
		for (ReportingFacade.CategoryAmount row : summary.incomeByCategory()) {
			csv.append("INCOME,")
					.append(safe(row.categoryCode())).append(',')
					.append(safe(row.categoryName())).append(',')
					.append(row.amount())
					.append('\n');
		}
		csv.append("TOTAL_EXPENSES,,,")
				.append(summary.totalExpenses())
				.append('\n');
		for (ReportingFacade.CategoryAmount row : summary.expensesByCategory()) {
			csv.append("EXPENSE,")
					.append(safe(row.categoryCode())).append(',')
					.append(safe(row.categoryName())).append(',')
					.append(row.amount())
					.append('\n');
		}
		csv.append("NET_RESULT,,,")
				.append(summary.netResult())
				.append('\n');
		return csv.toString();
	}

	private byte[] toXlsx(ReportingFacade.PlSummary summary) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Profit and Loss");
			int r = 0;
			Row header = sheet.createRow(r++);
			header.createCell(0).setCellValue("Section");
			header.createCell(1).setCellValue("Category Code");
			header.createCell(2).setCellValue("Category Name");
			header.createCell(3).setCellValue("Amount");
			writeRow(sheet, r++, "TOTAL_INCOME", "", "", summary.totalIncome().toPlainString());
			for (ReportingFacade.CategoryAmount row : summary.incomeByCategory()) {
				writeRow(sheet, r++, "INCOME", row.categoryCode(), row.categoryName(), row.amount().toPlainString());
			}
			writeRow(sheet, r++, "TOTAL_EXPENSES", "", "", summary.totalExpenses().toPlainString());
			for (ReportingFacade.CategoryAmount row : summary.expensesByCategory()) {
				writeRow(sheet, r++, "EXPENSE", row.categoryCode(), row.categoryName(), row.amount().toPlainString());
			}
			writeRow(sheet, r, "NET_RESULT", "", "", summary.netResult().toPlainString());
			workbook.write(out);
			return out.toByteArray();
		} catch (IOException ex) {
			throw new IllegalStateException("Unable to create spreadsheet export");
		}
	}

	private static void writeRow(Sheet sheet, int index, String section, String code, String name, String amount) {
		Row row = sheet.createRow(index);
		row.createCell(0).setCellValue(section == null ? "" : section);
		row.createCell(1).setCellValue(code == null ? "" : code);
		row.createCell(2).setCellValue(name == null ? "" : name);
		row.createCell(3).setCellValue(amount == null ? "" : amount);
	}

	private static String safe(String value) {
		if (value == null) {
			return "";
		}
		return value.contains(",") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
	}

	public record ExportFile(String fileName, String contentType, byte[] content) {
	}
}
