def usdBidAvg = (PF1_USDTRY_bid + PF2_USDTRY_bid) / 2
def usdAskAvg = (PF1_USDTRY_ask + PF2_USDTRY_ask) / 2
def usdMid = (usdBidAvg + usdAskAvg) / 2
def eurBidAvg = (PF1_EURUSD_bid + PF2_EURUSD_bid) / 2
return usdMid * eurBidAvg
