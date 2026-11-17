package wladcalochow.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Starcie bohaterów z obrońcami lochu, rozgrywane krok po kroku.
 * Każde wywołanie {@link #krok()} to działanie jednej istoty; wynikiem są zdarzenia do pokazania.
 */
public class Walka implements PoleWalki, Serializable {

    private static final long serialVersionUID = 1L;

    public enum Wynik { TRWA, OBRONA_UDANA, OBRONA_PRZEGRANA }

    public static final double SZANSA_TRAFIENIA = 0.85;
    public static final int MAX_RUND = 60;
    /** O jaką część maksymalnego morale spada morale bohaterów, gdy uderza odwód. */
    public static final double SPADEK_MORALE_PRZY_ZASADZCE = 0.40;

    private final List<Bohater> bohaterowie;
    private final List<Potwor> obroncy;
    private final List<Potwor> odwod;
    private final int liczebnoscPierwszejLinii;
    private final Skarbiec skarbiec;
    private final Random los;
    private final double mnoznikKradziezy;

    private RundaWalki runda;
    private int numerRundy;
    private Wynik wynik = Wynik.TRWA;

    public Walka(List<Bohater> bohaterowie, List<Potwor> obroncy, List<Potwor> odwod,
                 Skarbiec skarbiec, double mnoznikKradziezy, Random los) {
        this.bohaterowie = new ArrayList<>(bohaterowie);
        this.obroncy = new ArrayList<>(obroncy);
        this.odwod = new ArrayList<>(odwod);
        this.liczebnoscPierwszejLinii = obroncy.size();
        this.skarbiec = skarbiec;
        this.mnoznikKradziezy = mnoznikKradziezy;
        this.los = los;
        uczestnicy().forEach(Istota::przygotujDoWalki);
    }

    /** Wykonuje działanie jednej istoty i zwraca to, co się wydarzyło. */
    public List<Zdarzenie> krok() {
        List<Zdarzenie> zdarzenia = new ArrayList<>();
        if (wynik != Wynik.TRWA || rozstrzygnij(zdarzenia)) {
            return zdarzenia;
        }
        if (runda == null || runda.czyZakonczona()) {
            if (numerRundy >= MAX_RUND) {
                // Walka trwa zbyt długo: bohaterowie dają za wygraną.
                for (Bohater bohater : bohaterowie) {
                    if (bohater.wWalce()) {
                        bohater.uciekaj();
                        zdarzenia.add(new Zdarzenie.Ucieczka(bohater));
                    }
                }
                rozstrzygnij(zdarzenia);
                return zdarzenia;
            }
            numerRundy++;
            List<Istota> aktywni = new ArrayList<>(aktywni(bohaterowie));
            aktywni.addAll(aktywni(obroncy));
            runda = new RundaWalki(numerRundy, aktywni, los);
            zdarzenia.add(new Zdarzenie.NowaRunda(numerRundy));
        }
        Istota aktor = runda.nastepny();
        if (aktor != null) {
            wykonajKolejke(aktor, zdarzenia);
        }
        rozstrzygnij(zdarzenia);
        return zdarzenia;
    }

    private void wykonajKolejke(Istota aktor, List<Zdarzenie> zdarzenia) {
        if (aktor.czyOgluszona()) {
            // Ogłuszony traci kolejkę: nie może nawet uciec.
            aktor.ocknijSie();
            zdarzenia.add(new Zdarzenie.StrataKolejki(aktor));
            return;
        }
        if (aktor.czyUcieka(this)) {
            aktor.uciekaj();
            zdarzenia.add(new Zdarzenie.Ucieczka(aktor));
            return;
        }
        Akcja akcja = aktor.wybierzAkcje(this, los);
        switch (akcja) {
            case Akcja.Atak atak -> atakuj(aktor, atak.cel(), zdarzenia);
            case Akcja.ProbaKradziezy kradziez -> kradnij(aktor, kradziez, zdarzenia);
            case Akcja.Czekanie czekanie -> {
            }
        }
    }

    private void atakuj(Istota aktor, Istota cel, List<Zdarzenie> zdarzenia) {
        if (los.nextDouble() >= SZANSA_TRAFIENIA) {
            zdarzenia.add(new Zdarzenie.Atak(aktor, cel, 0, false));
            return;
        }
        int obrazenia = Math.max(1, (int) Math.round(aktor.getSilaAtaku() * (0.8 + 0.4 * los.nextDouble())));
        cel.otrzymajAtak(obrazenia);
        zdarzenia.add(new Zdarzenie.Atak(aktor, cel, obrazenia, true));
        if (!cel.czyZyje()) {
            int lup = aktor.zabierzZloto(cel);
            zdarzenia.add(new Zdarzenie.Smierc(cel, aktor, lup));
        } else {
            aktor.poTrafieniu(cel, los, zdarzenia);
        }
    }

    private void kradnij(Istota aktor, Akcja.ProbaKradziezy proba, List<Zdarzenie> zdarzenia) {
        boolean udana = !skarbiec.czyPusty() && los.nextDouble() < proba.szansa() * mnoznikKradziezy;
        int kwota = 0;
        if (udana) {
            double ulamek = (0.05 + 0.15 * los.nextDouble()) * mnoznikKradziezy;
            kwota = skarbiec.zabierz(Math.max(1, (int) Math.round(skarbiec.getZloto() * ulamek)));
            aktor.dodajZloto(kwota);
        }
        zdarzenia.add(new Zdarzenie.Kradziez(aktor, kwota, udana));
        if (proba.potemUcieczka()) {
            aktor.uciekaj();
            zdarzenia.add(new Zdarzenie.Ucieczka(aktor));
        }
    }

    /** Sprawdza, czy któraś strona przegrała. Zwraca true, jeśli walka jest zakończona. */
    private boolean rozstrzygnij(List<Zdarzenie> zdarzenia) {
        if (wynik != Wynik.TRWA) {
            return true;
        }
        if (aktywni(bohaterowie).isEmpty()) {
            wynik = Wynik.OBRONA_UDANA;
            zdarzenia.add(new Zdarzenie.KoniecWalki(true));
            return true;
        }
        // Odwód wkracza, gdy pierwsza linia stopnieje o jedną trzecią (albo całkiem padnie), i od razu uderza.
        if (!odwod.isEmpty() && aktywni(obroncy).size() * 3 <= liczebnoscPierwszejLinii * 2) {
            wprowadzOdwod(zdarzenia);
            return rozstrzygnij(zdarzenia);
        }
        if (aktywni(obroncy).isEmpty()) {
            wynik = Wynik.OBRONA_PRZEGRANA;
            zdarzenia.add(new Zdarzenie.KoniecWalki(false));
            return true;
        }
        return false;
    }

    /** Odwód dołącza do walki i każdy z jego wojowników od razu wykonuje jeden atak (uderzenie z zaskoczenia). */
    private void wprowadzOdwod(List<Zdarzenie> zdarzenia) {
        List<Potwor> wchodzacy = new ArrayList<>(odwod);
        obroncy.addAll(odwod);
        odwod.clear();
        zdarzenia.add(new Zdarzenie.Informacja("Pierwsza linia słabnie. Odwód wkracza do walki i uderza z zaskoczenia!",
                Zdarzenie.Ton.NAGLOWEK));
        // Zasadzka: bohaterowie, pewni zwycięstwa, nagle widzą świeże siły i tracą ducha.
        for (Bohater bohater : aktywni(bohaterowie)) {
            bohater.zmienMorale(-(int) Math.round(bohater.getMaxMorale() * SPADEK_MORALE_PRZY_ZASADZCE));
        }
        for (Potwor potwor : wchodzacy) {
            if (aktywni(bohaterowie).isEmpty()) {
                break;
            }
            if (potwor.wWalce() && potwor.wybierzAkcje(this, los) instanceof Akcja.Atak atak) {
                atakuj(potwor, atak.cel(), zdarzenia);
            }
        }
    }

    private static <T extends Istota> List<T> aktywni(List<T> strona) {
        return strona.stream().filter(Istota::wWalce).toList();
    }

    @Override
    public List<Istota> przeciwnicy(Istota kto) {
        return new ArrayList<>(bohaterowie.contains(kto) ? aktywni(obroncy) : aktywni(bohaterowie));
    }

    @Override
    public List<Istota> sojusznicy(Istota kto) {
        return new ArrayList<>(bohaterowie.contains(kto) ? aktywni(bohaterowie) : aktywni(obroncy));
    }

    /** Wszyscy uczestnicy walki: bohaterowie, obrońcy i odwód. */
    public List<Istota> uczestnicy() {
        List<Istota> wszyscy = new ArrayList<>(bohaterowie);
        wszyscy.addAll(obroncy);
        wszyscy.addAll(odwod);
        return wszyscy;
    }

    public List<Bohater> getBohaterowie() {
        return List.copyOf(bohaterowie);
    }

    /** Obrońcy, którzy weszli do walki (żywi, polegli i ci, którzy uciekli). */
    public List<Potwor> getObroncy() {
        return List.copyOf(obroncy);
    }

    public List<Potwor> getOdwod() {
        return List.copyOf(odwod);
    }

    public boolean czyZakonczona() {
        return wynik != Wynik.TRWA;
    }

    public Wynik getWynik() {
        return wynik;
    }

    public int getNumerRundy() {
        return numerRundy;
    }
}
