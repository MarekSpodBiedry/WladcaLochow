package wladcalochow.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.model.strategia.Ulepszenie;

/** Loch Władcy: załoga potworów, skarbiec, dwaj ministrowie i ustawienia strategiczne. */
public class Loch implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Najwięcej potworów, ile pomieści loch. */
    public static final int POJEMNOSC = 12;
    /** Tyle przegranych obron z rzędu oznacza zdobycie lochu przez bohaterów. */
    public static final int PORAZKI_DO_UPADKU = 3;

    private final List<Potwor> listaPotworow = new ArrayList<>();
    private final Skarbiec skarbiec;
    private MinisterFinansow ministerFinansow;
    private MinisterPracy ministerPracy;
    private final Map<Ulepszenie, Integer> ulepszenia = new EnumMap<>(Ulepszenie.class);
    private Postawa postawa = Postawa.PELNA_OBRONA;
    private int limitRekrutacji = 100;
    private int wydobycieDoOdebrania;
    private int porazkiZRzedu;

    public Loch(Skarbiec skarbiec) {
        this.skarbiec = skarbiec;
    }

    /** Lista tylko do odczytu; załogę zmienia się przez zatrudnij() i usun(). */
    public List<Potwor> getListaPotworow() {
        return Collections.unmodifiableList(listaPotworow);
    }

    public void zatrudnij(Potwor potwor) {
        if (!listaPotworow.contains(potwor)) {
            listaPotworow.add(potwor);
        }
    }

    public int wolneMiejsca() {
        return Math.max(0, POJEMNOSC - listaPotworow.size());
    }

    /** Udana obrona zeruje licznik porażek, przegrana go zwiększa. Każda walka zeruje licznik łapówek. */
    public void zanotujWynikObrony(boolean udana) {
        porazkiZRzedu = udana ? 0 : porazkiZRzedu + 1;
        lapowkiZRzedu = 0;
    }

    /** Każda kolejna łapówka bez walki pomiędzy jest droższa: bohaterowie wiedzą, że loch płaci. */
    public static final double DROZEJ_ZA_KOLEJNA_LAPOWKE = 0.5;

    private int lapowkiZRzedu;

    public void zanotujLapowke() {
        lapowkiZRzedu++;
    }

    public int getLapowkiZRzedu() {
        return lapowkiZRzedu;
    }

    /** Mnożnik ceny łapówki: 1 przy pierwszej, potem +50% za każdą kolejną bez walki pomiędzy. */
    public double mnoznikLapowki() {
        return 1 + DROZEJ_ZA_KOLEJNA_LAPOWKE * lapowkiZRzedu;
    }

    public int getPorazkiZRzedu() {
        return porazkiZRzedu;
    }

    public boolean czyZdobyty() {
        return porazkiZRzedu >= PORAZKI_DO_UPADKU;
    }

    public void usun(Potwor potwor) {
        listaPotworow.remove(potwor);
    }

    /** Potwory, które dziś walczą: nie kopią złota i nie leżą ranne. */
    public List<Potwor> wojownicy() {
        return listaPotworow.stream().filter(p -> !p.czyWKopalni() && !p.czyRanny()).toList();
    }

    /** Potwory zdolne do pracy i walki (bez ciężko rannych). Spośród nich Minister Pracy wybiera górników. */
    public List<Potwor> zdolni() {
        return listaPotworow.stream().filter(p -> !p.czyRanny()).toList();
    }

    public List<Potwor> ranni() {
        return listaPotworow.stream().filter(Potwor::czyRanny).toList();
    }

    /** Potwory, którym wieczorem należy się żołd: wszyscy poza górnikami, także ranni. */
    public List<Potwor> naZoldzie() {
        return listaPotworow.stream().filter(p -> !p.czyWKopalni()).toList();
    }

    public List<Potwor> gornicy() {
        return listaPotworow.stream().filter(Potwor::czyWKopalni).toList();
    }

    /** Suma dziennego żołdu całej załogi. */
    public int dziennyKosztZoldu() {
        return listaPotworow.stream().mapToInt(Potwor::getZold).sum();
    }

    public int poziom(Ulepszenie ulepszenie) {
        return ulepszenia.getOrDefault(ulepszenie, 0);
    }

    public void ulepsz(Ulepszenie ulepszenie) {
        int poziom = poziom(ulepszenie);
        if (poziom >= Ulepszenie.MAX_POZIOM) {
            throw new IllegalStateException(ulepszenie.nazwa() + " ma już najwyższy poziom");
        }
        ulepszenia.put(ulepszenie, poziom + 1);
    }

    /** Mnożnik wydobycia: nadzór Ministra Pracy i poziom kopalni. */
    public double mnoznikWydobycia() {
        return 1.0 + ministerPracy.getNadzorKopalni() + Ulepszenie.KOPALNIA_NA_POZIOM * poziom(Ulepszenie.KOPALNIA)
                + (ministerPracy.ma(wladcalochow.model.strategia.CechaMinistra.NADZORCA) ? 0.25 : 0);
    }

    /** Mnożnik szansy kradzieży i wielkości łupu: im lepszy zamek, tym mniejszy. */
    public double mnoznikKradziezy() {
        return 1.0 - Ulepszenie.ZAMEK_NA_POZIOM * poziom(Ulepszenie.ZAMEK_SKARBCA);
    }

    public void dodajWydobycie(int kwota) {
        wydobycieDoOdebrania += Math.max(0, kwota);
    }

    public int odbierzWydobycie() {
        int kwota = wydobycieDoOdebrania;
        wydobycieDoOdebrania = 0;
        return kwota;
    }

    public int getWydobycieDoOdebrania() {
        return wydobycieDoOdebrania;
    }

    public Skarbiec getSkarbiec() {
        return skarbiec;
    }

    public MinisterFinansow getMinisterFinansow() {
        return ministerFinansow;
    }

    public void setMinisterFinansow(MinisterFinansow ministerFinansow) {
        this.ministerFinansow = ministerFinansow;
    }

    public MinisterPracy getMinisterPracy() {
        return ministerPracy;
    }

    public void setMinisterPracy(MinisterPracy ministerPracy) {
        this.ministerPracy = ministerPracy;
    }

    public Postawa getPostawa() {
        return postawa;
    }

    public void setPostawa(Postawa postawa) {
        this.postawa = postawa;
    }

    public int getLimitRekrutacji() {
        return limitRekrutacji;
    }

    public void setLimitRekrutacji(int procent) {
        this.limitRekrutacji = Math.max(0, Math.min(100, procent));
    }
}
