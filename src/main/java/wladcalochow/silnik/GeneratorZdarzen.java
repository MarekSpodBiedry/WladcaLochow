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
            bohaterowie = losujBohaterow(numer, loch.getSkarbiec().getZloto(), loch.getListaPotworow(), los);
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
     * Siła ataku rośnie razem z siłą lochu, a nie bez końca z numerem dnia. Bohaterowie dobierają się tak,
     * żeby ich łączna wartość (ta sama miara co cena łapówki) była ułamkiem wartości obrońców lochu.
     * Ułamek rośnie przez pierwsze {@value #DNI_DO_PELNEJ_GROZY} dni, waha się losowo, a czasem przychodzi
     * fala znacznie silniejsza niż zwykle. Bogaty skarbiec przyciąga dodatkowych bohaterów.
     * Dzięki temu dobrze prowadzony loch może trwać bez końca, ale pech i złe decyzje go niszczą.
     */
    static List<Bohater> losujBohaterow(int numer, int zloto, List<Potwor> potwory, Random los) {
        List<Bohater> bohaterowie = new ArrayList<>();
        if (numer <= DNI_SPOKOJU || los.nextDouble() < 0.20) {
            return bohaterowie;
        }
        Set<String> zajete = new HashSet<>();
        // Walczą mniej więcej dwie trzecie załogi (reszta kopie).
        double obrona = DzienGry.wartoscOddzialu(potwory) * 2.0 / 3.0;
        double rozwoj = Math.min(1.0, numer / (double) DNI_DO_PELNEJ_GROZY);
        double mnoznik = (GROZA_POCZATKOWA + (GROZA_PELNA - GROZA_POCZATKOWA) * rozwoj) * (0.75 + 0.5 * los.nextDouble());
        if (los.nextDouble() < SZANSA_FALI) {
            mnoznik *= SILA_FALI;
        }
        double minimum = Math.min(MINIMUM_POZNE, MINIMUM_POCZATKOWE + MINIMUM_NA_DZIEN * numer);
        double cel = Math.max(minimum, obrona * mnoznik) + zloto / ZLOTO_NA_PUNKT_ZAGROZENIA;
        double hartowanie = Math.min(MAX_HARTOWANIE, HARTOWANIE_NA_DZIEN * (numer - 1));
        int wartosc = 0;
        while (wartosc < cel && bohaterowie.size() < MAX_BOHATEROW) {
            Bohater bohater = FabrykaIstot.losowyBohater(los, zajete);
            bohater.zahartuj(hartowanie);
            bohaterowie.add(bohater);
            wartosc += DzienGry.wartoscOddzialu(List.of(bohater));
        }
        return bohaterowie;
    }

    /** Po tylu dniach groza ataków osiąga pełny poziom względem siły lochu. */
    static final int DNI_DO_PELNEJ_GROZY = 40;
    /** Wartość bohaterów jako ułamek wartości obrońców: na początku gry i po {@link #DNI_DO_PELNEJ_GROZY} dniach. */
    static final double GROZA_POCZATKOWA = 0.45;
    static final double GROZA_PELNA = 1.30;
    /** Czasem przychodzi fala: atak dużo silniejszy niż zwykle (pech, na który trzeba mieć zapas). */
    static final double SZANSA_FALI = 0.12;
    static final double SILA_FALI = 1.9;
    /** Najmniejsza wartość ataku: nawet pusty loch jest napadany, a z czasem coraz mocniej, do pewnej granicy. */
    static final double MINIMUM_POCZATKOWE = 30;
    static final double MINIMUM_NA_DZIEN = 2.5;
    static final double MINIMUM_POZNE = 250;
    /** Ile złota w skarbcu przyciąga dodatkowy punkt wartości ataku. */
    static final double ZLOTO_NA_PUNKT_ZAGROZENIA = 8;
    /** Bohaterowie twardnieją z dniami, ale tylko do pewnej granicy. */
    static final double HARTOWANIE_NA_DZIEN = 0.01;
    static final double MAX_HARTOWANIE = 0.40;

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
