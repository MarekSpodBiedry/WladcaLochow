package wladcalochow.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zamienia zdania z dziennika na krótką etykietę i samą treść, żeby dziennik czytało się jak tabelę:
 * „Złoto   +306 · dochód 55 · kopalnia 251” zamiast „Przypływ złota: +306 (dochód 55, kopalnia 251).”.
 * Zdania, których nie zna, zostają bez zmian (bez etykiety).
 */
final class SkrotDziennika {

    /** Wpis po skróceniu: etykieta (może być pusta) i treść. */
    record Skrot(String etykieta, String tresc) {
    }

    private static final Map<Pattern, Function<Matcher, Skrot>> WZORY = new LinkedHashMap<>();

    private static void wzor(String regex, Function<Matcher, Skrot> skrot) {
        WZORY.put(Pattern.compile(regex), skrot);
    }

    static {
        wzor("Przypływ złota: \\+(\\d+) \\(dochód (\\d+), kopalnia (\\d+)\\)\\.",
                m -> new Skrot("Złoto", "+" + m.group(1) + "  · dochód " + m.group(2) + " · kopalnia " + m.group(3)));
        wzor("Zwiad: okolica spokojna, nikt się nie zbliża\\.", m -> new Skrot("Zwiad", "spokój, nikt nie idzie"));
        wzor("Zwiad: (.*)", m -> new Skrot("Zwiad", zwiad(m.group(1))));
        wzor("Kandydaci do pracy: brak\\.", m -> new Skrot("Kandydaci", "brak"));
        wzor("Kandydaci do pracy: (.*)\\.", m -> new Skrot("Kandydaci", kandydaci(m.group(1))));
        wzor("Rozkaz: (.*?)\\. Koszt: (\\d+) złota\\.", m -> new Skrot("Rozkaz", m.group(1) + " · −" + m.group(2)));
        wzor("Rozkaz: (.*)\\.", m -> new Skrot("Rozkaz", m.group(1)));
        wzor("Rekrutacja: budżet (\\d+) złota, .* zatrudnia: nikogo\\.",
                m -> new Skrot("Zatrudnieni", "nikt (budżet " + m.group(1) + ")"));
        wzor("Rekrutacja: budżet (\\d+) złota, .* zatrudnia: (.*) \\(koszt (\\d+)\\)\\.",
                m -> new Skrot("Zatrudnieni", m.group(2) + " · −" + m.group(3)));
        wzor("Loch jest pełny \\(\\d+ potworów\\), nikogo nie zatrudniono\\.",
                m -> new Skrot("Zatrudnieni", "nikt, loch pełny"));
        wzor("Dziś nikt nie zgłosił się do pracy w lochu\\.", m -> new Skrot("Zatrudnieni", "nikt się nie zgłosił"));
        wzor("Do kopalni idą: (.*)\\.", m -> new Skrot("Do kopalni", m.group(1)));
        wzor("Po ranach wracają do sił: (.*)\\.", m -> new Skrot("Wyleczeni", m.group(1)));
        wzor("Spokojny dzień: .*", m -> new Skrot("Atak", "nikt nie przyszedł"));
        wzor("Atak! Pod bramą stają: (.*)\\.", m -> new Skrot("Atak", atak(m.group(1))));
        wzor("Pułapki ranią bohaterów \\(-(\\d+)% życia\\)\\.",
                m -> new Skrot("Pułapki", "bohaterowie −" + m.group(1) + "% życia"));
        wzor("W odwodzie czekają: (.*)\\.", m -> new Skrot("Odwód", "czekają: " + m.group(1)));
        wzor("Pierwsza linia słabnie\\..*", m -> new Skrot("Odwód", "wchodzi do walki z zaskoczenia"));
        wzor("Loch obroniony!", m -> new Skrot("Wynik", "obroniony"));
        wzor("Obrona przełamana!", m -> new Skrot("Wynik", "PRZEGRANA"));
        wzor("Bohaterowie biorą (\\d+) złota łapówki.*",
                m -> new Skrot("Łapówka", "−" + m.group(1) + ", odeszli bez walki (następna droższa)"));
        wzor("Na łapówkę \\((\\d+) złota\\) nie starczyło.*",
                m -> new Skrot("Łapówka", "nie starczyło " + m.group(1) + " złota, trzeba walczyć"));
        wzor("Nikt nie broni lochu! Bohaterowie wynoszą cały skarbiec: (\\d+) złota\\.",
                m -> new Skrot("Wynik", "nikt nie bronił, skarbiec wyniesiony (−" + m.group(1) + ")"));
        wzor("Zwycięzcy plądrują skarbiec i wynoszą (\\d+) złota\\.", m -> new Skrot("Plądrowanie", "−" + m.group(1)));
        wzor("Polegli w obronie lochu: (.*)\\.", m -> new Skrot("Polegli", m.group(1)));
        wzor("Przeżyli ciężko ranni: (.*?)\\. Przez kilka dni.*",
                m -> new Skrot("Ranni", m.group(1) + " (kilka dni poza walką, ataki silniejsze)"));
        wzor("(.*) awansuje na poziom (\\d+) \\(żołd (\\d+)\\)\\.",
                m -> new Skrot("Awans", m.group(1) + " → poziom " + m.group(2) + ", żołd " + m.group(3)));
        wzor("Łupy z walki trafiają do skarbca: \\+(\\d+) złota\\.", m -> new Skrot("Łupy", "+" + m.group(1)));
        wzor("Kopalnia: górników (\\d+), wykopali (\\d+) złota \\(wpłynie jutro rano\\)\\.",
                m -> new Skrot("Urobek", "+" + m.group(2) + " jutro rano (górników " + m.group(1) + ")"));
        wzor("Żołd: wypłacono (\\d+) złota, opłaconych potworów: (\\d+)\\.",
                m -> new Skrot("Żołd", "−" + m.group(1) + " (" + m.group(2) + " potworów)"));
        wzor("Zabrakło żołdu dla: (.*)\\. Ich morale spada\\.", m -> new Skrot("Bez żołdu", m.group(1) + " (morale spada)"));
        wzor("Bez żołdu i z fatalnym morale odchodzą z lochu: (.*)\\.", m -> new Skrot("Odeszli", m.group(1)));
        // Stare brzmienie („z rzędu”) zostaje dla dzienników z zapisów sprzed zmiany napisu.
        wzor("(?:Przegrane obrony z rzędu|Porażki bez wygranej): (\\d+) z (\\d+)\\..*",
                m -> new Skrot("Porażki", m.group(1) + " z " + m.group(2) + " bez wygranej, przy " + m.group(2)
                        + ". loch upada"));
        wzor("(?:Trzecia przegrana obrona z rzędu|Trzecia porażka bez wygranej).*",
                m -> new Skrot("Koniec", "trzecia porażka bez wygranej, loch zdobyty"));
        wzor("Loch opustoszał.*", m -> new Skrot("Koniec", "brak potworów i złota"));
        wzor("(Finanse|Kadry) obejmuje (.*)\\.", m -> new Skrot("Minister", m.group(2) + " (" + m.group(1).toLowerCase() + ")"));
        wzor("Zabrakło na pensję: (.*) odchodzi\\. (.*)\\.", m -> new Skrot("Minister", m.group(1) + " odchodzi bez pensji. "
                + m.group(2)));
        wzor("(.*) znika bez słowa \\(nielojalny\\)\\. (.*)\\.", m -> new Skrot("Minister", m.group(1)
                + " odszedł (nielojalny). " + m.group(2)));
        wzor("Lichwiarz .* pomnaża skarbiec: \\+(\\d+) złota odsetek\\.", m -> new Skrot("Odsetki", "+" + m.group(1)));
        wzor("Claude: (.*)", m -> new Skrot("Claude", m.group(1)));
    }

    private SkrotDziennika() {
    }

    static Skrot skroc(String tekst) {
        for (Map.Entry<Pattern, Function<Matcher, Skrot>> wzor : WZORY.entrySet()) {
            Matcher m = wzor.getKey().matcher(tekst);
            if (m.matches()) {
                return wzor.getValue().apply(m);
            }
        }
        return new Skrot("", tekst);
    }

    /** „spodziewana liczba bohaterów: 1-6. Wśród nich na pewno: Łucznik. Przekupienie … 206 złota. Ocena: groźny.” */
    private static String zwiad(String tekst) {
        StringBuilder s = new StringBuilder();
        Matcher liczba = Pattern.compile("spodziewana liczba bohaterów: ([\\d-]+)\\.").matcher(tekst);
        if (liczba.find()) {
            s.append(liczba.group(1).replace('-', '–')).append(" bohaterów");
        }
        Matcher pewny = Pattern.compile("Wśród nich na pewno: ([^.]+)\\.").matcher(tekst);
        if (pewny.find()) {
            s.append(" · w tym ").append(pewny.group(1).toLowerCase());
        }
        Matcher lapowka = Pattern.compile("około (\\d+) złota").matcher(tekst);
        if (lapowka.find()) {
            s.append(" · łapówka ").append(lapowka.group(1));
        }
        Matcher ocena = Pattern.compile("Ocena: ([^.]+)\\.").matcher(tekst);
        if (ocena.find()) {
            s.append(" · ").append(ocena.group(1).toUpperCase());
        }
        return s.isEmpty() ? tekst : s.toString();
    }

    /** „Chrzęst (Goblin, żołd 11), Kark (Ork, żołd 25)” → „Chrzęst goblin 11 · Kark ork 25”. */
    private static String kandydaci(String tekst) {
        Matcher m = Pattern.compile("\\s*([^,(]+?) \\((\\p{L}+), żołd (\\d+)\\)").matcher(tekst);
        StringBuilder s = new StringBuilder();
        while (m.find()) {
            s.append(s.isEmpty() ? "" : " · ").append(m.group(1)).append(' ')
                    .append(m.group(2).toLowerCase()).append(' ').append(m.group(3));
        }
        return s.isEmpty() ? tekst : s.toString();
    }

    /** „Sir X (Rycerz), Y (Łucznik), Z (Łucznik)” → „3: łucznik ×2, rycerz ×1” (kolejność pierwszego pojawienia). */
    private static String atak(String tekst) {
        Map<String, Integer> rodzaje = new LinkedHashMap<>();
        Matcher m = Pattern.compile("\\((\\p{L}+)\\)").matcher(tekst);
        int razem = 0;
        while (m.find()) {
            rodzaje.merge(m.group(1).toLowerCase(), 1, Integer::sum);
            razem++;
        }
        if (razem == 0) {
            return tekst;
        }
        StringBuilder s = new StringBuilder(razem + " bohaterów: ");
        boolean pierwszy = true;
        for (Map.Entry<String, Integer> r : rodzaje.entrySet()) {
            s.append(pierwszy ? "" : ", ").append(r.getKey()).append(" ×").append(r.getValue());
            pierwszy = false;
        }
        return s.toString();
    }
}
