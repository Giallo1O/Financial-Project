package it.glknet.financial_manager.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.glknet.financial_manager.config.AuthInterceptor;
import it.glknet.financial_manager.dto.DashboardDualSummaryDto;
import it.glknet.financial_manager.dto.SpaceDetailDto;
import it.glknet.financial_manager.dto.SpaceDto;
import it.glknet.financial_manager.dto.SpaceImportItem;
import it.glknet.financial_manager.service.SpaceService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @GetMapping
    public ResponseEntity<List<SpaceDto>> getSpaces(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
        return ResponseEntity.ok(spaceService.listSpaces(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpaceDetailDto> getSpaceDetail(
            @RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(spaceService.getSpaceDetail(userId, id));
    }

    @GetMapping("/dashboard-summary")
    public ResponseEntity<DashboardDualSummaryDto> getDashboardSummary(
            @RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year) {
        LocalDate now = LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int y = year != null ? year : now.getYear();
        return ResponseEntity.ok(spaceService.getDashboardSummary(userId, m, y));
    }

    @PostMapping("/import")
    public ResponseEntity<Integer> importSpaces(
            @RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
            @RequestBody List<SpaceImportItem> items) {
        return ResponseEntity.ok(spaceService.importSpaceItems(userId, items));
    }
}
