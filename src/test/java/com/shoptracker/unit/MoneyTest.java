package com.shoptracker.unit;

import com.shoptracker.orders.domain.model.Money;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

public class MoneyTest {

    @Test
    void addTwoMoneyValues() {
        Money a = new Money(1000);
        Money b = new Money(2000);
        assertThat(a.add(b)).isEqualTo(new Money(3000));
    }

    @Test
    void subtractMoney() {
        Money a = new Money(5000);
        Money b = new Money(3000);
        assertThat(a.subtract(b)).isEqualTo(new Money(2000));
    }

    @Test
    void applyDiscountRate() {
        Money amount = new Money(100000);
        Money discounted = amount.applyRate(new BigDecimal("0.10"));
        assertThat(discounted).isEqualTo(new Money(10000));
    }

    @Test
    void zeroMoneyIsNegativeOrZero() {assertThat(Money.ZERO.isNegativeOrZero()).isTrue();}

    @Test
    void moneyEqualityByValue() {
        Money a = new Money(new BigDecimal("1000"), "KRW");
        Money b = new Money(new BigDecimal("1000"), "KRW");
        assertThat(a).isEqualTo(b);
    }
}
