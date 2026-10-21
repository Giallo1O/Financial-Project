package it.glknet.financial_manager.dto;

import java.util.List;

import it.glknet.financial_manager.model.Receipt;
import it.glknet.financial_manager.model.ReceiptItem;

public record ReceiptDetail(Receipt receipt, List<ReceiptItem> items) {
}
