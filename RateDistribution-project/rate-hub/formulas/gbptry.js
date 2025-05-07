let usdBidAvg = (PF1_USDTRY_bid + PF2_USDTRY_bid) / 2;
let usdAskAvg = (PF1_USDTRY_ask + PF2_USDTRY_ask) / 2;
let usdMid = (usdBidAvg + usdAskAvg) / 2;
let gbpBidAvg = (PF1_GBPUSD_bid + PF2_GBPUSD_bid) / 2;
usdMid * gbpBidAvg;
