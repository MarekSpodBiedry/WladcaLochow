package wladcalochow.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import wladcalochow.model.Zdarzenie.Informacja;
import wladcalochow.model.Zdarzenie.Ton;
import wladcalochow.model.strategia.CechaMinistra;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.model.strategia.RaportZwiadu;
import wladcalochow.model.strategia.Ulepszenie;
import wladcalochow.model.strategia.WadaMinistra;

/**
 * Jeden dzień gry. Przechowuje to, co dany dzień przynosi (dochód, kandydaci, bohaterowie),
 * i rozgrywa kolejne fazy: poranek (dochód, rekrutacja, kopalnia), obronę i wieczór (żołd).
 */
public class DzienGry implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Faza { PORANEK, OBRONA, WIECZOR, ZAKONCZONY }

    public static final int ROZKAZY_NA_DZIEN = 2;
    /** Szansa, że pokonany w walce potwór przeżyje ciężko ranny zamiast zginąć. */
    public static final double SZANSA_PRZEZYCIA = 0.35;
    /** Ile poranków ciężko ranny dochodzi do siebie: wraca w 3-5 poranek, czyli opuszcza 2-4 dni. */
    public static final int MIN_PORANKOW_RANNEGO = 2;
    public static final int MAX_PORANKOW_RANNEGO = 4;

    private final int numer;
    /** O ile silniejsi są bohaterowie, gdy loch chciał ich przekupić, a zabrakło złota. */
    public static final double OSMIELENIE = 0.15;
    /** Postawa Przekupstwo, ale zabrakło złota: ta walka nie zeruje ceny łapówki. */
    private boolean lapowkaNieudana;
    /** Stażysta finansów wygadał się w karczmie o skarbcu: dzisiejszy atak jest silniejszy. */
    private boolean wygadanySkarbiec;
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
    private boolean przychodPrzyjety;
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

    /**
     * Przypływ złota: dochód lochu i urobek z wczorajszej kopalni. Dzieje się na samym początku dnia,
     * jeszcze przed rozkazami Władcy, żeby rano dało się wydać to, co właśnie wpłynęło.
     */
    public List<Zdarzenie> przyjmijPrzychod(Loch loch) {
        wymagaj(Faza.PORANEK);
        if (przychodPrzyjety) {
            return List.of();
        }
        przychodPrzyjety = true;
        List<Zdarzenie> zdarzenia = new ArrayList<>();
        if (loch.getMinisterFinansow().ma(CechaMinistra.LICHWIARZ)) {
            int odsetki = Math.min(MAX_ODSETEK, (int) Math.round(loch.getSkarbiec().getZloto() * ODSETKI));
            if (odsetki > 0) {
                loch.getSkarbiec().wplac(odsetki);
                bilans.przychod(odsetki, 0);
                zdarzenia.add(new Informacja("Lichwiarz " + loch.getMinisterFinansow().getImie() + " pomnaża skarbiec: +"
                        + odsetki + " złota odsetek.", Ton.ZLOTO));
            }
        }
        int wydobycie = loch.odbierzWydobycie();
        loch.getSkarbiec().wplac(dochodBazowy + wydobycie);
        bilans.przychod(dochodBazowy, wydobycie);
        zdarzenia.add(new Informacja("Przypływ złota: +" + (dochodBazowy + wydobycie)
                + " (dochód " + dochodBazowy + ", kopalnia " + wydobycie + ").", Ton.ZLOTO));
        return zdarzenia;
    }

    /** Odsetki Lichwiarza: część skarbca dziennie, najwyżej {@value #MAX_ODSETEK} złota. */
    public static final double ODSETKI = 0.02;
    /** O ile tańsze są łapówki u Negocjatora. */
    public static final double RABAT_NEGOCJATORA = 0.25;
    public static final int MAX_ODSETEK = 150;
    /** Dzienna szansa, że minister nielojalny odejdzie bez ostrzeżenia. */
    public static final double SZANSA_ODEJSCIA_NIELOJALNEGO = 0.03;

    public List<Zdarzenie> rozegrajPoranek(Loch loch, Random los) {
        wymagaj(Faza.PORANEK);
        List<Zdarzenie> zdarzenia = new ArrayList<>(przyjmijPrzychod(loch));

        odejsciaNielojalnych(loch, los, zdarzenia);
        wyplacPensje(loch, zdarzenia);
        wpadkiStazystow(loch, los, zdarzenia);
        rekrutuj(loch, zdarzenia);

        boolean medyk = loch.getMinisterPracy().ma(CechaMinistra.MEDYK);
        List<Potwor> wyzdrowiali = new ArrayList<>();
        for (Potwor potwor : loch.getListaPotworow()) {
            // Medyk stawia rannych na nogi dwa razy szybciej: dwa poranki rekonwalescencji w jeden.
            boolean wrocil = potwor.dochodzDoSiebie();
            if (!wrocil && medyk) {
                wrocil = potwor.dochodzDoSiebie();
            }
            if (wrocil) {
                wyzdrowiali.add(potwor);
            }
        }
        if (!wyzdrowiali.isEmpty()) {
            zdarzenia.add(new Informacja("Po ranach wracają do sił: " + imiona(wyzdrowiali) + ".", Ton.DOBRY));
        }
        loch.getListaPotworow().forEach(p -> p.setWKopalni(false));
        // Ciężko ranni nie kopią i nie walczą: Minister Pracy wybiera górników spośród zdolnych do pracy.
        List<Potwor> gornicy = loch.getMinisterPracy().wybierzDoKopalni(loch.zdolni(), los);
        gornicy.forEach(p -> p.setWKopalni(true));
        if (!gornicy.isEmpty()) {
            zdarzenia.add(new Informacja("Do kopalni idą: " + imiona(gornicy) + ".", Ton.NEUTRALNY));
        }
        faza = Faza.OBRONA;
        return zdarzenia;
    }

    private void odejsciaNielojalnych(Loch loch, Random los, List<Zdarzenie> zdarzenia) {
        if (loch.getMinisterFinansow().ma(WadaMinistra.NIELOJALNY) && los.nextDouble() < SZANSA_ODEJSCIA_NIELOJALNEGO) {
            bilans.odszedlMinister(loch.getMinisterFinansow().getImie() + " (finanse, nielojalny)");
            zdarzenia.add(new Informacja(loch.getMinisterFinansow().getImie() + " znika bez słowa (nielojalny). "
                    + "Finanse przejmuje darmowy stażysta, który lubi się wygadać.", Ton.ALARM));
            loch.setMinisterFinansow(new RozrzutnyMinisterFinansow("Stażysta Wicek", 0, 0.0));
        }
        if (loch.getMinisterPracy().ma(WadaMinistra.NIELOJALNY) && los.nextDouble() < SZANSA_ODEJSCIA_NIELOJALNEGO) {
            bilans.odszedlMinister(loch.getMinisterPracy().getImie() + " (kadry, nielojalny)");
            zdarzenia.add(new Informacja(loch.getMinisterPracy().getImie() + " znika bez słowa (nielojalny). "
                    + "Kadry przejmuje darmowa stażystka, która myli listy.", Ton.ALARM));
            loch.setMinisterPracy(new BylejakiMinisterPracy("Stażystka Bronka", 0, 0, 0.0));
        }
    }

    public boolean czyWygadanySkarbiec() {
        return wygadanySkarbiec;
    }

    public void wygadanySkarbiec() {
        wygadanySkarbiec = true;
    }

    /** Dzienna szansa, że stażystka kadr pomyli listy i zwolni potwora. */
    public static final double SZANSA_WPADKI_STAZYSTKI = 0.07;

    /**
     * Darmowa stażystka nie jest za darmo: czasem zwalnia przez pomyłkę. Bez wpadek opłacało się celowo nie
     * płacić pensji ministrom (pomiar: same stażyści 215 dni wobec 176 z płatnymi ministrami bez wymiany).
     */
    private void wpadkiStazystow(Loch loch, Random los, List<Zdarzenie> zdarzenia) {
        MinisterPracy pracy = loch.getMinisterPracy();
        List<Potwor> zdolni = loch.zdolni();
        if (pracy.czyStazysta() && !zdolni.isEmpty() && los.nextDouble() < SZANSA_WPADKI_STAZYSTKI) {
            Potwor zwolniony = zdolni.get(los.nextInt(zdolni.size()));
            loch.usun(zwolniony);
            bilans.zwolnionyPrzezPomylke(zwolniony);
            zdarzenia.add(new Informacja(pracy.getImie() + " pomyliła listy i zwolniła z lochu: "
                    + zwolniony.getImie() + ".", Ton.ALARM));
        }
    }

    private void wyplacPensje(Loch loch, List<Zdarzenie> zdarzenia) {
        Skarbiec skarbiec = loch.getSkarbiec();
        MinisterFinansow finansow = loch.getMinisterFinansow();
        int pensjaFinansow = finansow.pensjaDnia(bilans.przychody());
        if (skarbiec.wyplac(pensjaFinansow)) {
            bilans.pensja(pensjaFinansow);
        } else {
            loch.setMinisterFinansow(new RozrzutnyMinisterFinansow("Stażysta Wicek", 0, 0.0));
            bilans.odszedlMinister(finansow.getImie() + " (finanse, brak pensji)");
            zdarzenia.add(new Informacja("Zabrakło na pensję: " + finansow.getImie() + " odchodzi. "
                    + "Finanse przejmuje darmowy stażysta, który lubi się wygadać.", Ton.ALARM));
        }
        MinisterPracy pracy = loch.getMinisterPracy();
        int pensjaPracy = pracy.pensjaDnia(bilans.przychody());
        if (skarbiec.wyplac(pensjaPracy)) {
            bilans.pensja(pensjaPracy);
        } else {
            loch.setMinisterPracy(new BylejakiMinisterPracy("Stażystka Bronka", 0, 0, 0.0));
            bilans.odszedlMinister(pracy.getImie() + " (kadry, brak pensji)");
            zdarzenia.add(new Informacja("Zabrakło na pensję: " + pracy.getImie() + " odchodzi. "
                    + "Kadry przejmuje darmowa stażystka, która myli listy.", Ton.ALARM));
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
                loch.zanotujLapowke();
                zdarzenia.add(new Informacja("Bohaterowie biorą " + cena + " złota łapówki i odchodzą bez walki. "
                        + "Następnym razem zażądają więcej.", Ton.ZLOTO));
                return null;
            }
            // Kto prosi, żeby nie walczyć, a potem nie płaci, wychodzi na słabego: bohaterowie atakują śmielej.
            lapowkaNieudana = true;
            bohaterowie.forEach(b -> b.zahartuj(OSMIELENIE));
            zdarzenia.add(new Informacja("Na łapówkę (" + cena + " złota) nie starczyło. Bohaterowie wiedzą już, "
                    + "że w skarbcu pusto: atakują śmielej (+" + Math.round(OSMIELENIE * 100) + "% siły), "
                    + "a cena łapówki nie spadnie.", Ton.ALARM));
        }

        int pulapki = loch.poziom(Ulepszenie.PULAPKI);
        if (pulapki > 0) {
            for (Bohater bohater : bohaterowie) {
                bohater.zran((int) Math.round(bohater.getMaxPunktyZycia() * Ulepszenie.PULAPKI_NA_POZIOM * pulapki));
            }
            zdarzenia.add(new Informacja("Pułapki ranią bohaterów (-" + Math.round(Ulepszenie.PULAPKI_NA_POZIOM * 100 * pulapki)
                    + "% życia).", Ton.DOBRY));
        }

        List<Potwor> wojownicy = loch.wojownicy();
        if (wojownicy.isEmpty()) {
            int lup = skarbiec.zabierz(skarbiec.getZloto());
            loch.zanotujWynikObrony(false, !lapowkaNieudana);
            bilans.pladrowanie(lup);
            bilans.obrona(BilansDnia.Obrona.BEZ_OBRONCOW);
            zdarzenia.add(new Informacja("Nikt nie broni lochu! Bohaterowie wynoszą cały skarbiec: "
                    + lup + " złota.", Ton.ALARM));
            return null;
        }

        List<Potwor> pierwszaLinia = new ArrayList<>(wojownicy);
        List<Potwor> odwod = new ArrayList<>();
        if (loch.getPostawa() == Postawa.ODWOD) {
            // Jedna trzecia najcenniejszych wojowników (najwyższy poziom, potem największa moc) czeka z tyłu.
            odwod = wojownicy.stream()
                    .sorted(Comparator.comparingInt(Potwor::getPoziom).thenComparingInt(Potwor::moc).reversed())
                    .limit(wojownicy.size() / 3)
                    .collect(Collectors.toList());
            pierwszaLinia.removeAll(odwod);
            if (!odwod.isEmpty()) {
                zdarzenia.add(new Informacja("W odwodzie czekają: " + imiona(odwod) + ".", Ton.NEUTRALNY));
            }
        }
        zlotoPrzedWalka = skarbiec.getZloto();
        walka = new Walka(bohaterowie, pierwszaLinia, odwod, skarbiec, loch.mnoznikKradziezy(), los);
        return walka;
    }

    /**
     * Ile kosztuje przekupienie dzisiejszych bohaterów: ich wartość, droższa o każdą wcześniejszą łapówkę
     * bez walki pomiędzy, po negocjacjach Ministra Finansów.
     */
    public int kosztPrzekupstwa(Loch loch) {
        return cenaPrzekupstwa(loch, bohaterowie);
    }

    /** Cena łapówki dla podanego oddziału (zwiad liczy z niej widełki, zanim powstanie dzień). */
    public static int cenaPrzekupstwa(Loch loch, List<? extends Istota> oddzial) {
        return loch.getMinisterFinansow().cenaPoNegocjacjach(
                (int) Math.round(wartoscOddzialu(oddzial) * loch.mnoznikLapowki()
                        * (loch.getMinisterFinansow().ma(CechaMinistra.NEGOCJATOR) ? 1 - RABAT_NEGOCJATORA : 1)));
    }

    /**
     * Miara siły oddziału: od niej zależy cena łapówki i siła ataku na loch. Prosty wzór 2 × siła + życie / 2
     * jest poprawiony mnożnikiem rodzaju (np. gobliny ogłuszają, ogry nie tracą morale).
     */
    public static int wartoscOddzialu(List<? extends Istota> oddzial) {
        return (int) Math.round(oddzial.stream()
                .mapToDouble(b -> (2 * b.getSilaAtaku() + b.getMaxPunktyZycia() / 2.0) * b.mnoznikWartosci())
                .sum());
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
        loch.zanotujWynikObrony(udana, !lapowkaNieudana);
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
        List<Potwor> ranni = new ArrayList<>();
        for (Potwor potwor : walka.getObroncy()) {
            if (!potwor.czyZyje() && los.nextDouble() < SZANSA_PRZEZYCIA) {
                // Część pokonanych przeżywa: loch nie traci w jednej walce całej inwestycji.
                potwor.przezyjRanna();
                // Liczone w porankach: przez 2-4 dni nie walczy i nie kopie, ale żołd należy mu się dalej.
                potwor.zranCiezko(MIN_PORANKOW_RANNEGO + los.nextInt(MAX_PORANKOW_RANNEGO - MIN_PORANKOW_RANNEGO + 1));
                ranni.add(potwor);
            } else if (!potwor.czyZyje()) {
                polegli.add(potwor);
                loch.usun(potwor);
                bilans.polegl(potwor);
            } else if (potwor.podsumujWalke(loch.getMinisterPracy().ma(CechaMinistra.TRENER))) {
                bilans.awans(potwor);
                zdarzenia.add(new Informacja(potwor.getImie() + " awansuje na poziom " + potwor.getPoziom()
                        + " (żołd " + potwor.getZold() + ").", Ton.DOBRY));
            }
        }
        if (!ranni.isEmpty()) {
            zdarzenia.add(new Informacja("Przeżyli ciężko ranni: " + imiona(ranni)
                    + ". Przez kilka dni nie będą walczyć ani kopać, a bohaterowie wyczują słabość lochu.", Ton.ALARM));
        }
        if (!polegli.isEmpty()) {
            zdarzenia.add(new Informacja("Polegli w obronie lochu: " + imiona(polegli) + ".", Ton.ZLY));
        }
        // Łupy zdobyte przez ocalałe potwory trafiają do skarbca.
        int lupy = 0;
        for (Potwor potwor : loch.getListaPotworow()) {
            lupy += potwor.oddajZloto();
        }
        if (lupy > 0) {
            skarbiec.wplac(lupy);
            bilans.lupy(lupy);
            zdarzenia.add(new Informacja("Łupy z walki trafiają do skarbca: +" + lupy + " złota.", Ton.ZLOTO));
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
                loch.getMinisterFinansow().wyplacZold(loch.getSkarbiec(), loch.naZoldzie(), los);
        if (!wynik.oplaceni().isEmpty()) {
            zdarzenia.add(new Informacja("Żołd: wypłacono " + wynik.wyplacono() + " złota, opłaconych potworów: "
                    + wynik.oplaceni().size() + ".", Ton.ZLOTO));
        }
        if (!wynik.nieoplaceni().isEmpty()) {
            zdarzenia.add(new Informacja("Zabrakło żołdu dla: " + imiona(wynik.nieoplaceni())
                    + ". Ich morale spada.", Ton.ALARM));
        }
        if (!wynik.odeszli().isEmpty()) {
            wynik.odeszli().forEach(loch::usun);
            zdarzenia.add(new Informacja("Bez żołdu i z fatalnym morale odchodzą z lochu: "
                    + imiona(wynik.odeszli()) + ".", Ton.ALARM));
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
