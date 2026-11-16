#!/usr/bin/env python3
"""Rysuje ikone gry: zasoby/ikona.ico (do skrotow) i zasoby/ikona.png (podglad).

Ikona to siatka 16x16 blokow, tak jak grafika w grze: na tle w kolorze tla gry
stoja same litery WL (inicjaly: Wladca Lochow) z blokow, w zlotym gradiencie z tytulu gry.

Wymaga biblioteki Pillow:  pip install pillow
Uzycie:                    python3 narzedzia/zbuduj-ikone.py
"""
import pathlib

from PIL import Image, ImageDraw

KORZEN = pathlib.Path(__file__).resolve().parent.parent
SIATKA = 16
ROZMIARY = [16, 32, 48, 64, 128, 256]

# Kolory z src/main/java/wladcalochow/ui/Paleta.java
TLO = 0x14161F
ZLOTO_GORA = 0xF7D36B
ZLOTO_DOL = 0xEC8B45

# Ksztalty liter jak w tytule gry (Aplikacja.LITERY_TYTULU): W z "Wladca" i L z "Lochow".
LITERA_W = ["#   #", "#   #", "# # #", "## ##", "#   #"]
LITERA_L = ["#    ", "#    ", "#    ", "#    ", "#####"]


def rgb(kolor):
    return ((kolor >> 16) & 255, (kolor >> 8) & 255, kolor & 255)


def mieszaj(a, b, t):
    return tuple(round(x * (1 - t) + y * t) for x, y in zip(rgb(a), rgb(b)))


def bloki():
    """Zwraca slownik (kolumna, wiersz) -> kolor dla wszystkich blokow liter."""
    wynik = {}
    # kazdy wiersz litery ma wysokosc dwoch blokow, jak wysoka komorka terminala
    for litera, kolumna0 in ((LITERA_W, 2), (LITERA_L, 9)):
        for wiersz, linia in enumerate(litera):
            for kolumna, znak in enumerate(linia):
                if znak != "#":
                    continue
                for polowka in range(2):
                    y = 3 + 2 * wiersz + polowka
                    wynik[(kolumna0 + kolumna, y)] = mieszaj(ZLOTO_GORA, ZLOTO_DOL, (y - 3) / 9)
    return wynik


def narysuj(rozmiar):
    """Rysuje ikone w podanym rozmiarze. Bloki sa ostre; od 64 px maja miedzy soba waska przerwe."""
    obraz = Image.new("RGBA", (rozmiar, rozmiar), rgb(TLO) + (255,))
    rysik = ImageDraw.Draw(obraz)
    bok = rozmiar // SIATKA
    przerwa = max(1, bok // 8) if rozmiar >= 64 else 0
    for (kolumna, wiersz), kolor in bloki().items():
        x = kolumna * bok
        y = wiersz * bok
        rysik.rectangle([x, y, x + bok - 1 - przerwa, y + bok - 1 - przerwa], fill=kolor + (255,))
    return obraz


def main():
    obrazy = {rozmiar: narysuj(rozmiar) for rozmiar in ROZMIARY}
    katalog = KORZEN / "zasoby"
    katalog.mkdir(exist_ok=True)
    najwiekszy = obrazy[ROZMIARY[-1]]
    najwiekszy.save(katalog / "ikona.png")
    najwiekszy.save(
        katalog / "ikona.ico",
        format="ICO",
        sizes=[(r, r) for r in ROZMIARY],
        append_images=[obrazy[r] for r in ROZMIARY[:-1]],
    )
    print("zapisano zasoby/ikona.ico i zasoby/ikona.png, rozmiary:", ROZMIARY)


if __name__ == "__main__":
    main()
