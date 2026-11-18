package wladcalochow.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import wladcalochow.model.Loch;
import wladcalochow.model.MinisterFinansow;
import wladcalochow.model.MinisterPracy;
import wladcalochow.model.Potwor;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.model.strategia.Rozkaz;
import wladcalochow.model.strategia.Ulepszenie;
import wladcalochow.model.strategia.WadaMinistra;
import wladcalochow.silnik.Gra;

/**
 * Most między grą a Claude'em, który gra za Władcę. Rano gra zapisuje do pliku stan.txt wszystko, co widzi
 * gracz (bez ukrytych danych, np. dokładnego składu bohaterów), razem z listą rozkazów do wydania.
 * Claude odpowiada plikiem decyzje.txt. Gra wykonuje rozkazy, a gracz ogląda je i sam rusza dzień.
 */
final class MostClaude {

    static final String PLIK_STANU = "stan.txt";
    static final String PLIK_DECYZJI = "decyzje.txt";
    static final String PLIK_HISTORII = "historia.txt";

    /** Odpowiedź Claude'a: rozkazy w kolejności wykonania i uzasadnienie. */
    record Decyzja(List<String> rozkazy, String powod) {
    }

    /** Wybór startowych ministrów. */
    record Start(MinisterFinansow finansow, MinisterPracy pracy, String powod) {
    }

    private final Path folder;
    private final Random los = new Random();

    MostClaude(Path folder) {
        this.folder = folder;
    }

    Path folder() {
        return folder;
    }

    // ================= stan dla Claude'a =================

    /** Zapisuje poranny stan gry. Zwraca identyfikator, który musi wrócić w odpowiedzi. */
    String wyslijPoranek(Gra gra) throws IOException {
        String id = "dzien-" + gra.getNumerDnia() + "-" + String.format("%04x", los.nextInt(0x10000));
        StringBuilder s = new StringBuilder();
        s.append("WLADCA LOCHOW: PORANEK DLA CLAUDE'A\n");
        s.append("id: ").append(id).append('\n');
        s.append("typ: PORANEK\n\n");
        opiszLoch(gra, s);
        s.append("\n== Dziennik od wczorajszego poranka ==\n");
        for (String wpis : ostatnieWpisy(gra)) {
            s.append("- ").append(wpis).append('\n');
        }
        s.append("\n== Rozkazy do wydania dziś: ").append(gra.getDzien().getPozostaleRozkazy())
                .append(" (każda linia: polecenie | koszt albo powód, dla którego nie da się go wydać) ==\n");
        for (String polecenie : wszystkiePolecenia(gra)) {
            Rozkaz rozkaz = rozkaz(polecenie, gra);
            String powod = gra.powodOdmowy(rozkaz);
            int koszt = gra.koszt(rozkaz);
            s.append(polecenie).append(" | ")
                    .append(powod != null ? "NIEDOSTĘPNE: " + powod : koszt > 0 ? "koszt " + koszt : "za darmo")
                    .append(" | ").append(rozkaz.nazwa()).append('\n');
        }
        s.append("\n== Format odpowiedzi w pliku ").append(PLIK_DECYZJI).append(" ==\n");
        s.append("id: ").append(id).append('\n');
        s.append("rozkaz: <polecenie z listy, np. POSTAWA ODWOD>   (0 do ")
                .append(gra.getDzien().getPozostaleRozkazy()).append(" linii, w kolejności wykonania)\n");
        s.append("powod: <dlaczego tak; może mieć wiele linii do końca pliku>\n");
        zapiszStan(s.toString());
        return id;
    }

    /** Zapisuje prośbę o wybór startowych ministrów nowej gry. */
    String wyslijStart(Gra gra) throws IOException {
        String id = "start-" + String.format("%04x", los.nextInt(0x10000));
        StringBuilder s = new StringBuilder();
        s.append("WLADCA LOCHOW: NOWA GRA, WYBÓR MINISTRÓW\n");
        s.append("id: ").append(id).append('\n');
        s.append("typ: START\n\n");
        s.append("Złoto na start: ").append(gra.getLoch().getSkarbiec().getZloto()).append('\n');
        s.append("Potwory na start:\n");
        opiszPotwory(gra.getLoch(), s);
        s.append("\nKandydaci na Ministra Finansów:\n");
        List<MinisterFinansow> finansow = gra.getStartowiFinansow();
        for (int i = 0; i < finansow.size(); i++) {
            s.append("MF").append(i + 1).append(": ").append(opis(finansow.get(i))).append('\n');
        }
        s.append("\nKandydaci na Ministra Pracy:\n");
        List<MinisterPracy> pracy = gra.getStartowiPracy();
        for (int i = 0; i < pracy.size(); i++) {
            s.append("MP").append(i + 1).append(": ").append(opis(pracy.get(i))).append('\n');
        }
        s.append("\n== Format odpowiedzi w pliku ").append(PLIK_DECYZJI).append(" ==\n");
        s.append("id: ").append(id).append('\n');
        s.append("start: MF<n> MP<n>\n");
        s.append("powod: <dlaczego tak>\n");
        zapiszStan(s.toString());
        return id;
    }

    private void opiszLoch(Gra gra, StringBuilder s) {
        Loch loch = gra.getLoch();
        s.append("Dzień: ").append(gra.getNumerDnia()).append('\n');
        s.append("Złoto w skarbcu: ").append(loch.getSkarbiec().getZloto()).append('\n');
        if (loch.getWydobycieDoOdebrania() > 0) {
            s.append("Z kopalni wpłynie dziś rano: +").append(loch.getWydobycieDoOdebrania()).append('\n');
        }
        s.append("Przegrane obrony z rzędu: ").append(loch.getPorazkiZRzedu()).append(" z ")
                .append(Loch.PORAZKI_DO_UPADKU).append(" (przy ").append(Loch.PORAZKI_DO_UPADKU)
                .append(". loch upada)\n");
        s.append("Postawa: ").append(loch.getPostawa().nazwa()).append('\n');
        s.append("Limit rekrutacji: ").append(loch.getLimitRekrutacji()).append("% skarbca\n");
        s.append("Minister Finansów: ").append(opis(loch.getMinisterFinansow())).append('\n');
        s.append("Minister Pracy: ").append(opis(loch.getMinisterPracy())).append('\n');
        if (gra.getNastepnyMinisterFinansow() != null) {
            s.append("Od jutra finanse: ").append(gra.getNastepnyMinisterFinansow()).append('\n');
        }
        if (gra.getNastepnyMinisterPracy() != null) {
            s.append("Od jutra kadry: ").append(gra.getNastepnyMinisterPracy()).append('\n');
        }
        s.append("Ulepszenia:");
        for (Ulepszenie ulepszenie : Ulepszenie.values()) {
            s.append(' ').append(ulepszenie.nazwa()).append(' ').append(loch.poziom(ulepszenie)).append('/')
                    .append(Ulepszenie.MAX_POZIOM).append(';');
        }
        s.append('\n');
        s.append(gra.getDzien().getRaport().opis()).append('\n');
        s.append("\nPotwory (").append(loch.getListaPotworow().size()).append('/').append(Loch.POJEMNOSC)
                .append("; co rano Minister Pracy wysyła jedną trzecią do kopalni):\n");
        opiszPotwory(loch, s);
        s.append("\nKandydaci do pracy dziś (zatrudnia Minister Pracy w ramach budżetu):\n");
        if (gra.getDzien().getKandydaci().isEmpty()) {
            s.append("(brak)\n");
        }
        for (Potwor p : gra.getDzien().getKandydaci()) {
            // Tylko to, co gracz widzi w dzienniku: imię, rodzaj i żołd (bez ukrytych statystyk).
            s.append("- ").append(p.getImie()).append(" · ").append(p.nazwaRodzaju()).append(" · żołd ")
                    .append(p.getZold()).append('\n');
        }
        s.append("\nKandydaci na ministrów dziś (zmiana działa od jutra):\n");
        List<MinisterFinansow> mf = gra.getDzien().getKandydaciFinansow();
        for (int i = 0; i < mf.size(); i++) {
            s.append("MF").append(i + 1).append(": ").append(opis(mf.get(i))).append('\n');
        }
        List<MinisterPracy> mp = gra.getDzien().getKandydaciPracy();
        for (int i = 0; i < mp.size(); i++) {
            s.append("MP").append(i + 1).append(": ").append(opis(mp.get(i))).append('\n');
        }
    }

    private static void opiszPotwory(Loch loch, StringBuilder s) {
        List<Potwor> potwory = loch.getListaPotworow();
        if (potwory.isEmpty()) {
            s.append("(nikogo)\n");
        }
        for (int i = 0; i < potwory.size(); i++) {
            Potwor p = potwory.get(i);
            s.append('P').append(i + 1).append(": ").append(p.getImie()).append(" · ").append(p.nazwaRodzaju())
                    .append(" · poziom ").append(p.getPoziom()).append(" · siła ").append(p.getSilaAtaku())
                    .append(" · życie ").append(p.getPunktyZycia()).append('/').append(p.getMaxPunktyZycia())
                    .append(" · morale ").append(p.getMorale()).append('/').append(p.getMaxMorale())
                    .append(" · żołd ").append(p.getZold())
                    .append(p.czyRanny() ? " · RANNY: nie walczy i nie kopie, wraca za " + p.getDniRekonwalescencji()
                            + " dni (żołd dalej płatny; zwolnienie rannego obniża morale reszty)" : "")
                    .append('\n');
        }
    }

    private static String opis(MinisterFinansow m) {
        return m.getImie() + " · " + m.nazwaRodzaju() + " · " + m.opisStylu() + " Pensja "
                + Aplikacja.pensja(m.getPensja(), m.ma(WadaMinistra.CHCIWY)) + ", zniżki "
                + Math.round(m.getNegocjacje() * 100) + "%." + Aplikacja.cechy(m.getCecha(), m.getWada());
    }

    private static String opis(MinisterPracy m) {
        return m.getImie() + " · " + m.nazwaRodzaju() + " · " + m.opisStylu() + " Pensja "
                + Aplikacja.pensja(m.getPensja(), m.ma(WadaMinistra.CHCIWY)) + ", werbunek +" + m.getWerbunek()
                + ", kopalnia +" + Math.round(m.getNadzorKopalni() * 100) + "%." + Aplikacja.cechy(m.getCecha(), m.getWada());
    }

    /** Wpisy dziennika od początku wczorajszego dnia (to, co gracz widzi na ekranie). */
    private static List<String> ostatnieWpisy(Gra gra) {
        List<Gra.WpisDziennika> wpisy = gra.getDziennik();
        int naglowki = 0;
        int od = 0;
        for (int i = wpisy.size() - 1; i >= 0; i--) {
            if (wpisy.get(i).tekst().startsWith("Dzień ")) {
                naglowki++;
                od = i;
                if (naglowki == 2) {
                    break;
                }
            }
        }
        List<String> wynik = new ArrayList<>();
        for (int i = od; i < wpisy.size(); i++) {
            wynik.add(wpisy.get(i).tekst());
        }
        return wynik;
    }

    // ================= polecenia =================

    /** Wszystkie polecenia, jakie da się dziś wydać (także te, na które nie stać lochu). */
    static List<String> wszystkiePolecenia(Gra gra) {
        List<String> polecenia = new ArrayList<>();
        for (Postawa postawa : Postawa.values()) {
            polecenia.add("POSTAWA " + postawa.name());
        }
        for (int procent : new int[] {0, 25, 50, 75, 100}) {
            polecenia.add("LIMIT " + procent);
        }
        for (Ulepszenie ulepszenie : Ulepszenie.values()) {
            polecenia.add("ULEPSZENIE " + ulepszenie.name());
        }
        int potwory = gra.getLoch().getListaPotworow().size();
        for (int i = 1; i <= potwory; i++) {
            polecenia.add("PREMIA P" + i);
        }
        for (int i = 1; i <= potwory; i++) {
            polecenia.add("ZWOLNIJ P" + i);
        }
        for (int i = 1; i <= potwory; i++) {
            if (gra.getLoch().getListaPotworow().get(i - 1).czyRanny()) {
                polecenia.add("SZPITAL P" + i);
            }
        }
        for (int i = 1; i <= gra.getDzien().getKandydaciFinansow().size(); i++) {
            polecenia.add("MINISTER MF" + i);
        }
        for (int i = 1; i <= gra.getDzien().getKandydaciPracy().size(); i++) {
            polecenia.add("MINISTER MP" + i);
        }
        return polecenia;
    }

    /** Zamienia polecenie na rozkaz. Numery potworów i ministrów odnoszą się do porannej listy. */
    static Rozkaz rozkaz(String polecenie, Gra gra) {
        String[] czesci = polecenie.trim().toUpperCase(Locale.ROOT).split("\\s+");
        if (czesci.length != 2) {
            throw new IllegalArgumentException("Nieznane polecenie: " + polecenie);
        }
        String argument = czesci[1];
        return switch (czesci[0]) {
            case "POSTAWA" -> new Rozkaz.ZmianaPostawy(Postawa.valueOf(argument));
            case "LIMIT" -> new Rozkaz.LimitRekrutacji(Integer.parseInt(argument));
            case "ULEPSZENIE" -> new Rozkaz.ZakupUlepszenia(Ulepszenie.valueOf(argument));
            case "PREMIA" -> new Rozkaz.Premia(element(gra.getLoch().getListaPotworow(), argument, "P"));
            case "ZWOLNIJ" -> new Rozkaz.Zwolnienie(element(gra.getLoch().getListaPotworow(), argument, "P"));
            case "SZPITAL" -> new Rozkaz.Szpital(element(gra.getLoch().getListaPotworow(), argument, "P"));
            case "MINISTER" -> argument.startsWith("MF")
                    ? new Rozkaz.ZmianaMinistraFinansow(element(gra.getDzien().getKandydaciFinansow(), argument, "MF"))
                    : new Rozkaz.ZmianaMinistraPracy(element(gra.getDzien().getKandydaciPracy(), argument, "MP"));
            default -> throw new IllegalArgumentException("Nieznane polecenie: " + polecenie);
        };
    }

    private static <T> T element(List<T> lista, String znacznik, String przedrostek) {
        if (!znacznik.startsWith(przedrostek)) {
            throw new IllegalArgumentException("Oczekiwano " + przedrostek + "<numer>, jest " + znacznik);
        }
        int numer = Integer.parseInt(znacznik.substring(przedrostek.length()));
        if (numer < 1 || numer > lista.size()) {
            throw new IllegalArgumentException("Nie ma " + znacznik);
        }
        return lista.get(numer - 1);
    }

    // ================= odpowiedź Claude'a =================

    /** Odpowiedź na poranek o podanym identyfikatorze albo pusty wynik, gdy jej jeszcze nie ma. */
    Optional<Decyzja> odbierzPoranek(String id) {
        return czytajOdpowiedz(id).map(linie -> {
            List<String> rozkazy = new ArrayList<>();
            for (String linia : linie.naglowki()) {
                if (linia.startsWith("rozkaz:")) {
                    rozkazy.add(linia.substring("rozkaz:".length()).trim());
                }
            }
            return new Decyzja(rozkazy, linie.powod());
        });
    }

    /** Wybór ministrów albo pusty wynik, gdy odpowiedzi jeszcze nie ma. */
    Optional<Start> odbierzStart(String id, Gra gra) {
        return czytajOdpowiedz(id).map(linie -> {
            for (String linia : linie.naglowki()) {
                if (linia.startsWith("start:")) {
                    String[] wybor = linia.substring("start:".length()).trim().toUpperCase(Locale.ROOT).split("\\s+");
                    if (wybor.length == 2) {
                        return new Start(element(gra.getStartowiFinansow(), wybor[0], "MF"),
                                element(gra.getStartowiPracy(), wybor[1], "MP"), linie.powod());
                    }
                }
            }
            throw new IllegalArgumentException("Brak linii start: MF<n> MP<n>");
        });
    }

    private record Odpowiedz(List<String> naglowki, String powod) {
    }

    private Optional<Odpowiedz> czytajOdpowiedz(String id) {
        Path plik = folder.resolve(PLIK_DECYZJI);
        List<String> linie;
        try {
            if (!Files.isRegularFile(plik)) {
                return Optional.empty();
            }
            linie = Files.readAllLines(plik, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Optional.empty();
        }
        List<String> naglowki = new ArrayList<>();
        StringBuilder powod = new StringBuilder();
        boolean wPowodzie = false;
        boolean zgodne = false;
        for (String surowa : linie) {
            String linia = surowa.replace("\uFEFF", "");
            if (wPowodzie) {
                powod.append('\n').append(linia);
            } else if (linia.trim().startsWith("powod:")) {
                wPowodzie = true;
                powod.append(linia.trim().substring("powod:".length()).trim());
            } else if (linia.trim().startsWith("id:")) {
                zgodne = linia.trim().substring(3).trim().equals(id);
            } else if (!linia.isBlank()) {
                naglowki.add(linia.trim());
            }
        }
        // Plik z innym identyfikatorem to odpowiedź na wcześniejszy poranek: czekamy dalej.
        return zgodne ? Optional.of(new Odpowiedz(naglowki, powod.toString().strip())) : Optional.empty();
    }

    /** Dopisuje do historii stan, na który Claude odpowiadał, i to, co z jego decyzji wyszło. */
    void zapiszHistorie(String wynik) {
        try {
            Files.createDirectories(folder);
            String stan = Files.readString(folder.resolve(PLIK_STANU), StandardCharsets.UTF_8);
            String decyzje = Files.readString(folder.resolve(PLIK_DECYZJI), StandardCharsets.UTF_8);
            String wpis = "\n######## " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n"
                    + stan + "\n-------- odpowiedź\n" + decyzje + "\n-------- wynik\n" + wynik + "\n";
            Files.writeString(folder.resolve(PLIK_HISTORII), wpis, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // historia jest pomocą przy analizie, jej brak nie przerywa gry
        }
    }

    private void zapiszStan(String tekst) throws IOException {
        Files.createDirectories(folder);
        Path tymczasowy = folder.resolve(PLIK_STANU + ".tmp");
        Files.writeString(tymczasowy, tekst, StandardCharsets.UTF_8);
        Files.move(tymczasowy, folder.resolve(PLIK_STANU), StandardCopyOption.REPLACE_EXISTING);
    }
}
