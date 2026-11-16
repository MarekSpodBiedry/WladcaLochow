package wladcalochow.ui;

import java.util.List;

/**
 * Teksty samouczka i pomocy. Doradca odzywa się w pierwszych dniach gry:
 * rano tłumaczy, co dziś nowego, a wieczorem wyjaśnia, co się wydarzyło.
 */
final class Doradca {

    /** Jedna strona wprowadzenia albo pomocy. */
    record Strona(String tytul, List<String> akapity) {
    }

    /** Przedstawienie gry w menu głównym: o co chodzi i dlaczego warto zagrać. */
    static final List<Strona> O_GRZE = List.of(
            new Strona("Odwrócona bajka", List.of(
                    "W zwykłych grach jesteś bohaterem, który schodzi do lochu po skarb. Tutaj jest odwrotnie: "
                            + "loch jest Twój, skarb jest Twój, a bohaterowie to włamywacze.",
                    "Masz skarbiec pełen złota i gromadę potworów na żołdzie: tchórzliwe gobliny z procami, "
                            + "twarde orki i ogry, które idą przez walkę jak czołg.",
                    "Nie machasz mieczem i nie przesuwasz potworów po planszy. Ty rządzisz: rano podejmujesz "
                            + "jedną albo dwie decyzje, a potem patrzysz, jak dzień rozgrywa się sam.")),
            new Strona("Twój dzień", List.of(
                    "Rano decydujesz. Kupić pułapki? Dać premię potworowi, któremu puszczają nerwy? "
                            + "Wymienić ministra? A może zapłacić bohaterom, żeby sobie poszli?",
                    "Potem loch pracuje: wpływa złoto, ministrowie zatrudniają potwory i wysyłają co trzeciego "
                            + "do kopalni.",
                    "Jeśli przyjdą bohaterowie, potwory stają pod bramą. Walkę oglądasz ruch po ruchu "
                            + "albo przewijasz do końca.",
                    "Wieczorem płacisz żołd i dostajesz rachunek: ile złota przyszło, na co poszło "
                            + "i kto nie dożył wieczora.")),
            new Strona("Dlaczego warto", List.of(
                    "Chciwość kontra bezpieczeństwo: złota potrzebujesz na żołd i ulepszenia, ale pełny skarbiec "
                            + "ściąga coraz groźniejszych gości.",
                    "Ministrowie mają charakter: jeden chomikuje złoto, drugi wydaje wszystko od ręki. "
                            + "To Ty wybierasz, komu oddać loch.",
                    "Potwory mają imiona. Kto przeżyje walkę, staje się weteranem: bije mocniej, ale chce "
                            + "więcej złota. Strata takiego boli.",
                    "Nie da się wygrać, da się tylko przetrwać dłużej. Ile dni wytrzymasz? "
                            + "Zacznij od gry z samouczkiem: niczego nie musisz umieć.")));

    static final List<Strona> STRONY = List.of(
            new Strona("Kim jesteś", List.of(
                    "Jesteś Władcą Lochów. Masz skarbiec ze złotem i potwory na żołdzie. "
                            + "Bohaterowie chcą to złoto zabrać.",
                    "Cel: przetrwać jak najwięcej dni. Loch upada po trzech przegranych obronach z rzędu.",
                    "Nie walczysz sam i nie sterujesz potworami. Ty rządzisz: wybierasz ministrów "
                            + "i wydajesz rozkazy, a reszta dzieje się sama.")),
            new Strona("Jak mija dzień", List.of(
                    "1 Rozkazy: rano decydujesz Ty. Masz najwyżej 2 rozkazy dziennie.",
                    "2 Rekrutacja: wpływa złoto, ministrowie zatrudniają potwory i wysyłają co trzeciego "
                            + "do kopalni.",
                    "3 Obrona: jeśli przyjdą bohaterowie, pozostałe potwory bronią lochu.",
                    "4 Żołd: wieczorem wojownicy dostają wypłatę. Złoto z kopalni wpłynie następnego ranka.")),
            new Strona("Co widzisz na ekranie", List.of(
                    "Na górze: numer dnia, złoto, rozkazy ◆ i przegrane obrony ■.",
                    "Po prawej: ministrowie i potwory. ★ to poziom, ♥ życie, ☻ morale.",
                    "Na dole: przyciski. Naciśnij literę z przycisku albo kliknij go myszką. "
                            + "Zielony przycisk (Enter) zawsze przesuwa grę dalej.",
                    "Nad dziennikiem zawsze jest podpowiedź, co możesz teraz zrobić.")),
            new Strona("Na co uważać", List.of(
                    "Żołd: potwór bez wypłaty traci morale, a z morale poniżej 10% odchodzi z lochu.",
                    "Bogactwo: im więcej złota w skarbcu i im późniejszy dzień, tym silniejsi bohaterowie.",
                    "Weterani: kto przeżyje walkę, rośnie w siłę, ale chce wyższego żołdu.",
                    "Loch mieści najwyżej 12 potworów. Kopiący złoto nie walczą i nie biorą żołdu.")));

    private Doradca() {
    }

    /** Poranna rada na dany dzień samouczka albo pusta lista, gdy doradca nie ma nic do powiedzenia. */
    static List<String> poranek(int dzien) {
        return switch (dzien) {
            case 1 -> List.of(
                    "Witaj w lochu, Władco! Na górze ekranu widzisz numer dnia i złoto w skarbcu. "
                            + "Po prawej są Twoi ministrowie i potwory.",
                    "Dziś niczego nie musisz decydować. Naciśnij Enter albo kliknij zielony przycisk, "
                            + "a dzień rozegra się sam. Popatrz, co się wydarzy.");
            case 2 -> List.of(
                    "Nowość: Ulepszenia (klawisz U). Masz dziś 1 rozkaz, czyli jedną decyzję.",
                    "Kopalnia daje więcej złota, pułapki ranią bohaterów, a zamek chroni skarbiec "
                            + "przed kradzieżą. Rozkazu nie trzeba wykorzystać: złoto przyda się też na żołd.");
            case 3 -> List.of(
                    "Dziś pierwszy atak! Zwiad melduje jednego rycerza. Twoje potwory poradzą sobie z nim same.",
                    "Nowość: Postawa (klawisz P). Decyduje, jak loch reaguje na atak: pełna obrona, odwód "
                            + "dla potworów z niskim morale albo przekupienie bohaterów. Dziś wystarczy pełna obrona.");
            case 4 -> List.of(
                    "Nowości: Potwory (O) i Budżet (B).",
                    "Potworowi możesz dać premię, żeby podnieść morale, albo go zwolnić, żeby nie płacić żołdu. "
                            + "Budżet ogranicza, ile złota minister wyda na nowe potwory.",
                    "Uwaga: bogactwo przyciąga bohaterów. Im więcej złota w skarbcu i im późniejszy dzień, "
                            + "tym silniejszy atak.");
            case 5 -> List.of(
                    "Ostatnia nowość: Ministrowie (M). Codziennie zgłaszają się nowi kandydaci. "
                            + "Wymiana kosztuje i działa od jutra.",
                    "Od dziś masz 2 rozkazy dziennie i wszystkie możliwości. To koniec samouczka. "
                            + "Pomoc jest zawsze pod klawiszem H. Powodzenia!");
            default -> List.of();
        };
    }

    /** Rada na początku pierwszej walki. */
    static List<String> walka() {
        return List.of(
                "To jest walka. Bohaterowie stoją po lewej, Twoi obrońcy po prawej, przed bramą.",
                "Walka toczy się sama. A wyłącza automat: wtedy Enter wykonuje ruch jednej postaci. "
                        + "F przewija walkę do końca.",
                "♥ to życie, ☻ to morale. Każde trafienie obniża morale; kto je straci, ucieka z pola walki.");
    }

    /** Wieczorny komentarz doradcy na dany dzień samouczka albo null. */
    static String wieczor(int dzien) {
        return switch (dzien) {
            case 1 -> "Tak wygląda każdy dzień: wpłynął dochód, minister zatrudnił nowe potwory, co trzeci "
                    + "poszedł kopać złoto, a wieczorem wojownicy dostali żołd. Urobek z kopalni wpłynie jutro rano.";
            case 2 -> "Pilnuj, żeby starczało na żołd. Potwór bez wypłaty traci morale, a z morale poniżej 10% "
                    + "odchodzi z lochu. Jutro spodziewaj się pierwszego gościa.";
            case 3 -> "Kto przeżył walkę, zdobywa doświadczenie i z czasem awansuje (★): rośnie w siłę, ale chce "
                    + "wyższego żołdu. Trzy przegrane obrony z rzędu oznaczają koniec gry.";
            case 4 -> "Czytaj poranny raport zwiadu w dzienniku: mówi, ilu bohaterów się zbliża "
                    + "i ile kosztowałoby ich przekupienie.";
            default -> null;
        };
    }
}
