package wladcalochow.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import wladcalochow.model.Zdarzenie.Informacja;
import wladcalochow.model.Zdarzenie.Ton;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.model.strategia.RaportZwiadu;
import wladcalochow.model.strategia.Ulepszenie;

/**
 * Jeden dzień gry. Przechowuje to, co dany dzień przynosi (dochód, kandydaci, bohaterowie),
 * i rozgrywa kolejne fazy: poranek (dochód, rekrutacja, kopalnia), obronę i wieczór (żołd).
 */
public class DzienGry implements Serializable {

    public enum Faza { PORANEK, OBRONA, WIECZOR, ZAKONCZONY }

    public static final int ROZKAZY_NA_DZIEN = 2;

    private final int numer;
    private final int dochodBazowy;
    private final List<Potwor> kandydaci;
    private final List<Bohater> bohaterowie;
    private final List<MinisterFinansow> kandydaciFinansow;
    private final List<MinisterPracy> kandydaciPracy;
    private final RaportZwiadu raport;
    private final BilansDnia bilans;

    private int zlotoPrzedWalka;
    private int limitRozkazow = ROZKAZY_NA_DZIEN;
    private int pozostaleRozkazy = ROZKAZY_NA_DZIEN;
    private Faza faza = Faza.PORANEK;
    private Walka walka;

    public DzienGry(int numer, int dochodBazowy, List<Potwor> kandydaci, List<Bohater> bohaterowie,
                    List<MinisterFinansow> kandydaciFinansow, List<MinisterPracy> kandydaciPracy,
                    RaportZwiadu raport, int zlotoRano) {
        this.numer = numer;
        this.dochodBazowy = Math.max(0, dochodBazowy);
        this.kandydaci = new ArrayList<>(kandydaci);
        this.bohaterowie = new ArrayList<>(bohaterowie);
        this.kandydaciFinansow = new ArrayList<>(kandydaciFinansow);
        this.kandydaciPracy = new ArrayList<>(kandydaciPracy);
        this.raport = raport;
        this.bilans = new BilansDnia(zlotoRano);
    }

    // ---- faza 1 i 2: przypływ złota, pensje ministrów, rekrutacja, przydział do kopalni ----

    public List<Zdarzenie> rozegrajPoranek(Loch loch, Random los) {
        wymagaj(Faza.PORANEK);
        List<Zdarzenie> zdarzenia = new ArrayList<>();
        Skarbiec skarbiec = loch.getSkarbiec();

        int wydobycie = loch.odbierzWydobycie();
        skarbiec.wplac(dochodBazowy + wydobycie);
        bilans.przychod(dochodBazowy, wydobycie);
        zdarzenia.add(new Informacja("Przypływ złota: +" + (dochodBazowy + wydobycie)
                + " (dochód " + dochodBazowy + ", kopalnia " + wydobycie + ").", Ton.ZLOTO));

        wyplacPensje(loch, zdarzenia);
        rekrutuj(loch, zdarzenia);

        loch.getListaPotworow().forEach(p -> p.setWKopalni(false));
        List<Potwor> gornicy = loch.getMinisterPracy().wybierzDoKopalni(loch.getListaPotworow(), los);
        gornicy.forEach(p -> p.setWKopalni(true));
        if (!gornicy.isEmpty()) {
            zdarzenia.add(new Informacja("Do kopalni idą: " + imiona(gornicy) + ".", Ton.NEUTRALNY));
        }
        faza = Faza.OBRONA;
        return zdarzenia;
    }

    private void wyplacPensje(Loch loch, List<Zdarzenie> zdarzenia) {
        Skarbiec skarbiec = loch.getSkarbiec();
        MinisterFinansow finansow = loch.getMinisterFinansow();
        if (skarbiec.wyplac(finansow.getPensja())) {
            bilans.pensja(finansow.getPensja());
        } else {
            loch.setMinisterFinansow(new RozrzutnyMinisterFinansow("Stażysta Wicek", 0, 0.0));
            zdarzenia.add(new Informacja("Zabrakło na pensję: " + finansow.getImie() + " odchodzi. "
                    + "Finanse przejmuje darmowy stażysta.", Ton.ZLY));
        }
        MinisterPracy pracy = loch.getMinisterPracy();
        if (skarbiec.wyplac(pracy.getPensja())) {
            bilans.pensja(pracy.getPensja());
        } else {
            loch.setMinisterPracy(new BylejakiMinisterPracy("Stażystka Bronka", 0, 0, 0.0));
            zdarzenia.add(new Informacja("Zabrakło na pensję: " + pracy.getImie() + " odchodzi. "
                    + "Kadry przejmuje darmowa stażystka.", Ton.ZLY));
        }
    }

    private void rekrutuj(Loch loch, List<Zdarzenie> zdarzenia) {
        if (kandydaci.isEmpty()) {
            zdarzenia.add(new Informacja("Dziś nikt nie zgłosił się do pracy w lochu.", Ton.NEUTRALNY));
            return;
        }
        if (loch.wolneMiejsca() == 0) {
            zdarzenia.add(new Informacja("Loch jest pełny (" + Loch.POJEMNOSC
                    + " potworów), nikogo nie zatrudniono.", Ton.NEUTRALNY));
            return;
        }
        Skarbiec skarbiec = loch.getSkarbiec();
        MinisterFinansow finansow = loch.getMinisterFinansow();
        MinisterPracy pracy = loch.getMinisterPracy();

        int kwota = finansow.kwotaNaRekrutacje(skarbiec.getZloto(), loch.dziennyKosztZoldu());
        int limit = (int) ((long) skarbiec.getZloto() * loch.getLimitRekrutacji() / 100);
        int budzet = Math.max(0, Math.min(kwota, limit));
        skarbiec.wyplac(budzet);

        List<Potwor> wybrani = pracy.wybierzDoZatrudnienia(kandydaci, budzet);
        if (wybrani.size() > loch.wolneMiejsca()) {
            wybrani = new ArrayList<>(wybrani.subList(0, loch.wolneMiejsca()));
        }
        int koszt = wybrani.stream().mapToInt(Potwor::getZold).sum();
        if (koszt > budzet) {
            throw new IllegalStateException("Minister Pracy przekroczył budżet rekrutacji");
        }
        skarbiec.wplac(budzet - koszt);
        wybrani.forEach(loch::zatrudnij);
        bilans.rekrutacja(koszt, wybrani);

        String kto = wybrani.isEmpty() ? "nikogo" : imiona(wybrani) + " (koszt " + koszt + ")";
        zdarzenia.add(new Informacja("Rekrutacja: budżet " + budzet + " złota, "
                + pracy.getImie() + " zatrudnia: " + kto + ".", wybrani.isEmpty() ? Ton.NEUTRALNY : Ton.DOBRY));
    }

    // ---- faza 3: obrona przed bohaterami ----

    /** Przygotowuje obronę. Zwraca walkę do rozegrania albo null, gdy do walki nie dochodzi. */
    public Walka przygotujObrone(Loch loch, Random los, List<Zdarzenie> zdarzenia) {
        wymagaj(Faza.OBRONA);
        faza = Faza.WIECZOR;
        if (bohaterowie.isEmpty()) {
            zdarzenia.add(new Informacja("Spokojny dzień: żaden bohater nie zaatakował lochu.", Ton.DOBRY));
            bilans.obrona(BilansDnia.Obrona.SPOKOJ);
            return null;
        }
        zdarzenia.add(new Informacja("Atak! Pod bramą stają: " + imionaZRodzajem(bohaterowie) + ".", Ton.NAGLOWEK));
        Skarbiec skarbiec = loch.getSkarbiec();

        if (loch.getPostawa() == Postawa.PRZEKUPSTWO) {
            int cena = kosztPrzekupstwa(loch);
            if (skarbiec.wyplac(cena)) {
                bilans.lapowka(cena);
                bilans.obrona(BilansDnia.Obrona.PRZEKUPSTWO);
                zdarzenia.add(new Informacja("Bohaterowie biorą " + cena + " złota łapówki i odchodzą bez walki.",
                        Ton.ZLOTO));
                return null;
            }
            zdarzenia.add(new Informacja("Na łapówkę (" + cena + " złota) nie starczyło. Loch musi się bronić.",
                    Ton.ZLY));
        }

        int pulapki = loch.poziom(Ulepszenie.PULAPKI);
        if (pulapki > 0) {
            for (Bohater bohater : bohaterowie) {
                bohater.zran((int) Math.round(bohater.getMaxPunktyZycia() * 0.10 * pulapki));
            }
            zdarzenia.add(new Informacja("Pułapki ranią bohaterów (-" + 10 * pulapki + "% życia).", Ton.DOBRY));
        }

        List<Potwor> wojownicy = loch.wojownicy();
        if (wojownicy.isEmpty()) {
            int lup = skarbiec.zabierz(skarbiec.getZloto());
            loch.zanotujWynikObrony(false);
            bilans.pladrowanie(lup);
            bilans.obrona(BilansDnia.Obrona.BEZ_OBRONCOW);
            zdarzenia.add(new Informacja("Nikt nie broni lochu! Bohaterowie wynoszą cały skarbiec: "
                    + lup + " złota.", Ton.ZLY));
            return null;
        }

        List<Potwor> pierwszaLinia = new ArrayList<>(wojownicy);
        List<Potwor> odwod = new ArrayList<>();
        if (loch.getPostawa() == Postawa.ODWOD) {
            odwod = wojownicy.stream().filter(p -> p.procentMorale() < 0.50).collect(Collectors.toList());
            if (odwod.size() < wojownicy.size()) {
                pierwszaLinia.removeAll(odwod);
            } else {
                odwod = new ArrayList<>();
            }
            if (!odwod.isEmpty()) {
                zdarzenia.add(new Informacja("W odwodzie czekają: " + imiona(odwod) + ".", Ton.NEUTRALNY));
            }
        }
        zlotoPrzedWalka = skarbiec.getZloto();
        walka = new Walka(bohaterowie, pierwszaLinia, odwod, skarbiec, loch.mnoznikKradziezy(), los);
        return walka;
    }

    /** Ile kosztuje przekupienie dzisiejszych bohaterów (po negocjacjach Ministra Finansów). */
    public int kosztPrzekupstwa(Loch loch) {
        return loch.getMinisterFinansow().cenaPoNegocjacjach(wartoscOddzialu(bohaterowie));
    }

    /** Miara siły oddziału bohaterów, od której zależy cena łapówki. */
    public static int wartoscOddzialu(List<? extends Istota> oddzial) {
        return oddzial.stream().mapToInt(b -> 2 * b.getSilaAtaku() + b.getMaxPunktyZycia() / 2).sum();
    }

    /** Skutki zakończonej walki: plądrowanie, polegli, doświadczenie weteranów. */
    public List<Zdarzenie> zakonczObrone(Loch loch, Random los) {
        List<Zdarzenie> zdarzenia = new ArrayList<>();
        if (walka == null) {
            return zdarzenia;
        }
        if (!walka.czyZakonczona()) {
            throw new IllegalStateException("Walka jeszcze trwa");
        }
        Skarbiec skarbiec = loch.getSkarbiec();
        boolean udana = walka.getWynik() == Walka.Wynik.OBRONA_UDANA;
        loch.zanotujWynikObrony(udana);
        bilans.obrona(udana ? BilansDnia.Obrona.WYGRANA : BilansDnia.Obrona.PRZEGRANA);
        bilans.kradziez(zlotoPrzedWalka - skarbiec.getZloto());
        if (walka.getWynik() == Walka.Wynik.OBRONA_PRZEGRANA) {
            int lup = 0;
            for (Bohater bohater : walka.getBohaterowie()) {
                if (bohater.wWalce()) {
                    double ulamek = (0.20 + 0.15 * los.nextDouble()) * loch.mnoznikKradziezy();
                    int kwota = skarbiec.zabierz((int) Math.ceil(skarbiec.getZloto() * ulamek));
                    bohater.dodajZloto(kwota);
                    lup += kwota;
                }
            }
            bilans.pladrowanie(lup);
            zdarzenia.add(new Informacja("Zwycięzcy plądrują skarbiec i wynoszą " + lup + " złota.", Ton.ZLY));
        }

        List<Potwor> polegli = new ArrayList<>();
        for (Potwor potwor : walka.getObroncy()) {
            if (!potwor.czyZyje()) {
                polegli.add(potwor);
                loch.usun(potwor);
                bilans.polegl(potwor);
            } else if (potwor.podsumujWalke()) {
                bilans.awans(potwor);
                zdarzenia.add(new Informacja(potwor.getImie() + " awansuje na poziom " + potwor.getPoziom()
                        + " (żołd " + potwor.getZold() + ").", Ton.DOBRY));
            }
        }
        if (!polegli.isEmpty()) {
            zdarzenia.add(new Informacja("Polegli w obronie lochu: " + imiona(polegli) + ".", Ton.ZLY));
        }
        // Uciekinierzy wracają do lochu, ogłuszeni dochodzą do siebie.
        loch.getListaPotworow().forEach(Istota::odpocznijPoWalce);
        return zdarzenia;
    }

    // ---- faza 4: wydobycie, wypłata żołdu, leczenie ----

    public List<Zdarzenie> rozegrajWieczor(Loch loch, Random los) {
        wymagaj(Faza.WIECZOR);
        List<Zdarzenie> zdarzenia = new ArrayList<>();

        List<Potwor> gornicy = loch.gornicy();
        int wydobycie = 0;
        if (!gornicy.isEmpty()) {
            double suma = gornicy.stream().mapToDouble(Potwor::wartoscWydobycia).sum();
            wydobycie = (int) Math.round(suma * loch.mnoznikWydobycia());
            loch.dodajWydobycie(wydobycie);
            zdarzenia.add(new Informacja("Kopalnia: górników " + gornicy.size() + ", wykopali " + wydobycie
                    + " złota (wpłynie jutro rano).", Ton.ZLOTO));
        }

        MinisterFinansow.WynikWyplaty wynik =
                loch.getMinisterFinansow().wyplacZold(loch.getSkarbiec(), loch.wojownicy(), los);
        if (!wynik.oplaceni().isEmpty()) {
            zdarzenia.add(new Informacja("Żołd: wypłacono " + wynik.wyplacono() + " złota, opłaconych potworów: "
                    + wynik.oplaceni().size() + ".", Ton.ZLOTO));
        }
        if (!wynik.nieoplaceni().isEmpty()) {
            zdarzenia.add(new Informacja("Zabrakło żołdu dla: " + imiona(wynik.nieoplaceni())
                    + ". Ich morale spada.", Ton.ZLY));
        }
        if (!wynik.odeszli().isEmpty()) {
            wynik.odeszli().forEach(loch::usun);
            zdarzenia.add(new Informacja("Bez żołdu i z fatalnym morale odchodzą z lochu: "
                    + imiona(wynik.odeszli()) + ".", Ton.ZLY));
        }

        bilans.wieczor(wynik.wyplacono(), wynik.nieoplaceni(), wynik.odeszli(), wydobycie,
                loch.getSkarbiec().getZloto());

        for (Potwor potwor : loch.getListaPotworow()) {
            potwor.wylecz();
            potwor.setWKopalni(false);
        }
        faza = Faza.ZAKONCZONY;
        return zdarzenia;
    }

    // ---- rozkazy ----

    /** Ustawia, ile rozkazów wolno wydać tego dnia (w samouczku mniej niż zwykle). */
    public void ustawLimitRozkazow(int limit) {
        if (faza != Faza.PORANEK || pozostaleRozkazy != limitRozkazow) {
            throw new IllegalStateException("Limit rozkazów ustawia się przed pierwszym rozkazem");
        }
        limitRozkazow = Math.max(0, limit);
        pozostaleRozkazy = limitRozkazow;
    }

    public int getLimitRozkazow() {
        return limitRozkazow;
    }

    public boolean maRozkazy() {
        return pozostaleRozkazy > 0 && faza == Faza.PORANEK;
    }

    /** Zużywa jeden rozkaz i zapisuje jego koszt w bilansie dnia. */
    public void zuzyjRozkaz(int koszt) {
        if (!maRozkazy()) {
            throw new IllegalStateException("Brak rozkazów na dziś");
        }
        pozostaleRozkazy--;
        bilans.rozkaz(koszt);
    }

    public BilansDnia getBilans() {
        return bilans;
    }

    private void wymagaj(Faza oczekiwana) {
        if (faza != oczekiwana) {
            throw new IllegalStateException("Faza dnia to " + faza + ", a oczekiwano " + oczekiwana);
        }
    }

    private static String imiona(List<? extends Istota> istoty) {
        return istoty.stream().map(Istota::getImie).collect(Collectors.joining(", "));
    }

    private static String imionaZRodzajem(List<? extends Istota> istoty) {
        return istoty.stream().map(i -> i.getImie() + " (" + i.nazwaRodzaju() + ")")
                .collect(Collectors.joining(", "));
    }

    public int getNumer() {
        return numer;
    }

    public int getDochodBazowy() {
        return dochodBazowy;
    }

    public List<Potwor> getKandydaci() {
        return List.copyOf(kandydaci);
    }

    public List<Bohater> getBohaterowie() {
        return List.copyOf(bohaterowie);
    }

    public List<MinisterFinansow> getKandydaciFinansow() {
        return List.copyOf(kandydaciFinansow);
    }

    public List<MinisterPracy> getKandydaciPracy() {
        return List.copyOf(kandydaciPracy);
    }

    public RaportZwiadu getRaport() {
        return raport;
    }

    public int getPozostaleRozkazy() {
        return pozostaleRozkazy;
    }

    public Faza getFaza() {
        return faza;
    }

    public Walka getWalka() {
        return walka;
    }
}
