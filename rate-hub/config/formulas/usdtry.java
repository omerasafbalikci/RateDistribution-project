BigDecimal pf1 = m.get("PF1_USDTRY_bid");
BigDecimal pf2 = m.get("PF2_USDTRY_bid");
return pf1.add(pf2).divide(BigDecimal.valueOf(2));
