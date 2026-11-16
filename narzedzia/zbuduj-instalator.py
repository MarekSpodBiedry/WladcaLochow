#!/usr/bin/env python3
"""Buduje install.ps1 z czytelnego zrodla narzedzia/install.zrodlo.ps1.

Zamienia kazdy znak spoza ASCII w literalach  T '...'  na kod \\uXXXX, zeby opublikowany
skrypt byl czystym ASCII. Z opcja --sprawdz niczego nie zapisuje, tylko konczy sie bledem,
gdy install.ps1 nie odpowiada zrodlu.
"""
import pathlib
import re
import sys

KORZEN = pathlib.Path(__file__).resolve().parent.parent
ZRODLO = KORZEN / "narzedzia" / "install.zrodlo.ps1"
WYNIK = KORZEN / "install.ps1"


def zbuduj() -> str:
    zrodlo = ZRODLO.read_text(encoding="utf-8")
    for literal in re.finditer(r"T '([^']*)'", zrodlo):
        if "\\" in literal.group(1):
            sys.exit(f"Literal z ukosnikiem wstecznym zostalby zle odkodowany: {literal.group(0)}")

    def zakoduj(dopasowanie: re.Match) -> str:
        return "".join(z if ord(z) < 128 else "\\u%04x" % ord(z) for z in dopasowanie.group(0))

    wynik = re.sub(r"T '[^']*'", zakoduj, zrodlo)
    for numer, wiersz in enumerate(wynik.split("\n"), 1):
        if any(ord(z) > 127 for z in wiersz):
            sys.exit(f"Znak spoza ASCII poza literalem T '...' w wierszu {numer}: {wiersz.strip()[:80]}")
    return wynik


def main() -> None:
    wynik = zbuduj()
    if "--sprawdz" in sys.argv:
        obecny = WYNIK.read_text(encoding="ascii") if WYNIK.exists() else ""
        if obecny != wynik:
            sys.exit("install.ps1 nie odpowiada zrodlu. Uruchom: python3 narzedzia/zbuduj-instalator.py")
        print("install.ps1 jest zgodny ze zrodlem")
        return
    WYNIK.write_text(wynik, encoding="ascii", newline="\n")
    print(f"zapisano {WYNIK.name}: {len(wynik)} znakow")


if __name__ == "__main__":
    main()
