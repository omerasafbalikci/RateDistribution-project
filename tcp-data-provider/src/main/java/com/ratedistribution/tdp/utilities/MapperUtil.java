package com.ratedistribution.tdp.utilities;

import com.ratedistribution.tdp.config.*;
import com.ratedistribution.tdp.model.GarchParams;
import com.ratedistribution.tdp.model.MultiRateDefinition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapperUtil {
    @SuppressWarnings("unchecked")
    public static List<MultiRateDefinition> parseRatesList(List<?> list) {
        List<MultiRateDefinition> result = new ArrayList<>();
        for(Object o : list) {
            if(o instanceof Map<?,?> m) {
                MultiRateDefinition def = new MultiRateDefinition();
                def.setRateName(strVal(m.get("rateName")));
                def.setInitialPrice(dblVal(m.get("initialPrice")));
                def.setDrift(dblVal(m.get("drift")));
                def.setBaseSpread(dblVal(m.get("baseSpread")));
                def.setGarchParams(parseGarch((Map<String, Object>)m.get("garchParams")));
                def.setJumpIntensity(dblVal(m.get("jumpIntensity")));
                def.setJumpMean(dblVal(m.get("jumpMean")));
                def.setJumpVol(dblVal(m.get("jumpVol")));
                def.setUseMeanReversion(boolVal(m.get("useMeanReversion")));
                def.setKappa(dblVal(m.get("kappa")));
                def.setTheta(dblVal(m.get("theta")));
                result.add(def);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static GarchParams parseGarch(Map<String, Object> gmap) {
        if(gmap == null) return null;
        GarchParams g = new GarchParams();
        g.setOmega(dblVal(gmap.get("omega")));
        g.setAlpha(dblVal(gmap.get("alpha")));
        g.setBeta(dblVal(gmap.get("beta")));
        return g;
    }

    public static List<SessionVolFactor> parseSessionFactors(List<?> list) {
        List<SessionVolFactor> sf = new ArrayList<>();
        for(Object o : list) {
            if(o instanceof Map<?,?> m) {
                SessionVolFactor f = new SessionVolFactor();
                f.setStartHour(intVal(m.get("startHour")));
                f.setEndHour(intVal(m.get("endHour")));
                f.setVolMultiplier(dblVal(m.get("volMultiplier")));
                sf.add(f);
            }
        }
        return sf;
    }

    public static List<HolidayDefinition> parseHolidays(List<?> list) {
        List<HolidayDefinition> hd = new ArrayList<>();
        for(Object o : list) {
            if(o instanceof Map<?,?> m) {
                HolidayDefinition h = new HolidayDefinition();
                h.setName(strVal(m.get("name")));
                h.setStartDateTime(Instant.parse(strVal(m.get("startDateTime"))));
                h.setEndDateTime(Instant.parse(strVal(m.get("endDateTime"))));
                hd.add(h);
            }
        }
        return hd;
    }

    public static WeekendHandling parseWeekendHandling(Map<?,?> m) {
        WeekendHandling w = new WeekendHandling();
        w.setEnabled(boolVal(m.get("enabled")));
        w.setWeekendGapJumpMean(dblVal(m.get("weekendGapJumpMean")));
        w.setWeekendGapJumpVol(dblVal(m.get("weekendGapJumpVol")));
        return w;
    }

    public static RegimeDefinition parseRegimeDef(Map<?,?> m) {
        RegimeDefinition r = new RegimeDefinition();
        r.setVolScale(dblVal(m.get("volScale")));
        r.setMeanDuration(intVal(m.get("meanDuration")));
        r.setTransitionProb(dblVal(m.get("transitionProb")));
        return r;
    }

    private static double dblVal(Object o) {
        if(o instanceof Number n) return n.doubleValue();
        return 0.0;
    }
    private static int intVal(Object o) {
        if(o instanceof Number n) return n.intValue();
        return 0;
    }
    private static boolean boolVal(Object o) {
        if(o instanceof Boolean b) return b;
        return false;
    }
    private static String strVal(Object o) {
        return (o != null) ? o.toString() : "";
    }
}