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

    /** Udana obrona zeruje licznik porażek, przegrana go zwiększa. */
    public void zanotujWynikObrony(boolean udana) {
        porazkiZRzedu = udana ? 0 : porazkiZRzedu + 1;
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

    /** Potwory, które dziś nie kopią złota, czyli mogą walczyć i pobierają żołd. */
    public List<Potwor> wojownicy() {
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
        return 1.0 + ministerPracy.getNadzorKopalni() + 0.25 * poziom(Ulepszenie.KOPALNIA);
    }

    /** Mnożnik szansy kradzieży i wielkości łupu: im lepszy zamek, tym mniejszy. */
    public double mnoznikKradziezy() {
        return 1.0 - 0.25 * poziom(Ulepszenie.ZAMEK_SKARBCA);
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
