package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.glknet.financial_manager.dto.DashboardDualSummaryDto;
import it.glknet.financial_manager.dto.SpaceDetailDto;
import it.glknet.financial_manager.dto.SpaceDto;
import it.glknet.financial_manager.dto.SpaceImportItem;
import it.glknet.financial_manager.model.Space;
import it.glknet.financial_manager.model.SpaceMovement;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.model.TransactionType;
import it.glknet.financial_manager.repository.SpaceMovementRepository;
import it.glknet.financial_manager.repository.SpaceRepository;
import it.glknet.financial_manager.repository.TransactionRepository;

class SpaceServiceTest {

    private SpaceRepository spaceRepo;
    private SpaceMovementRepository moveRepo;
    private TransactionRepository txRepo;
    private SpaceService service;

    @BeforeEach
    void setUp() {
        spaceRepo = mock(SpaceRepository.class);
        moveRepo = mock(SpaceMovementRepository.class);
        txRepo = mock(TransactionRepository.class);
        service = new SpaceService(spaceRepo, moveRepo, txRepo);
    }

    @Test
    void importSpaceItemsCreatesSpacesAndDetectsShared() {
        UUID userId = UUID.randomUUID();
        List<Space> savedSpaces = new ArrayList<>();
        List<SpaceMovement> savedMoves = new ArrayList<>();

        when(spaceRepo.save(any(Space.class))).thenAnswer(inv -> {
            Space s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            savedSpaces.add(s);
            return s;
        });

        when(moveRepo.save(any(SpaceMovement.class))).thenAnswer(inv -> {
            SpaceMovement m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            savedMoves.add(m);
            return m;
        });

        List<SpaceImportItem> items = List.of(
                new SpaceImportItem("Spesa", LocalDate.of(2026, 10, 10), new BigDecimal("150.00"), "Conto corrente principale", ""),
                new SpaceImportItem("Spesa", LocalDate.of(2026, 10, 12), new BigDecimal("100.00"), "ROSSELLA MEDAGLIA", ""),
                new SpaceImportItem("Spesa", LocalDate.of(2026, 10, 15), new BigDecimal("-40.00"), "Conto corrente principale", "BENNET 8"),
                new SpaceImportItem("Spesa", LocalDate.of(2026, 10, 16), new BigDecimal("-12.00"), "ROSSELLA MEDAGLIA", "LIDL 2514")
        );

        int imported = service.importSpaceItems(userId, items);

        assertEquals(4, imported);
        assertFalse(savedSpaces.isEmpty());
        Space spesa = savedSpaces.get(savedSpaces.size() - 1);
        assertEquals("Spesa", spesa.getName());
        assertTrue(spesa.isShared());
        assertEquals(0, new BigDecimal("198.00").compareTo(spesa.getCurrentBalance()));

        assertEquals("ANDREA", savedMoves.get(0).getAuthor());
        assertEquals("ROSSELLA", savedMoves.get(1).getAuthor());
        assertEquals("BENNET 8", savedMoves.get(2).getDescription());
        assertEquals("LIDL 2514", savedMoves.get(3).getDescription());
    }

    @Test
    void dashboardSummaryCalculatesGeneralAndPersonalSeparately() {
        UUID userId = UUID.randomUUID();
        LocalDate d = LocalDate.of(2026, 10, 15);

        // Main transactions: salary 2000, groceries card 40, rent card 500, rossella 100
        List<Transaction> mainTxs = List.of(
                Transaction.builder().id(UUID.randomUUID()).userId(userId).transactionDate(d)
                        .amount(new BigDecimal("2000.00")).type(TransactionType.ENTRATA).description("Stipendio").build(),
                Transaction.builder().id(UUID.randomUUID()).userId(userId).transactionDate(d)
                        .amount(new BigDecimal("100.00")).type(TransactionType.ENTRATA).description("Rossella Medaglia").build(),
                Transaction.builder().id(UUID.randomUUID()).userId(userId).transactionDate(d)
                        .amount(new BigDecimal("40.00")).type(TransactionType.USCITA).description("Bennet").build(),
                Transaction.builder().id(UUID.randomUUID()).userId(userId).transactionDate(d)
                        .amount(new BigDecimal("500.00")).type(TransactionType.USCITA).description("Affitto proprietario").build()
        );
        when(txRepo.findByUserIdAndTransactionDateBetween(any(), any(), any())).thenReturn(mainTxs);

        // Space movements: Rossella put 150 in Spesa, Andrea took 40 reimbursement, Rossella spent 20
        List<SpaceMovement> spaceMoves = List.of(
                SpaceMovement.builder().id(UUID.randomUUID()).userId(userId).date(d)
                        .amount(new BigDecimal("150.00")).author("ROSSELLA").description("Versamento").build(),
                SpaceMovement.builder().id(UUID.randomUUID()).userId(userId).date(d)
                        .amount(new BigDecimal("-40.00")).author("ANDREA").description("Rimborso Bennet").build(),
                SpaceMovement.builder().id(UUID.randomUUID()).userId(userId).date(d)
                        .amount(new BigDecimal("-20.00")).author("ROSSELLA").description("Lidl").build()
        );
        when(moveRepo.findByUserIdAndDateBetween(any(), any(), any())).thenReturn(spaceMoves);

        DashboardDualSummaryDto summary = service.getDashboardSummary(userId, 10, 2026);

        // General:
        // Income = Andrea salary (2000) + Rossella main (100) + Rossella space (150) = 2250
        // Expenses = Main expenses (540) + Rossella space withdrawals (20) = 560
        // Balance = 2250 - 560 = 1690
        assertEquals(0, new BigDecimal("2250.00").compareTo(summary.generalIncome()));
        assertEquals(0, new BigDecimal("560.00").compareTo(summary.generalExpenses()));
        assertEquals(0, new BigDecimal("1690.00").compareTo(summary.generalBalance()));

        // Personal Andrea:
        // Income = Andrea salary (2000)
        // Expenses = Main expenses (540) - reimbursements received (40) = 500
        // Balance = 2000 - 500 = 1500
        assertEquals(0, new BigDecimal("2000.00").compareTo(summary.personalIncome()));
        assertEquals(0, new BigDecimal("500.00").compareTo(summary.personalExpenses()));
        assertEquals(0, new BigDecimal("1500.00").compareTo(summary.personalBalance()));
    }
}
