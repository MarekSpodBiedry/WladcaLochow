# Analiza i model klas

Opis gry: [Opis_gry.docx](Opis_gry.docx)

## Cel

Przygotowanie kompletnego modelu obiektowego gry.

## Produkty do oddania

1. Opis architektury.
2. Definicje klas Java (bez implementacji logiki walki).

## Zakres

### Klasy bazowe

- Istota
  - silaAtaku
  - punktyZycia
  - maxPunktyZycia
  - morale
  - maxMorale
  - zloto
  - metody wspólne

### Bohaterowie

- Bohater (abstrakcyjna)
- Rycerz
- Lucznik
- Zlodziej

### Potwory

- Potwor (abstrakcyjna)
- Goblin
- Ork
- Ogr

### Ministrowie

- MinisterFinansow
  - RozrzutnyMinisterFinansow
  - ZachowawczyMinisterFinansow
- MinisterPracy
  - RamboMinisterPracy
  - BylejakiMinisterPracy

### Pozostałe klasy

- Loch
- Skarbiec
- Walka
- RundaWalki
- DzienGry

## Wymagania

- dziedziczenie,
- klasy abstrakcyjne,
- polimorfizm,
- poprawny podział odpowiedzialności,
- określenie konstruktorów oraz zasad kopiowania obiektów.

## Prezentacja

3-4 slajdy:

- diagram klas,
- hierarchia bohaterów,
- hierarchia potworów,
- hierarchia ministrów.

## Diagram

```
Istota
├── Bohater
│   ├── Rycerz
│   ├── Łucznik
│   └── Złodziej
│
└── Potwor
    ├── Goblin
    ├── Ogr
    └── Ork

MinisterFinansow
├── ZachowawczyMinisterFinansow
└── RozrzutnyMinisterFinansow

MinisterPracy
├── RamboMinisterPracy
└── BylejakiMinisterPracy

Loch
├── listaPotworow
├── skarbiec
├── ministerFinansow
└── ministerPracy

Walka
└── uczestnicy

Skarbiec
```
