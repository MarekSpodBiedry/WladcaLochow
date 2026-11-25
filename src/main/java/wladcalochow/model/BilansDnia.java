package wladcalochow.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Podsumowanie jednego dnia: skąd przyszło złoto, na co poszło i co stało się z załogą. */
public class BilansDnia implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Obrona { NIEROZEGRANA, SPOKOJ, PRZEKUPSTWO, WYGRANA, PRZEGRANA, BEZ_OBRONCOW }

    private final int zlotoRano;
    private int zlotoWieczorem;
    private int dochod;
    private int kopalnia;
    private int lupy;
    private int pensje;
    private int rekrutacja;
    private int rozkazy;
    private int lapowka;
    private int skradzione;
    private int spladrowane;
    private int zold;
    private int wydobycieNaJutro;
    private Obrona obrona = Obrona.NIEROZEGRANA;
    private final List<String> zatrudnieni = new ArrayList<>();
    private final List<String> polegli = new ArrayList<>();
    private final List<String> odeszli = new ArrayList<>();
    /** Ministrowie, którzy dziś odeszli (nielojalni albo bez pensji). Starsze zapisy: null. */
    private List<String> odeszliMinistrowie = new ArrayList<>();
    private final List<String> nieoplaceni = new ArrayList<>();
    private final List<String> awanse = new ArrayList<>();

    BilansDnia(int zlotoRano) {
        this.zlotoRano = zlotoRano;
        this.zlotoWieczorem = zlotoRano;
    }

    void przychod(int dochodDnia, int zKopalni) {
        dochod += dochodDnia;
        kopalnia += zKopalni;
    }

    void lupy(int kwota) {
        lupy += kwota;
    }

    public int getLupy() {
        return lupy;
    }

    void pensja(int kwota) {
        pensje += kwota;
    }

    void rekrutacja(int koszt, List<Potwor> nowi) {
        rekrutacja += koszt;
        nowi.forEach(p -> zatrudnieni.add(p.getImie()));
    }

    void rozkaz(int koszt) {
        rozkazy += koszt;
    }

    void lapowka(int kwota) {
        lapowka += kwota;
    }

    void kradziez(int kwota) {
        skradzione += Math.max(0, kwota);
    }

    /** Potwór zwolniony przez pomyłkę stażystki kadr. */
    void zwolnionyPrzezPomylke(Potwor potwor) {
        odeszli.add(potwor.getImie());
    }

    void pladrowanie(int kwota) {
        spladrowane += kwota;
    }

    void obrona(Obrona wynik) {
        obrona = wynik;
    }

    void polegl(Potwor potwor) {
        polegli.add(potwor.getImie());
    }

    void awans(Potwor potwor) {
        awanse.add(potwor.getImie());
    }

    void wieczor(int wyplaconyZold, List<Potwor> bezZoldu, List<Potwor> odchodzacy, int wydobycie, int zloto) {
        zold += wyplaconyZold;
        bezZoldu.forEach(p -> nieoplaceni.add(p.getImie()));
        odchodzacy.forEach(p -> odeszli.add(p.getImie()));
        wydobycieNaJutro = wydobycie;
        zlotoWieczorem = zloto;
    }

    public int getZlotoRano() {
        return zlotoRano;
    }

    public int getZlotoWieczorem() {
        return zlotoWieczorem;
    }

    public int getDochod() {
        return dochod;
    }

    public int getKopalnia() {
        return kopalnia;
    }

    public int getPensje() {
        return pensje;
    }

    public int getRekrutacja() {
        return rekrutacja;
    }

    public int getRozkazy() {
        return rozkazy;
    }

    public int getLapowka() {
        return lapowka;
    }

    public int getSkradzione() {
        return skradzione;
    }

    public int getSpladrowane() {
        return spladrowane;
    }

    public int getZold() {
        return zold;
    }

    public int getWydobycieNaJutro() {
        return wydobycieNaJutro;
    }

    public Obrona getObrona() {
        return obrona;
    }

    public List<String> getZatrudnieni() {
        return List.copyOf(zatrudnieni);
    }

    public List<String> getPolegli() {
        return List.copyOf(polegli);
    }

    void odszedlMinister(String opis) {
        if (odeszliMinistrowie == null) {
            odeszliMinistrowie = new ArrayList<>();
        }
        odeszliMinistrowie.add(opis);
    }

    public List<String> getOdeszliMinistrowie() {
        return odeszliMinistrowie == null ? List.of() : List.copyOf(odeszliMinistrowie);
    }

    public List<String> getOdeszli() {
        return List.copyOf(odeszli);
    }

    public List<String> getNieoplaceni() {
        return List.copyOf(nieoplaceni);
    }

    public List<String> getAwanse() {
        return List.copyOf(awanse);
    }

    public int przychody() {
        return dochod + kopalnia + lupy;
    }

    public int wydatki() {
        return pensje + rekrutacja + rozkazy + lapowka + skradzione + spladrowane + zold;
    }
}
