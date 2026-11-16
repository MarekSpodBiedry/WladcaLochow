# Władca Lochów

Terminalowa gra strategiczna w Javie. Jesteś Władcą Podziemnych Lochów: zatrudniasz potwory,
kopiesz złoto i bronisz skarbca przed bohaterami. Nie sterujesz potworami w walce, tylko decydujesz,
kto rządzi lochem i jaką prowadzi politykę.

![Walka pod bramą lochu](docs/podglad-walka.png)

Obrazki to podgląd wyrenderowany poza terminalem; dokładny wygląd zależy od czcionki terminala.

## Uruchomienie

Wymagania: Java 21 lub nowsza i Maven.

```bash
mvn package
```

```bash
java -jar target/wladca-lochow.jar
```

Terminal musi mieć co najmniej 108 × 30 znaków, obsługiwać Unicode i emoji. Najlepiej wygląda
w terminalu z kolorem 24-bitowym (Windows Terminal, iTerm2, kitty, WezTerm, terminal VS Code,
GNOME Terminal). W pozostałych gra sama przechodzi na 256 kolorów.

Opcje:

| Opcja | Znaczenie |
|---|---|
| `--ziarno LICZBA` | powtarzalna rozgrywka: to samo ziarno i te same decyzje dają ten sam przebieg |
| `--zapis PLIK` | inne miejsce zapisu gry (domyślnie `~/.wladcalochow/zapis.dat`) |
| zmienna `WLADCA_KOLORY=256` lub `=24` | wymuszenie trybu kolorów |

## Sterowanie

- **Klawiatura:** litery i Enter widoczne na przyciskach u dołu ekranu; w oknach wyboru strzałki,
  Enter, cyfry 1-9 i Esc. Strzałki góra/dół przewijają dziennik.
- **Myszka:** kliknięcie w przycisk albo w opcję okna. Sam ruch myszki niczego nie robi.

## Zasady w skrócie

Dzień składa się z czterech faz:

1. **Poranek.** Widzisz raport zwiadu i listę kandydatów. Wydajesz najwyżej 2 rozkazy.
2. **Dochód i rekrutacja.** Wpływa złoto, ministrowie pobierają pensje, Minister Finansów wyznacza
   budżet, a Minister Pracy zatrudnia potwory i wysyła jedną trzecią załogi do kopalni.
3. **Obrona.** Bohaterowie atakują, walka toczy się rundami (krok po kroku albo automatycznie).
4. **Wieczór.** Górnicy oddają urobek (wpłynie rano), wojownicy dostają żołd, wszyscy są leczeni.

Rozkazy Władcy:

| Rozkaz | Skutek |
|---|---|
| Ministrowie | zmiana Ministra Finansów lub Pracy; płatna, działa od jutra |
| Budżet | limit, jaką część skarbca wolno oddać na rekrutację |
| Postawa | pełna obrona, odwód (słabe morale czeka z tyłu) albo przekupstwo |
| Potwory | premia podnosząca morale albo zwolnienie |
| Ulepszenia | kopalnia, pułapki, zamek skarbca; każde ma 3 poziomy |

Gra nie ma końca: bohaterów przyciąga bogactwo, a z każdym dniem są silniejsi. Loch upada po trzech
przegranych obronach z rzędu albo gdy nie ma już ani potworów, ani złota. Wynikiem jest liczba
przetrwanych dni. Gra zapisuje się sama każdego ranka i po każdym rozkazie.

Szczegółowy opis istot, ministrów i walki: [docs/Opis_gry.docx](docs/Opis_gry.docx).

## Budowa kodu

| Pakiet | Zawartość |
|---|---|
| `wladcalochow.model` | klasy z zadania: `Istota`, bohaterowie, potwory, ministrowie, `Loch`, `Skarbiec`, `Walka`, `RundaWalki`, `DzienGry` |
| `wladcalochow.model.strategia` | rozkazy, postawa, ulepszenia, raport zwiadu |
| `wladcalochow.silnik` | `Gra` (stan rozgrywki), generator zdarzeń, fabryka istot, zapis gry |
| `wladcalochow.ui` | terminal: bufor ekranu, sceneria, pulpit, okna, klawiatura i myszka |

Model nie zna terminala: zwraca zdarzenia, a interfejs je tylko pokazuje. Istoty są unikatowe
i nigdy nie są kopiowane.

## Testy

```bash
mvn test
```

Testy sprawdzają zasady istot i ministrów, przebieg walki, fazy dnia, zapis gry oraz symulują
kilkadziesiąt pełnych rozgrywek z losowymi rozkazami.
