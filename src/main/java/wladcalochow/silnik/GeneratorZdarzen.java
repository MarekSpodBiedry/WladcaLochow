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
    public static final double SZANSA_ZEROWEGO_DOCHODU = 0.05;

    private GeneratorZdarzen() {
    }

    public static DzienGry nowyDzien(int numer, Loch loch, Random los) {
        return nowyDzien(numer, loch, los, false);
    }

    /** W samouczku pierwsze dni zawsze przynoszą dochód i kandydatów, a pierwszy atak jest łatwy. */
    public static DzienGry nowyDzien(int numer, Loch loch, Random los, boolean samouczek) {
        boolean lagodnie = samouczek && numer <= Samouczek.DZIEN_PIERWSZEGO_ATAKU;
        // Dochód waha się w węższym zakresie, a zerowy zdarza się rzadko: wcześniej co siódmy dzień
        // dawał 0 i loch nie miał z czego zapłacić żołdu.
        int dochod = los.nextDouble() < SZANSA_ZEROWEGO_DOCHODU && !lagodnie ? 0 : 40 + los.nextInt(31) + 2 * numer;
        if (lagodnie) {
            dochod = Math.max(40, dochod);
        }

        List<Potwor> kandydaci = new ArrayList<>();
        if (los.nextDouble() >= 0.10 || lagodnie) {
            int ilu = 2 + los.nextInt(3) + loch.getMinisterPracy().getWerbunek();
            Set<String> zajete = new HashSet<>();
            loch.getListaPotworow().forEach(p -> zajete.add(p.getImie()));
            for (int i = 0; i < ilu; i++) {
                kandydaci.add(FabrykaIstot.losowyPotwor(los, zajete));
            }
        }

        List<Bohater> bohaterowie;
        RaportZwiadu raport;
        if (samouczek && numer == Samouczek.DZIEN_PIERWSZEGO_ATAKU) {
            Bohater giermek = FabrykaIstot.giermek(los, new HashSet<>());
            bohaterowie = List.of(giermek);
            raport = new RaportZwiadu(1, 1, giermek.nazwaRodzaju(), DzienGry.wartoscOddzialu(bohaterowie));
        } else {
            bohaterowie = losujBohaterow(numer, loch.getSkarbiec().getZloto(), loch.getListaPotworow().size(), los);
            raport = zwiad(bohaterowie, los);
        }
        Set<String> ministrowie = new HashSet<>();
        ministrowie.add(loch.getMinisterFinansow().getImie());
        ministrowie.add(loch.getMinisterPracy().getImie());
        return new DzienGry(numer, dochod, kandydaci, bohaterowie,
                kandydaciFinansow(los, 2, ministrowie), kandydaciPracy(los, 2, ministrowie), raport,
                loch.getSkarbiec().getZloto());
    }

    /**
     * Im późniejszy dzień i pełniejszy skarbiec, tym liczniejsi i silniejsi bohaterowie. Liczba bohaterów
     * waha się najwyżej o 25% wokół zagrożenia i nie przekracza liczby potworów w lochu plus jeden:
     * osłabiony loch ma szansę się odbudować, zamiast od razu dostać atak sześciu bohaterów.
     */
    static List<Bohater> losujBohaterow(int numer, int zloto, int potwory, Random los) {
        List<Bohater> bohaterowie = new ArrayList<>();
        if (numer <= DNI_SPOKOJU || los.nextDouble() < 0.20) {
            return bohaterowie;
        }
        Set<String> zajete = new HashSet<>();
        double zagrozenie = 0.6 + 0.18 * numer + zloto / 500.0;
        int ilu = (int) Math.floor(zagrozenie * (0.75 + 0.5 * los.nextDouble()));
        ilu = Math.min(Math.min(MAX_BOHATEROW, potwory + 1), ilu);
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
