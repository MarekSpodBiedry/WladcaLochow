package wladcalochow.silnik;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import wladcalochow.model.Bohater;
import wladcalochow.model.DzienGry;
import wladcalochow.model.Loch;
import wladcalochow.model.MinisterFinansow;
import wladcalochow.model.MinisterPracy;
import wladcalochow.model.Potwor;
import wladcalochow.model.strategia.RaportZwiadu;

/** Losuje to, co przynosi kolejny dzień. Trudność rośnie z numerem dnia i bogactwem skarbca. */
public final class GeneratorZdarzen {

    public static final int DNI_SPOKOJU = 2;
    public static final int MAX_BOHATEROW = 12;

    private GeneratorZdarzen() {
    }

    public static DzienGry nowyDzien(int numer, Loch loch, Random los) {
        int dochod = los.nextDouble() < 0.15 ? 0 : 20 + los.nextInt(51) + 2 * numer;

        List<Potwor> kandydaci = new ArrayList<>();
        if (los.nextDouble() >= 0.10) {
            int ilu = 2 + los.nextInt(3) + loch.getMinisterPracy().getWerbunek();
            Set<String> zajete = new HashSet<>();
            loch.getListaPotworow().forEach(p -> zajete.add(p.getImie()));
            for (int i = 0; i < ilu; i++) {
                kandydaci.add(FabrykaIstot.losowyPotwor(los, zajete));
            }
        }

        List<Bohater> bohaterowie = losujBohaterow(numer, loch.getSkarbiec().getZloto(), los);
        RaportZwiadu raport = zwiad(bohaterowie, los);
        Set<String> ministrowie = new HashSet<>();
        ministrowie.add(loch.getMinisterFinansow().getImie());
        ministrowie.add(loch.getMinisterPracy().getImie());
        return new DzienGry(numer, dochod, kandydaci, bohaterowie,
                kandydaciFinansow(los, 2, ministrowie), kandydaciPracy(los, 2, ministrowie), raport);
    }

    /** Im późniejszy dzień i pełniejszy skarbiec, tym liczniejsi i silniejsi bohaterowie. */
    static List<Bohater> losujBohaterow(int numer, int zloto, Random los) {
        List<Bohater> bohaterowie = new ArrayList<>();
        if (numer <= DNI_SPOKOJU || los.nextDouble() < 0.20) {
            return bohaterowie;
        }
        Set<String> zajete = new HashSet<>();
        double zagrozenie = 0.6 + 0.18 * numer + zloto / 500.0;
        int ilu = Math.min(MAX_BOHATEROW, (int) Math.floor(zagrozenie * (0.5 + los.nextDouble())));
        for (int i = 0; i < ilu; i++) {
            Bohater bohater = FabrykaIstot.losowyBohater(los, zajete);
            bohater.zahartuj(0.025 * (numer - 1));
            bohaterowie.add(bohater);
        }
        return bohaterowie;
    }

    /** Zwiad nie jest dokładny: podaje przedział liczebności, czasem jeden rodzaj i szacunek łapówki. */
    static RaportZwiadu zwiad(List<Bohater> bohaterowie, Random los) {
        int ilu = bohaterowie.size();
        int min = Math.max(0, ilu - los.nextInt(2));
        int max = ilu + los.nextInt(2);
        String rodzaj = null;
        int szacunek = 0;
        if (ilu > 0) {
            if (los.nextDouble() < 0.70) {
                rodzaj = bohaterowie.get(los.nextInt(ilu)).nazwaRodzaju();
            }
            szacunek = (int) Math.round(DzienGry.wartoscOddzialu(bohaterowie) * (0.8 + 0.4 * los.nextDouble()));
            min = Math.max(1, min);
        }
        return new RaportZwiadu(min, max, rodzaj, szacunek);
    }

    /** Kandydaci na Ministra Finansów; wśród nich zawsze są oba style. */
    public static List<MinisterFinansow> kandydaciFinansow(Random los, int ilu, Set<String> zajete) {
        List<MinisterFinansow> kandydaci = new ArrayList<>();
        boolean pierwszyZachowawczy = los.nextBoolean();
        for (int i = 0; i < ilu; i++) {
            boolean zachowawczy = i < 2 ? (i == 0) == pierwszyZachowawczy : los.nextBoolean();
            kandydaci.add(FabrykaIstot.ministerFinansow(los, zachowawczy, zajete));
        }
        return kandydaci;
    }

    /** Kandydaci na Ministra Pracy; wśród nich zawsze są oba style. */
    public static List<MinisterPracy> kandydaciPracy(Random los, int ilu, Set<String> zajete) {
        List<MinisterPracy> kandydaci = new ArrayList<>();
        boolean pierwszyRambo = los.nextBoolean();
        for (int i = 0; i < ilu; i++) {
            boolean rambo = i < 2 ? (i == 0) == pierwszyRambo : los.nextBoolean();
            kandydaci.add(FabrykaIstot.ministerPracy(los, rambo, zajete));
        }
        return kandydaci;
    }
}
