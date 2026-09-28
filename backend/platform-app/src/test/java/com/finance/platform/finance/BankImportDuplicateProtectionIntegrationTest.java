package com.finance.platform.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.finance.application.dto.BankAccountResponse;
import com.finance.platform.finance.application.dto.BankImportResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 3 — bank import duplicate protection (PostgreSQL / Testcontainers).
 */
class BankImportDuplicateProtectionIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String IDEMPOTENCY = "Idempotency-Key";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private BankTransactionJpaRepository bankTransactionJpaRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private IntegrationTestSupport.Session admin;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Bank Dedupe Firm");
	}

	@Test
	void firstImport_createsTransactions() throws Exception {
		ClientResponse client = createClient("Dedupe Client A");
		BankAccountResponse account = createBankAccount(client.id());
		String csv = sampleCsv("2026-09-03", "1500.00", "UBER TRIP", "839274", "12000.00");

		BankImportResponse result = importCsv(client.id(), account.id(), "sept.csv", csv);
		assertThat(result.importedCount()).isEqualTo(1);
		assertThat(result.duplicateCount()).isZero();
		assertThat(listTransactions(client.id(), account.id())).hasSize(1);
	}

	@Test
	void repeatedIdenticalFile_isIdempotent_noExtraTransactions() throws Exception {
		ClientResponse client = createClient("Dedupe Client B");
		BankAccountResponse account = createBankAccount(client.id());
		String csv = sampleCsv("2026-09-03", "4500.00", "UBER TRIP", "839274", "10500.00");

		importCsv(client.id(), account.id(), "bank-sept.csv", csv);
		BankImportResponse replay = importCsv(client.id(), account.id(), "bank-sept.csv", csv);

		assertThat(replay.importedCount()).isZero();
		assertThat(replay.duplicateCount()).isEqualTo(1);
		assertThat(listTransactions(client.id(), account.id())).hasSize(1);
	}

	@Test
	void sameContentDifferentFileName_skipsRowDuplicates() throws Exception {
		ClientResponse client = createClient("Dedupe Client C");
		BankAccountResponse account = createBankAccount(client.id());
		String csv = sampleCsv("2026-09-03", "2200.00", "FUEL", "F1", "9000.00");

		importCsv(client.id(), account.id(), "statement-september.csv", csv);
		BankImportResponse second = importCsv(client.id(), account.id(), "my-bank-export.csv", csv);

		assertThat(second.importedCount()).isZero();
		assertThat(second.duplicateCount()).isEqualTo(1);
		assertThat(listTransactions(client.id(), account.id())).hasSize(1);
	}

	@Test
	void overlappingFiles_doNotDuplicateSharedRows() throws Exception {
		ClientResponse client = createClient("Dedupe Client D");
		BankAccountResponse account = createBankAccount(client.id());
		String fileA = """
				Date,Description,Reference,Debit,Credit,Balance
				2026-09-10,SHARED ROW,REF-A,100.00,,5000.00
				2026-09-12,ONLY A,REF-B,200.00,,4800.00
				""";
		String fileB = """
				Date,Description,Reference,Debit,Credit,Balance
				2026-09-10,SHARED ROW,REF-A,100.00,,5000.00
				2026-09-20,ONLY B,REF-C,300.00,,4500.00
				""";

		BankImportResponse first = importCsv(client.id(), account.id(), "part-a.csv", fileA);
		BankImportResponse second = importCsv(client.id(), account.id(), "part-b.csv", fileB);

		assertThat(first.importedCount()).isEqualTo(2);
		assertThat(second.importedCount()).isEqualTo(1);
		assertThat(second.duplicateCount()).isEqualTo(1);
		assertThat(listTransactions(client.id(), account.id())).hasSize(3);
	}

	@Test
	void legitimateIdenticalLookingRows_bothImported_whenBalancesDiffer() throws Exception {
		ClientResponse client = createClient("Dedupe Client E");
		BankAccountResponse account = createBankAccount(client.id());
		String csv = """
				Date,Description,Reference,Debit,Credit,Balance
				2026-09-03,UBER TRIP,,1500.00,,8500.00
				2026-09-03,UBER TRIP,,1500.00,,7000.00
				""";

		BankImportResponse result = importCsv(client.id(), account.id(), "uber-twice.csv", csv);
		assertThat(result.importedCount()).isEqualTo(2);
		assertThat(result.duplicateCount()).isZero();
		assertThat(listTransactions(client.id(), account.id())).hasSize(2);
	}

	@Test
	void normalization_doesNotBypassDedupe() throws Exception {
		ClientResponse client = createClient("Dedupe Client F");
		BankAccountResponse account = createBankAccount(client.id());
		String first = sampleCsv("2026-09-05", "500.00", "KEELLS SUPER", "R1", "8000.00");
		String second = sampleCsv("2026-09-05", "500.00", "keells   super", "r1", "8000.00");

		importCsv(client.id(), account.id(), "first.csv", first);
		BankImportResponse replay = importCsv(client.id(), account.id(), "second.csv", second);

		assertThat(replay.importedCount()).isZero();
		assertThat(replay.duplicateCount()).isEqualTo(1);
		assertThat(listTransactions(client.id(), account.id())).hasSize(1);
	}

	@Test
	void identicalTransactionIdentity_allowedAcrossTenants() throws Exception {
		IntegrationTestSupport.Session otherFirm = support.registerFirmAdmin("Other Bank Firm");
		ClientResponse clientA = createClient("Tenant A Client");
		ClientResponse clientB = createClientFor(otherFirm, "Tenant B Client");
		BankAccountResponse accountA = createBankAccount(clientA.id());
		BankAccountResponse accountB = createBankAccountFor(otherFirm, clientB.id());
		String csv = sampleCsv("2026-09-08", "99.00", "SHARED VENDOR", "X1", "1000.00");

		importCsv(clientA.id(), accountA.id(), "a.csv", csv);
		importCsvFor(otherFirm, clientB.id(), accountB.id(), "b.csv", csv);

		assertThat(listTransactions(clientA.id(), accountA.id())).hasSize(1);
		assertThat(listTransactionsFor(otherFirm, clientB.id(), accountB.id())).hasSize(1);
	}

	@Test
	void identicalTransactionIdentity_allowedAcrossBankAccounts() throws Exception {
		ClientResponse client = createClient("Two Accounts");
		BankAccountResponse account1 = createBankAccount(client.id());
		BankAccountResponse account2 = createSecondBankAccount(client.id());
		String csv = sampleCsv("2026-09-09", "77.00", "TRANSFER", "T1", "5000.00");

		importCsv(client.id(), account1.id(), "acc1.csv", csv);
		importCsv(client.id(), account2.id(), "acc2.csv", csv);

		assertThat(listTransactions(client.id(), account1.id())).hasSize(1);
		assertThat(listTransactions(client.id(), account2.id())).hasSize(1);
	}

	@Test
	void concurrentImports_sameRows_produceSingleTransaction() throws Exception {
		ClientResponse client = createClient("Concurrent Client");
		BankAccountResponse account = createBankAccount(client.id());
		importCsv(client.id(), account.id(), "seed.csv",
				sampleCsv("2026-09-11", "3333.00", "PAYROLL", "P1", "20000.00"));
		BankTransaction template = bankTransactionJpaRepository.findAll().stream()
				.filter(t -> t.getBankAccount().getId().equals(account.id()))
				.findFirst()
				.orElseThrow();

		Integer uniqueIndexes = jdbcTemplate.queryForObject("""
				select count(*) from pg_indexes
				where indexname = 'uq_bank_txn_account_row_hash'
				""", Integer.class);
		assertThat(uniqueIndexes).isEqualTo(1);

		BankTransaction duplicateAttempt = BankTransaction.builder()
				.client(template.getClient())
				.bankAccount(template.getBankAccount())
				.bankImport(template.getBankImport())
				.txnDate(template.getTxnDate())
				.valueDate(template.getValueDate())
				.description(template.getDescription())
				.referenceNo(template.getReferenceNo())
				.debit(template.getDebit())
				.credit(template.getCredit())
				.balance(template.getBalance())
				.externalRowHash(template.getExternalRowHash())
				.matchStatus(BankTransaction.MatchStatus.UNMATCHED)
				.build();
		duplicateAttempt.setFirmId(template.getFirmId());
		assertThat(listTransactions(client.id(), account.id())).hasSize(1);
		assertThatThrownBy(() -> bankTransactionJpaRepository.saveAndFlush(duplicateAttempt))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void idempotencyKeyReplay_doesNotDuplicateTransactions() throws Exception {
		ClientResponse client = createClient("Idempotency Client");
		BankAccountResponse account = createBankAccount(client.id());
		String csv = sampleCsv("2026-09-15", "1200.00", "SUPPLIER", "S1", "3000.00");
		String key = UUID.randomUUID().toString();

		importCsvWithKey(client.id(), account.id(), "once.csv", csv, key);
		importCsvWithKey(client.id(), account.id(), "once.csv", csv, key);

		assertThat(listTransactions(client.id(), account.id())).hasSize(1);
	}

	private static String sampleCsv(String date, String debit, String description, String reference, String balance) {
		return "Date,Description,Reference,Debit,Credit,Balance\n"
				+ date + "," + description + "," + reference + "," + debit + ",," + balance + "\n";
	}

	private ClientResponse createClient(String name) throws Exception {
		return createClientFor(admin, name);
	}

	private ClientResponse createClientFor(IntegrationTestSupport.Session session, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ClientResponse.class);
	}

	private BankAccountResponse createBankAccount(UUID clientId) throws Exception {
		return createBankAccountFor(admin, clientId);
	}

	private BankAccountResponse createBankAccountFor(IntegrationTestSupport.Session session, UUID clientId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/accounts")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bankName": "Dedupe Bank",
								  "accountName": "Operating",
								  "maskedAccountNumber": "****1111",
								  "currency": "LKR"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, BankAccountResponse.class);
	}

	private BankAccountResponse createSecondBankAccount(UUID clientId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/accounts")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bankName": "Dedupe Bank",
								  "accountName": "Savings",
								  "maskedAccountNumber": "****2222",
								  "currency": "LKR"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, BankAccountResponse.class);
	}

	private BankImportResponse importCsv(UUID clientId, UUID bankAccountId, String fileName, String csv) throws Exception {
		return importCsvFor(admin, clientId, bankAccountId, fileName, csv);
	}

	private BankImportResponse importCsvFor(
			IntegrationTestSupport.Session session,
			UUID clientId,
			UUID bankAccountId,
			String fileName,
			String csv
	) throws Exception {
		return importCsvWithKey(session, clientId, bankAccountId, fileName, csv, UUID.randomUUID().toString());
	}

	private BankImportResponse importCsvWithKey(
			UUID clientId,
			UUID bankAccountId,
			String fileName,
			String csv,
			String idempotencyKey
	) throws Exception {
		return importCsvWithKey(admin, clientId, bankAccountId, fileName, csv, idempotencyKey);
	}

	private BankImportResponse importCsvWithKey(
			IntegrationTestSupport.Session session,
			UUID clientId,
			UUID bankAccountId,
			String fileName,
			String csv,
			String idempotencyKey
	) throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
		MvcResult result = mockMvc.perform(multipart("/api/v1/clients/" + clientId + "/bank/imports")
						.file(file)
						.param("bankAccountId", bankAccountId.toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.header(IDEMPOTENCY, idempotencyKey))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, BankImportResponse.class);
	}

	private List<BankTransactionResponse> listTransactions(UUID clientId, UUID bankAccountId) throws Exception {
		return listTransactionsFor(admin, clientId, bankAccountId);
	}

	private List<BankTransactionResponse> listTransactionsFor(
			IntegrationTestSupport.Session session,
			UUID clientId,
			UUID bankAccountId
	) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/clients/" + clientId + "/bank/transactions")
						.param("bankAccountId", bankAccountId.toString())
						.param("size", "50")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token())))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
		List<BankTransactionResponse> rows = new ArrayList<>();
		for (JsonNode row : page.get("content")) {
			rows.add(objectMapper.treeToValue(row, BankTransactionResponse.class));
		}
		return rows;
	}
}
