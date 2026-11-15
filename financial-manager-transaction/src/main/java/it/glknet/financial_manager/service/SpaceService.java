package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final SpaceMovementRepository movementRepository;
    private final TransactionRepository transactionRepository;

    public List<SpaceDto> listSpaces(UUID userId) {
        List<Space> spaces = spaceRepository.findByUserIdOrderByCurrentBalanceDesc(userId);
        List<SpaceDto> dtos = new ArrayList<>();

        for (Space s : spaces) {
            List<SpaceMovement> moves = movementRepository.findBySpaceIdOrderByDateAscCreatedAtAsc(s.getId());
            BigDecimal totalIn = BigDecimal.ZERO;
            BigDecimal totalOut = BigDecimal.ZERO;
            BigDecimal andreaIn = BigDecimal.ZERO;
            BigDecimal rossellaIn = BigDecimal.ZERO;

            for (SpaceMovement m : moves) {
                if (m.getAmount().signum() > 0) {
                    totalIn = totalIn.add(m.getAmount());
                    if ("ROSSELLA".equalsIgnoreCase(m.getAuthor())) {
                        rossellaIn = rossellaIn.add(m.getAmount());
                    } else {
                        andreaIn = andreaIn.add(m.getAmount());
                    }
                } else {
                    totalOut = totalOut.add(m.getAmount().abs());
                }
            }

            dtos.add(new SpaceDto(
                    s.getId(),
                    s.getName(),
                    s.getIcon(),
                    s.getColor(),
                    s.getCurrentBalance(),
                    s.isShared(),
                    totalIn,
                    totalOut,
                    andreaIn,
                    rossellaIn,
                    moves.size()
            ));
        }
        return dtos;
    }

    public SpaceDetailDto getSpaceDetail(UUID userId, UUID spaceId) {
        Space space = spaceRepository.findById(spaceId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spazio non trovato"));

        List<SpaceMovement> chronMoves = movementRepository.findBySpaceIdOrderByDateAscCreatedAtAsc(spaceId);

        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        BigDecimal andreaIn = BigDecimal.ZERO;
        BigDecimal rossellaIn = BigDecimal.ZERO;
        BigDecimal externalOut = BigDecimal.ZERO;

        List<SpaceDetailDto.TimelinePoint> timeline = new ArrayList<>();
        for (SpaceMovement m : chronMoves) {
            if (m.getAmount().signum() > 0) {
                totalIn = totalIn.add(m.getAmount());
                if ("ROSSELLA".equalsIgnoreCase(m.getAuthor())) {
                    rossellaIn = rossellaIn.add(m.getAmount());
                } else {
                    andreaIn = andreaIn.add(m.getAmount());
                }
            } else {
                totalOut = totalOut.add(m.getAmount().abs());
                if ("ESTERNO".equalsIgnoreCase(m.getAuthor())) {
                    externalOut = externalOut.add(m.getAmount().abs());
                }
            }
            timeline.add(new SpaceDetailDto.TimelinePoint(m.getDate(), m.getBalanceAfter()));
        }

        List<SpaceMovement> descMoves = movementRepository.findBySpaceIdOrderByDateDescCreatedAtDesc(spaceId);

        return new SpaceDetailDto(
                space,
                totalIn,
                totalOut,
                andreaIn,
                rossellaIn,
                externalOut,
                timeline,
                descMoves
        );
    }

    public DashboardDualSummaryDto getDashboardSummary(UUID userId, int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Transaction> mainTxs = transactionRepository.findByUserIdAndTransactionDateBetween(userId, start, end);
        List<SpaceMovement> spaceMoves = movementRepository.findByUserIdAndDateBetween(userId, start, end);

        BigDecimal andreaPersonalIncome = BigDecimal.ZERO;
        BigDecimal rossellaMainIncome = BigDecimal.ZERO;
        BigDecimal mainExternalExpenses = BigDecimal.ZERO;

        for (Transaction t : mainTxs) {
            String desc = t.getDescription() == null ? "" : t.getDescription().toLowerCase(Locale.ROOT);
            if (t.getType() == TransactionType.ENTRATA) {
                if (desc.contains("rossella")) {
                    rossellaMainIncome = rossellaMainIncome.add(t.getAmount());
                } else {
                    andreaPersonalIncome = andreaPersonalIncome.add(t.getAmount());
                }
            } else if (t.getType() == TransactionType.USCITA) {
                mainExternalExpenses = mainExternalExpenses.add(t.getAmount());
            }
        }

        BigDecimal rossellaSpaceIncome = BigDecimal.ZERO;
        BigDecimal andreaReimbursements = BigDecimal.ZERO;
        BigDecimal spaceExternalExpenses = BigDecimal.ZERO;
        BigDecimal rossellaSpaceWithdrawals = BigDecimal.ZERO;

        for (SpaceMovement sm : spaceMoves) {
            String auth = sm.getAuthor() == null ? "" : sm.getAuthor().toUpperCase(Locale.ROOT);
            BigDecimal amt = sm.getAmount();

            if (amt.signum() > 0) {
                if ("ROSSELLA".equals(auth)) {
                    rossellaSpaceIncome = rossellaSpaceIncome.add(amt);
                }
            } else {
                BigDecimal absAmt = amt.abs();
                if ("ANDREA".equals(auth)) {
                    // prelievo da spazio verso conto principale = rimborso ad Andrea
                    andreaReimbursements = andreaReimbursements.add(absAmt);
                } else if ("ROSSELLA".equals(auth)) {
                    rossellaSpaceWithdrawals = rossellaSpaceWithdrawals.add(absAmt);
                } else {
                    spaceExternalExpenses = spaceExternalExpenses.add(absAmt);
                }
            }
        }

        BigDecimal rossellaTotalContribution = rossellaMainIncome.add(rossellaSpaceIncome);
        BigDecimal generalIncome = andreaPersonalIncome.add(rossellaTotalContribution);
        BigDecimal generalExpenses = mainExternalExpenses.add(spaceExternalExpenses).add(rossellaSpaceWithdrawals);
        BigDecimal generalBalance = generalIncome.subtract(generalExpenses);

        BigDecimal personalIncome = andreaPersonalIncome;
        // Spese personali Andrea = spese uscite dalla carta principale meno i rimborsi ricevuti dagli spazi comuni
        BigDecimal personalExpenses = mainExternalExpenses.subtract(andreaReimbursements).max(BigDecimal.ZERO);
        BigDecimal personalBalance = personalIncome.subtract(personalExpenses);

        return new DashboardDualSummaryDto(
                month,
                year,
                generalIncome,
                generalExpenses,
                generalBalance,
                personalIncome,
                personalExpenses,
                personalBalance,
                rossellaTotalContribution,
                andreaReimbursements
        );
    }

    @Transactional
    public int importSpaceItems(UUID userId, List<SpaceImportItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        // Raggruppa per spazio
        Map<String, List<SpaceImportItem>> bySpace = new HashMap<>();
        for (SpaceImportItem item : items) {
            if (item.spaceName() == null || item.spaceName().isBlank()) continue;
            bySpace.computeIfAbsent(item.spaceName().trim(), k -> new ArrayList<>()).add(item);
        }

        int importedCount = 0;

        for (Map.Entry<String, List<SpaceImportItem>> entry : bySpace.entrySet()) {
            String spaceName = entry.getKey();
            List<SpaceImportItem> spaceItems = entry.getValue();
            spaceItems.sort(Comparator.comparing(SpaceImportItem::date));

            Space space = spaceRepository.findByUserIdAndNameIgnoreCase(userId, spaceName)
                    .orElseGet(() -> Space.builder()
                            .userId(userId)
                            .name(spaceName)
                            .icon(defaultIcon(spaceName))
                            .color(defaultColor(spaceName))
                            .currentBalance(BigDecimal.ZERO)
                            .shared(false)
                            .build());

            // Carica movimenti esistenti per non duplicare
            List<SpaceMovement> existing = movementRepository.findBySpaceIdOrderByDateAscCreatedAtAsc(
                    space.getId() != null ? space.getId() : UUID.randomUUID());
            Set<String> existingKeys = new HashSet<>();
            for (SpaceMovement m : existing) {
                existingKeys.add(movementKey(m.getDate(), m.getAmount(), m.getReference(), m.getPartnerName()));
            }

            BigDecimal runningBalance = space.getId() != null && !existing.isEmpty()
                    ? existing.get(existing.size() - 1).getBalanceAfter()
                    : BigDecimal.ZERO;

            boolean newlyCreated = space.getId() == null;
            if (newlyCreated) {
                space = spaceRepository.save(space);
            }

            for (SpaceImportItem item : spaceItems) {
                String key = movementKey(item.date(), item.amount(), item.reference(), item.partnerName());
                if (existingKeys.contains(key)) {
                    continue;
                }

                String partner = item.partnerName() == null ? "" : item.partnerName().trim();
                String ref = item.reference() == null ? "" : item.reference().trim();
                String author;

                if (partner.toLowerCase(Locale.ROOT).contains("rossella")) {
                    author = "ROSSELLA";
                    space.setShared(true);
                } else if ("conto corrente principale".equalsIgnoreCase(partner)) {
                    author = "ANDREA";
                } else {
                    author = "ESTERNO";
                }

                String desc;
                if (!ref.isEmpty()) {
                    desc = ref;
                } else if ("conto corrente principale".equalsIgnoreCase(partner)) {
                    desc = (item.amount().signum() > 0 ? "Versamento da " : "Prelievo verso ") + "Conto principale";
                } else {
                    desc = partner.isEmpty() ? "Movimento spazio" : partner;
                }

                runningBalance = runningBalance.add(item.amount());

                SpaceMovement move = SpaceMovement.builder()
                        .spaceId(space.getId())
                        .userId(userId)
                        .date(item.date())
                        .amount(item.amount())
                        .author(author)
                        .partnerName(partner)
                        .reference(ref)
                        .description(desc)
                        .balanceAfter(runningBalance)
                        .build();

                movementRepository.save(move);
                existingKeys.add(key);
                importedCount++;
            }

            space.setCurrentBalance(runningBalance);
            spaceRepository.save(space);
        }

        return importedCount;
    }

    private static String movementKey(LocalDate date, BigDecimal amt, String ref, String partner) {
        String cleanRef = ref == null ? "" : ref.trim().toLowerCase(Locale.ROOT);
        String cleanPartner = partner == null ? "" : partner.trim().toLowerCase(Locale.ROOT);
        String plainAmt = amt == null ? "0" : amt.stripTrailingZeros().toPlainString();
        return date + "|" + plainAmt + "|" + cleanRef + "|" + cleanPartner;
    }

    public static String defaultIcon(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("spesa")) return "shopping-cart";
        if (n.contains("affitto")) return "home";
        if (n.contains("bollette")) return "zap";
        if (n.contains("airi") || n.contains("🐱") || n.contains("gatto") || n.contains("gatta")) return "cat";
        if (n.contains("abbonamenti")) return "tv";
        if (n.contains("vacanze")) return "plane";
        if (n.contains("macchina") || n.contains("auto")) return "car";
        if (n.contains("benzina")) return "fuel";
        if (n.contains("debiti")) return "credit-card";
        return "wallet";
    }

    public static String defaultColor(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("spesa")) return "#10b981"; // emerald
        if (n.contains("affitto")) return "#6366f1"; // indigo
        if (n.contains("bollette")) return "#f59e0b"; // amber
        if (n.contains("airi") || n.contains("🐱")) return "#ec4899"; // pink
        if (n.contains("abbonamenti")) return "#8b5cf6"; // purple
        if (n.contains("vacanze")) return "#06b6d4"; // cyan
        if (n.contains("macchina")) return "#3b82f6"; // blue
        if (n.contains("benzina")) return "#ef4444"; // red
        if (n.contains("debiti")) return "#64748b"; // slate
        return "#9e5545"; // terracotta
    }
}
