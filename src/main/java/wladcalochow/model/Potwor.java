package wladcalochow.model;

/** Najemnik Władcy. Oprócz statystyk istoty ma żołd, doświadczenie i przydział do kopalni. */
public abstract class Potwor extends Istota {

    private static final long serialVersionUID = 1L;

    public static final int MAX_POZIOM = 9;
    public static final double AWANS = 0.10;

    private int zold;
    private int lupDnia;
    private int doswiadczenie;
    private int doswiadczenieZWalki;
    private int poziom = 1;
    private boolean wKopalni;

    protected Potwor(String imie, int silaAtaku, int maxPunktyZycia, int maxMorale, int zold) {
        super(imie, silaAtaku, maxPunktyZycia, maxMorale, 0);
        if (zold < 0) {
            throw new IllegalArgumentException("Żołd nie może być ujemny");
        }
        this.zold = zold;
    }

    /** Siła i zdrowie sumarycznie: miara, którą kieruje się Minister Rambo. */
    public int moc() {
        return getSilaAtaku() + getMaxPunktyZycia();
    }

    /** Wartość złota wykopanego przez potwora w ciągu dnia pracy w kopalni. */
    public double wartoscWydobycia() {
        return (getSilaAtaku() + getPunktyZycia()) * procentMorale();
    }

    /**
     * Ile żołdu należy się potworowi dzisiaj. Łup z walki nie pomniejsza już żołdu: po walce trafia
     * do skarbca (wcześniej zostawał przy potworze i przepadał razem z nim, gdy potwór ginął).
     */
    public int nalezneDzis() {
        return zold;
    }

    public void otrzymajZold() {
        zmienMorale((int) Math.round(getMaxMorale() * 0.20));
        lupDnia = 0;
    }

    public void brakZoldu() {
        zmienMorale(-(int) Math.round(getMaxMorale() * 0.10));
        lupDnia = 0;
    }

    /** Potwór z morale poniżej 10%, który nie dostał żołdu, odchodzi z lochu. */
    public boolean chceOdejsc() {
        return procentMorale() < 0.10;
    }

    public void premia() {
        zmienMorale((int) Math.round(getMaxMorale() * 0.30));
    }

    @Override
    public int zabierzZloto(Istota ofiara) {
        int lup = super.zabierzZloto(ofiara);
        lupDnia += lup;
        doswiadczenieZWalki++;
        return lup;
    }

    /** Po przeżytej walce potwór zdobywa doświadczenie. Zwraca true, jeśli awansował. */
    public boolean podsumujWalke() {
        return podsumujWalke(false);
    }

    /** Jak wyżej; pod okiem Trenera potwór zdobywa dwa razy więcej doświadczenia. */
    public boolean podsumujWalke(boolean trener) {
        int zdobyte = (1 + doswiadczenieZWalki) * (trener ? 2 : 1);
        doswiadczenieZWalki = 0;
        doswiadczenie += zdobyte;
        boolean awans = false;
        while (poziom < MAX_POZIOM && doswiadczenie >= progAwansu()) {
            doswiadczenie -= progAwansu();
            poziom++;
            wzmocnij(AWANS);
            zold = (int) Math.ceil(zold * (1 + AWANS));
            awans = true;
        }
        return awans;
    }

    private int progAwansu() {
        return 3 * poziom;
    }

    /** Dni, przez które ciężko ranny potwór jeszcze nie walczy i nie kopie (0: zdrowy). */
    private int dniRekonwalescencji;

    /** Ciężko ranny potwór dochodzi do siebie przez podaną liczbę poranków. Żołd należy mu się dalej. */
    public void zranCiezko(int dni) {
        dniRekonwalescencji = Math.max(dniRekonwalescencji, dni);
    }

    public boolean czyRanny() {
        return dniRekonwalescencji > 0;
    }

    /** Szpital: ciężko ranny od razu wraca do sił. */
    public void wyleczZRan() {
        dniRekonwalescencji = 0;
    }

    public int getDniRekonwalescencji() {
        return dniRekonwalescencji;
    }

    /** Kolejny poranek rekonwalescencji. Zwraca true, gdy potwór właśnie wrócił do sił. */
    public boolean dochodzDoSiebie() {
        if (dniRekonwalescencji == 0) {
            return false;
        }
        dniRekonwalescencji--;
        return dniRekonwalescencji == 0;
    }

    public int getZold() {
        return zold;
    }

    public int getLupDnia() {
        return lupDnia;
    }

    public int getPoziom() {
        return poziom;
    }

    public boolean czyWKopalni() {
        return wKopalni;
    }

    public void setWKopalni(boolean wKopalni) {
        this.wKopalni = wKopalni;
    }
}
