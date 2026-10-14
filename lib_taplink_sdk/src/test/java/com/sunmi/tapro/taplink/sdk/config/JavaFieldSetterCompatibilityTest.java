package com.sunmi.tapro.taplink.sdk.config;

import com.sunmi.tapro.taplink.sdk.enums.LogLevel;
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo;
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo;
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest;
import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;

public class JavaFieldSetterCompatibilityTest {
    /** SPEC-CONFIG-001 AC1/AC3: existing Java getter/fluent descriptors remain usable. */
    @Test public void ignoredReturnAndChainsMutateOriginal() {
        TaplinkConfig config = TaplinkConfig.create("app", "key");
        config.setTimeout(30);
        assertSame(config, config.setLogEnabled(true).setLogLevel(LogLevel.DEBUG));
        assertEquals(30, config.getTimeout());
        assertTrue(config.getLogEnabled());
        AmountInfo amount = new AmountInfo(new BigDecimal("1000"), "USD", null, null, null, null, null);
        amount.setOrderAmount(new BigDecimal("2000"));
        assertSame(amount, amount.setPricingCurrency("USD").setTaxAmount(new BigDecimal("100")));
        assertEquals(new BigDecimal("2000"), amount.getOrderAmount());
        StaffInfo staff = new StaffInfo();
        staff.setOperatorId("operator");
        assertSame(staff, staff.setTipRecipientId("recipient"));
        PaymentRequest request = PaymentRequest.builder().setAction("SALE")
            .setReferenceOrderId("ORDER001").setTransactionRequestId("new-request")
            .setAmount(amount).build();
        request.setAmount(amount);
        assertSame(request, request.setStaffInfo(staff).setDescription("purchase"));
        assertSame(amount, request.getAmount());
        assertEquals("operator", request.getStaffInfo().getOperatorId());
    }
}
