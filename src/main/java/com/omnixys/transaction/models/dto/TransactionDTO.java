package com.omnixys.transaction.models.dto;

import com.omnixys.transaction.models.enums.CurrencyType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionDTO(
  @NotNull(message = "Amount cannot be null")
  @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
  BigDecimal amount,

  @NotNull(message = "Currency cannot be null")
  CurrencyType currency,

  UUID sender,
  UUID receiver
) {
  public interface OnCreate { }
}
