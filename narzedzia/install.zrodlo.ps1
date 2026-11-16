# Wladca Lochow - instalator dla Windows.
#
# Uzycie (w PowerShellu):
#   irm https://raw.githubusercontent.com/MarekSpodBiedry/WladcaLochow/main/install.ps1 | iex
#
# Co robi:
#   0. Sprawdzanie Javy    - szuka Javy 21+; gdy jej nie ma, pobiera przenosna Jave do folderu gry
#   1. Laczenie z serwerem - pyta GitHuba o najnowsze wydanie gry
#   2. Pobieranie          - pobiera gre do %LOCALAPPDATA%\WladcaLochow, tworzy skroty
#   3. Sprawdzanie         - porownuje sume SHA-256 i uruchamia autotest gry
# Niczego nie instaluje w systemie i nie wymaga uprawnien administratora.
# Odinstalowanie: usun folder %LOCALAPPDATA%\WladcaLochow i skroty "Wladca Lochow".
#
# Publikowany plik install.ps1 zawiera wylacznie znaki ASCII: polskie litery i znaki grafiki
# sa w nim zapisane jako kody \uXXXX i zamieniane funkcja T. Dzieki temu skrypt dziala tak samo
# bez wzgledu na kodowanie, w jakim PowerShell go wczyta.
# Czytelne zrodlo to narzedzia/install.zrodlo.ps1; install.ps1 powstaje z niego poleceniem
#   python3 narzedzia/zbuduj-instalator.py
#
# Zmienne srodowiskowe dla testow:
#   WLADCA_PODGLAD=1     sama animacja, bez pobierania i bez zmian na dysku
#   WLADCA_FOLDER        inny folder instalacji
#   WLADCA_API           inny adres zapytania o najnowsze wydanie
#   WLADCA_JAVA_URL      inny adres archiwum z Java
#   WLADCA_WYMUS_JAVE=1  pobierz Jave, nawet jesli jest juz w systemie
#   WLADCA_BEZ_STARTU=1  nie czekaj na klawisz i nie uruchamiaj gry na koncu
#   WLADCA_TEST=1        pozwol uruchomic prawdziwa instalacje poza Windows (bez skrotow)

& {
    $ErrorActionPreference = 'Stop'

    function T([string]$tekst) { return [regex]::Unescape($tekst) }

    # UWAGA: PowerShell nie rozroznia wielkosci liter w nazwach zmiennych, a funkcje widza zmienne
    # swoich wywolujacych. Dlatego wspolne tablice maja dlugie, jednoznaczne nazwy ($Znaki, $Kolory, $S)
    # i zadna zmienna lokalna nie moze nazywac sie tak samo.

    $Repo = 'MarekSpodBiedry/WladcaLochow'
    $NaWindows = ($env:OS -eq 'Windows_NT')
    $Podglad = ($env:WLADCA_PODGLAD -eq '1')
    $Test = ($env:WLADCA_TEST -eq '1')
    $BezStartu = ($env:WLADCA_BEZ_STARTU -eq '1')
    $MinJava = 21

    if (-not $NaWindows -and -not $Podglad -and -not $Test) {
        Write-Host (T 'Ten instalator działa tylko na Windows. Na innych systemach pobierz plik wladca-lochow.jar ze strony wydań.')
        return
    }

    if ($env:WLADCA_FOLDER) { $Folder = $env:WLADCA_FOLDER }
    elseif ($NaWindows) { $Folder = Join-Path $env:LOCALAPPDATA 'WladcaLochow' }
    else { $Folder = Join-Path ([IO.Path]::GetTempPath()) 'WladcaLochow-test' }
    # Java dostaje sama nazwe pliku gry, a folder gry jest katalogiem roboczym. Java czyta argumenty
    # w stronie kodowej systemu, wiec pelna sciezka z polskimi literami (np. w nazwie uzytkownika)
    # na Windows po angielsku zamienilaby sie w znaki zapytania.
    $NazwaJar = 'wladca-lochow.jar'
    $Jar = Join-Path $Folder $NazwaJar
    if ($NaWindows) { $NazwaJava = 'java.exe' } else { $NazwaJava = 'java' }

    # ------------------------------------------------------------------ znaki i kolory

    $CSI = "$([char]27)["
    $Znaki = @{
        Pelny = T '█'; Dol = T '▄'; Gora = T '▀'; Cien = T '░'
        Szczyt = T '▲'; Flaga = T '►'; Kropka = T '·'; Oko = T '•'; Ptaszek = T '√'
    }
    $Kolory = @{
        Tlo = 0x14161F; Tekst = 0xECE6D6; Slaby = 0x8D94AF; Zloto = 0xF0B840; Pomarancz = 0xEC8B45
        Niebieski = 0x4FA3DB; NiebieskiJasny = 0x9AD0F5; Zielen = 0x4DB37E; Czerwien = 0xE2574C
        Pusty = 0x2A3049; KamienA = 0x8E94AA; KamienB = 0x777D95; KamienCien = 0x5B6079
        Wejscie = 0x17121F; Okno = 0xF6C453; OknoCiemne = 0xD9962F; Pien = 0x6A4326; Ksiezyc = 0xE8E4CC
    }

    function Mieszaj([int]$a, [int]$b, [double]$t) {
        if ($t -lt 0) { $t = 0 }
        if ($t -gt 1) { $t = 1 }
        $r = [int][Math]::Round((($a -shr 16) -band 255) * (1 - $t) + (($b -shr 16) -band 255) * $t)
        $g = [int][Math]::Round((($a -shr 8) -band 255) * (1 - $t) + (($b -shr 8) -band 255) * $t)
        $n = [int][Math]::Round(($a -band 255) * (1 - $t) + ($b -band 255) * $t)
        return (($r -shl 16) -bor ($g -shl 8) -bor $n)
    }

    function Barwa([int]$znak, [int]$tlo) {
        return "${CSI}38;2;$(($znak -shr 16) -band 255);$(($znak -shr 8) -band 255);$($znak -band 255);48;2;$(($tlo -shr 16) -band 255);$(($tlo -shr 8) -band 255);$($tlo -band 255)m"
    }

    # ------------------------------------------------------------------ stan

    $S = @{
        Paski = @(
            @{ Nazwa = (T 'Sprawdzanie Javy'); Cel = 0.0; Pokaz = 0.0; Stan = 'czeka'; Opis = '' },
            @{ Nazwa = (T 'Łączenie z serwerem'); Cel = 0.0; Pokaz = 0.0; Stan = 'czeka'; Opis = '' },
            @{ Nazwa = (T 'Pobieranie'); Cel = 0.0; Pokaz = 0.0; Stan = 'czeka'; Opis = '' },
            @{ Nazwa = (T 'Sprawdzanie'); Cel = 0.0; Pokaz = 0.0; Stan = 'czeka'; Opis = '' }
        )
        Komunikat = ''; KolorKomunikatu = $Kolory.Slaby; Mruga = $false
        B = New-Object System.Text.StringBuilder
        Zegar = [Diagnostics.Stopwatch]::StartNew(); Ostatnia = -1000; Nr = 0
        Los = New-Object System.Random
        W = 0; H = 0; Pelny = $false
        Sx = 0; Sy = 0; Sw = 0; Bx = 0; By = 0; Ogien = 0; Zamek = 0
        Gwiazdy = New-Object System.Collections.ArrayList
        Iskry = New-Object System.Collections.ArrayList
        Zajete = @{}
        Java = $null; Javaw = $null; OpisJavy = ''
        Wersja = ''; AdresJar = ''; Suma = ''; AdresIkony = ''; Rozmiar = 0; Aktualna = $false
        Klient = $null; KlientBezPrzekierowan = $null
        StareKodowanie = $null; StaryTryb = $null; Ekran = $false
    }

    # ------------------------------------------------------------------ rysowanie

    function Pisz([int]$x, [int]$y, [string]$tekst, [int]$znak, [int]$tlo) {
        if ($y -lt 0 -or $y -ge $S.H -or $x -ge $S.W) { return }
        if ($x -lt 0) { return }
        if ($x + $tekst.Length -gt $S.W) { $tekst = $tekst.Substring(0, $S.W - $x) }
        [void]$S.B.Append("${CSI}$($y + 1);$($x + 1)H").Append((Barwa $znak $tlo)).Append($tekst)
    }

    function Wyslij {
        if ($S.B.Length -gt 0) {
            [Console]::Out.Write($S.B.ToString())
            [Console]::Out.Flush()
            [void]$S.B.Clear()
        }
    }

    function Niebo([int]$wiersz) { return (Mieszaj 0x0E1433 0x2E2A5E ($wiersz / 6.0)) }

    function Ziemia([int]$wiersz) { return (Mieszaj 0x1F4D2E 0x163A22 (($wiersz - 7) / 2.0)) }

    # Tlo sceny w danym wierszu (wspolrzedne wzgledem sceny): niebo w wierszach 0-6, ziemia 7-9.
    function TloSceny([int]$wiersz) {
        if ($wiersz -lt 7) { return (Niebo $wiersz) }
        return (Ziemia $wiersz)
    }

    function NaScenie([int]$x, [int]$y, [string]$tekst, [int]$znak, [bool]$zajmij) {
        if ($x -lt 0 -or $x + $tekst.Length -gt $S.Sw -or $y -lt 0 -or $y -gt 9) { return }
        Pisz ($S.Sx + $x) ($S.Sy + $y) $tekst $znak (TloSceny $y)
        if ($zajmij) {
            for ($i = 0; $i -lt $tekst.Length; $i++) { $S.Zajete["$($x + $i),$y"] = $true }
        }
    }

    function Sosna([int]$cx, [int]$wysokosc) {
        $zielenie = @(0x3E9B63, 0x2E8554, 0x236B44)
        NaScenie $cx 6 $Znaki.Pelny $Kolory.Pien $true
        for ($i = 0; $i -lt $wysokosc; $i++) {
            $y = 6 - $wysokosc + $i
            $szer = 1 + 2 * (($i % 3) + [int][Math]::Floor($i / 3))
            $kolor = $zielenie[[Math]::Min(2, [int][Math]::Floor($i / 3))]
            if ($szer -eq 1) { NaScenie $cx $y $Znaki.Szczyt $kolor $true; continue }
            $pol = [int](($szer - 1) / 2)
            NaScenie ($cx - $pol) $y ($Znaki.Pelny * ($pol + 1)) $kolor $true
            NaScenie ($cx + 1) $y ($Znaki.Pelny * $pol) (Mieszaj $kolor 0x000000 0.18) $true
        }
    }

    function Kamien([int]$i, [int]$wiersz) {
        if ((([int][Math]::Floor($i / 2)) + $wiersz) % 2 -eq 0) { return $Kolory.KamienA }
        return $Kolory.KamienB
    }

    function Mur([int]$x, [int]$y, [int]$szer, [int]$wys) {
        for ($w = 0; $w -lt $wys; $w++) {
            for ($i = 0; $i -lt $szer; $i++) {
                $kolor = Kamien ($x + $i) ($y + $w)
                if ($i -eq 0 -or $i -eq $szer - 1) { $kolor = $Kolory.KamienCien }
                NaScenie ($x + $i) ($y + $w) $Znaki.Pelny $kolor $true
            }
        }
    }

    function Blanki([int]$x, [int]$y, [int]$szer) {
        for ($i = 0; $i -lt $szer; $i++) {
            if (([int][Math]::Floor($i / 2)) % 2 -eq 0) { NaScenie ($x + $i) $y $Znaki.Pelny (Kamien ($x + $i) $y) $true }
        }
    }

    function Zamek {
        $z = $S.Zamek
        Mur $z 2 6 6
        Blanki $z 1 6
        Mur ($z + 18) 2 6 6
        Blanki ($z + 18) 1 6
        Mur ($z + 6) 4 12 4
        Blanki ($z + 6) 3 12
        # brama
        for ($w = 5; $w -le 7; $w++) {
            Pisz ($S.Sx + $z + 10) ($S.Sy + $w) '    ' $Kolory.Wejscie $Kolory.Wejscie
        }
        Pisz ($S.Sx + $z + 10) ($S.Sy + 6) (' ' + $Znaki.Oko + $Znaki.Oko + ' ') $Kolory.Zloto $Kolory.Wejscie
        # maszt choragwi
        NaScenie ($z + 2) 0 '|' 0x9A9FB5 $true
        $S.Zajete["$($z + 3),0"] = $true
        $S.Zajete["$($z + 4),0"] = $true
    }

    function Tlo {
        [void]$S.B.Append((Barwa $Kolory.Tekst $Kolory.Tlo)).Append("${CSI}2J")
        $S.Zajete = @{}
        [void]$S.Gwiazdy.Clear()
        [void]$S.Iskry.Clear()
        if (-not $S.Pelny) {
            $tytul = T 'W Ł A D C A   L O C H Ó W'
            Pisz ([int](($S.W - $tytul.Length) / 2)) 0 $tytul $Kolory.Zloto $Kolory.Tlo
            return
        }
        # tytul z blokow
        $litery = @(
            '#   # #      ###  ####   ####  ###    #      ###   #### #   #  ###  #   #',
            '#   # # #   #   # #   # #     #   #   #     #   # #     #   # #   # #   #',
            '# # # ##    ##### #   # #     #####   #     #   # #     ##### #   # # # #',
            '## ## #     #   # #   # #     #   #   #     #   # #     #   # #   # ## ##',
            '#   # ##### #   # ####   #### #   #   #####  ###   #### #   #  ###  #   #'
        )
        $tx = [int](($S.W - $litery[0].Length) / 2)
        Pisz ($tx + 64) 0 ($Znaki.Dol + $Znaki.Gora) $Kolory.Zloto $Kolory.Tlo
        for ($i = 0; $i -lt 5; $i++) {
            $kolor = Mieszaj 0xF7D36B $Kolory.Pomarancz ($i / 4.0)
            Pisz $tx (1 + $i) ($litery[$i].Replace('#', $Znaki.Pelny)) $kolor $Kolory.Tlo
        }
        $podtytul = 'i n s t a l a t o r'
        Pisz ([int](($S.W - $podtytul.Length) / 2)) 6 $podtytul $Kolory.Slaby $Kolory.Tlo

        # niebo i ziemia
        for ($w = 0; $w -le 9; $w++) {
            Pisz $S.Sx ($S.Sy + $w) (' ' * $S.Sw) $Kolory.Tekst (TloSceny $w)
        }
        # ksiezyc
        $kx = [int]($S.Sw * 0.60)
        NaScenie $kx 0 ($Znaki.Dol + $Znaki.Pelny + $Znaki.Pelny + $Znaki.Dol) $Kolory.Ksiezyc $true
        NaScenie $kx 1 ($Znaki.Gora + $Znaki.Pelny + $Znaki.Pelny + $Znaki.Gora) $Kolory.Ksiezyc $true
        # las i zamek
        Sosna 5 6
        Sosna 13 4
        Sosna 20 5
        Sosna ($S.Sw - 4) 5
        Zamek
        # ognisko: polana na ziemi
        $o = $S.Ogien
        Pisz ($S.Sx + $o) ($S.Sy + 7) ($Znaki.Dol + $Znaki.Pelny + $Znaki.Gora + $Znaki.Pelny + $Znaki.Gora + $Znaki.Pelny + $Znaki.Dol) 0x7A4B2A (Ziemia 7)
        # trawa
        for ($i = 0; $i -lt [int]($S.Sw / 5); $i++) {
            $x = $S.Los.Next(0, $S.Sw)
            $y = $S.Los.Next(7, 10)
            if ($y -eq 7 -and $x -ge $o - 1 -and $x -le $o + 7) { continue }
            Pisz ($S.Sx + $x) ($S.Sy + $y) ',' (Mieszaj (Ziemia $y) 0x000000 0.3) (Ziemia $y)
        }
        # gwiazdy tylko tam, gdzie nic nie stoi
        for ($i = 0; $i -lt [int]($S.Sw / 3); $i++) {
            $x = $S.Los.Next(0, $S.Sw)
            $y = $S.Los.Next(0, 5)
            $nadOgniem = ($x -ge $o - 1 -and $x -le $o + 7)
            if (-not $S.Zajete.ContainsKey("$x,$y") -and -not $nadOgniem) {
                [void]$S.Gwiazdy.Add(@{ X = $x; Y = $y; Jasna = ($S.Los.Next(0, 2) -eq 0) })
                $S.Zajete["$x,$y"] = $true
            }
        }
        foreach ($g in $S.Gwiazdy) { Gwiazda $g }
    }

    function Gwiazda($g) {
        if ($g.Jasna) { NaScenie $g.X $g.Y '*' 0xF3EFD9 $false }
        else { NaScenie $g.X $g.Y $Znaki.Kropka 0x9A9CC4 $false }
    }

    # Ruchome czesci sceny: plomienie, iskry, gwiazdy, okna zamku i choragiew.
    function Animacja {
        if (-not $S.Pelny) { return }
        $o = $S.Ogien
        $zar = @(0x8E2A1E, 0xC9482B, 0xE8792F, 0xF4A63A, 0xF9D25C, 0xFFF0A8)
        for ($w = 0; $w -lt 3; $w++) {
            for ($c = 0; $c -lt 7; $c++) {
                $cieplo = (0.35 + 0.3 * $w) - [Math]::Abs($c - 3) * 0.17 + ($S.Los.NextDouble() - 0.5) * 0.5
                $x = $S.Sx + $o + $c
                $y = $S.Sy + 4 + $w
                if ($cieplo -lt 0.22) { Pisz $x $y ' ' $Kolory.Tekst (Niebo (4 + $w)); continue }
                $indeks = [Math]::Min(5, [Math]::Max(0, [int][Math]::Floor($cieplo * 6)))
                if ($w -eq 0 -or $cieplo -lt 0.4) { $znak = $Znaki.Dol } else { $znak = $Znaki.Pelny }
                Pisz $x $y $znak $zar[$indeks] (Niebo (4 + $w))
            }
        }
        # iskry unosza sie nad ogniem
        foreach ($i in @($S.Iskry)) {
            Pisz ($S.Sx + $i.X) ($S.Sy + $i.Y) ' ' $Kolory.Tekst (Niebo $i.Y)
            $i.Y = $i.Y - 1
            $i.X = $i.X + $S.Los.Next(-1, 2)
            if ($i.Y -lt 1 -or $i.X -lt $o - 1 -or $i.X -gt $o + 7) { [void]$S.Iskry.Remove($i) }
        }
        if ($S.Iskry.Count -lt 4 -and $S.Los.Next(0, 3) -eq 0) {
            [void]$S.Iskry.Add(@{ X = $o + $S.Los.Next(1, 6); Y = 3 })
        }
        foreach ($i in $S.Iskry) {
            Pisz ($S.Sx + $i.X) ($S.Sy + $i.Y) $Znaki.Kropka (Mieszaj 0xF9D25C 0xE8792F ($S.Los.NextDouble())) (Niebo $i.Y)
        }
        # poswiata ogniska na trawie
        $moc = 0.30 + 0.12 * [Math]::Sin($S.Nr / 3.0)
        for ($c = -3; $c -le 9; $c++) {
            if ($c -ge 0 -and $c -le 6) { continue }
            $odleglosc = [Math]::Min([Math]::Abs($c + 1), [Math]::Abs($c - 7))
            $blask = Mieszaj (Ziemia 7) 0xE8792F ($moc / (1 + $odleglosc))
            Pisz ($S.Sx + $o + $c) ($S.Sy + 7) ' ' $Kolory.Tekst $blask
        }
        # gwiazdy mrugaja
        if ($S.Gwiazdy.Count -gt 0) {
            $g = $S.Gwiazdy[$S.Los.Next(0, $S.Gwiazdy.Count)]
            $g.Jasna = -not $g.Jasna
            Gwiazda $g
        }
        # okna w wiezach migocza, choragiew faluje
        $z = $S.Zamek
        if ($S.Nr % 3 -eq 0) {
            foreach ($okno in @(($z + 2), ($z + 20))) {
                if ($S.Los.Next(0, 4) -eq 0) { $kolor = $Kolory.OknoCiemne } else { $kolor = $Kolory.Okno }
                Pisz ($S.Sx + $okno) ($S.Sy + 3) ($Znaki.Pelny + $Znaki.Pelny) $kolor $Kolory.Wejscie
            }
        }
        if ($S.Nr % 8 -lt 4) { $flaga = $Znaki.Pelny + $Znaki.Flaga } else { $flaga = $Znaki.Dol + $Znaki.Flaga }
        Pisz ($S.Sx + $z + 3) $S.Sy $flaga $Kolory.Czerwien (Niebo 0)
    }

    function Paski {
        $dlugosc = 30
        for ($i = 0; $i -lt 4; $i++) {
            $p = $S.Paski[$i]
            $y = $S.By + 2 * $i
            $kolor = $Kolory.Slaby; $kolorTekstu = $Kolory.Slaby; $znak = $Znaki.Kropka
            if ($p.Stan -eq 'trwa') { $kolor = $Kolory.Niebieski; $kolorTekstu = $Kolory.Tekst; $znak = $Znaki.Flaga }
            if ($p.Stan -eq 'gotowe') { $kolor = $Kolory.Zielen; $kolorTekstu = $Kolory.Tekst; $znak = $Znaki.Ptaszek }
            if ($p.Stan -eq 'blad') { $kolor = $Kolory.Czerwien; $kolorTekstu = $Kolory.Tekst; $znak = 'x' }
            Pisz $S.Bx $y $znak $kolor $Kolory.Tlo
            Pisz ($S.Bx + 2) $y ($p.Nazwa.PadRight(21)) $kolorTekstu $Kolory.Tlo
            $pelne = [int][Math]::Round($p.Pokaz * $dlugosc)
            Pisz ($S.Bx + 23) $y ($Znaki.Pelny * $pelne) $kolor $Kolory.Tlo
            Pisz ($S.Bx + 23 + $pelne) $y ($Znaki.Cien * ($dlugosc - $pelne)) $Kolory.Pusty $Kolory.Tlo
            if ($p.Stan -eq 'trwa' -and $pelne -gt 1) {
                # jasna kreska przesuwa sie po rosnacym pasku
                Pisz ($S.Bx + 23 + ($S.Nr % $pelne)) $y $Znaki.Pelny $Kolory.NiebieskiJasny $Kolory.Tlo
            }
            $procent = '{0,3}%' -f [int][Math]::Floor($p.Pokaz * 100)
            Pisz ($S.Bx + 54) $y $procent $kolorTekstu $Kolory.Tlo
            $opis = $p.Opis
            if ($opis.Length -gt 19) { $opis = $opis.Substring(0, 19) }
            Pisz ($S.Bx + 60) $y ($opis.PadRight(19)) $Kolory.Slaby $Kolory.Tlo
        }
        $tekst = $S.Komunikat
        if ($tekst.Length -gt $S.W - 2) { $tekst = $tekst.Substring(0, $S.W - 2) }
        $kolor = $S.KolorKomunikatu
        if ($S.Mruga -and ($S.Nr % 16 -ge 9)) { $kolor = Mieszaj $kolor $Kolory.Tlo 0.6 }
        Pisz 0 ($S.By + 9) (' ' * $S.W) $Kolory.Tekst $Kolory.Tlo
        Pisz ([int](($S.W - $tekst.Length) / 2)) ($S.By + 9) $tekst $kolor $Kolory.Tlo
    }

    function Uklad {
        $S.W = [Console]::WindowWidth
        $S.H = [Console]::WindowHeight
        $S.Pelny = ($S.W -ge 82 -and $S.H -ge 29)
        if ($S.Pelny) {
            $S.Sw = [Math]::Min($S.W - 4, 100)
            $S.Sx = [int](($S.W - $S.Sw) / 2)
            $S.Sy = 8
            $S.By = 19
            $S.Ogien = [int]($S.Sw * 0.38)
            $S.Zamek = $S.Sw - 34
        } else {
            $S.By = 2
        }
        $S.Bx = [Math]::Max(0, [int](($S.W - 79) / 2))
        Tlo
    }

    # Jedna klatka animacji: najwyzej co ok. 50 ms, chyba ze wymuszona.
    function Klatka([bool]$wymus) {
        $teraz = $S.Zegar.ElapsedMilliseconds
        if (-not $wymus -and $teraz - $S.Ostatnia -lt 50) { return }
        $S.Ostatnia = $teraz
        $S.Nr++
        if ([Console]::WindowWidth -ne $S.W -or [Console]::WindowHeight -ne $S.H) { Uklad }
        foreach ($p in $S.Paski) {
            $p.Pokaz = $p.Pokaz + ($p.Cel - $p.Pokaz) * 0.22
            if ([Math]::Abs($p.Cel - $p.Pokaz) -lt 0.004) { $p.Pokaz = $p.Cel }
        }
        Animacja
        Paski
        Wyslij
    }

    function Animuj([int]$milisekundy) {
        $koniec = $S.Zegar.ElapsedMilliseconds + $milisekundy
        while ($S.Zegar.ElapsedMilliseconds -lt $koniec) { Klatka $false; Start-Sleep -Milliseconds 12 }
    }

    # ------------------------------------------------------------------ kroki i postep

    function Zacznij([int]$i, [string]$opis) {
        $S.Paski[$i].Stan = 'trwa'
        $S.Paski[$i].Opis = $opis
        $S.Paski[$i].Cel = 0.06
    }

    function Postep([int]$i, [double]$ulamek, [string]$opis) {
        $p = $S.Paski[$i]
        if ($ulamek -gt 1) { $ulamek = 1 }
        if ($ulamek -gt $p.Cel) { $p.Cel = $ulamek }
        if ($opis) { $p.Opis = $opis }
        Klatka $false
    }

    function Zakoncz([int]$i, [string]$opis) {
        $p = $S.Paski[$i]
        $p.Cel = 1.0
        $p.Opis = $opis
        while ($p.Pokaz -lt 0.999) { Klatka $false; Start-Sleep -Milliseconds 12 }
        $p.Stan = 'gotowe'
        Klatka $true
    }

    function Czekaj($zadanie) {
        while ($true) {
            try { if ($zadanie.Wait(12)) { break } } catch { break }
            Klatka $false
        }
        if ($zadanie.IsFaulted -or $zadanie.IsCanceled) {
            $powod = ''
            if ($zadanie.Exception) { $powod = $zadanie.Exception.GetBaseException().Message }
            throw ((T 'Brak połączenia z siecią albo serwer nie odpowiada. ') + $powod)
        }
    }

    function MB([double]$bajty) { return ('{0:0.0}' -f ($bajty / 1048576.0)).Replace(',', '.') }

    # ------------------------------------------------------------------ siec i pliki

    function PobierzTekst([string]$adres) {
        $zadanie = $S.Klient.GetAsync($adres)
        Czekaj $zadanie
        $odpowiedz = $zadanie.Result
        if (-not $odpowiedz.IsSuccessStatusCode) {
            throw ((T 'Serwer odpowiedział kodem ') + [int]$odpowiedz.StatusCode + ' (' + $adres + ')')
        }
        $czytanie = $odpowiedz.Content.ReadAsStringAsync()
        Czekaj $czytanie
        return $czytanie.Result
    }

    function PobierzPlik([string]$adres, [string]$plik, [int]$pasek, [double]$od, [double]$do, [string]$co) {
        $zadanie = $S.Klient.GetAsync($adres, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead)
        Czekaj $zadanie
        $odpowiedz = $zadanie.Result
        if (-not $odpowiedz.IsSuccessStatusCode) {
            throw ((T 'Nie udało się pobrać pliku, kod ') + [int]$odpowiedz.StatusCode + ' (' + $adres + ')')
        }
        $calosc = $odpowiedz.Content.Headers.ContentLength
        $otwieranie = $odpowiedz.Content.ReadAsStreamAsync()
        Czekaj $otwieranie
        $wejscie = $otwieranie.Result
        $wyjscie = [IO.File]::Create($plik)
        $bufor = New-Object byte[] 81920
        $suma = 0
        try {
            while ($true) {
                $czytanie = $wejscie.ReadAsync($bufor, 0, $bufor.Length)
                Czekaj $czytanie
                $ile = $czytanie.Result
                if ($ile -le 0) { break }
                $wyjscie.Write($bufor, 0, $ile)
                $suma += $ile
                if ($calosc) {
                    Postep $pasek ($od + ($do - $od) * ($suma / [double]$calosc)) ($co + ' ' + (MB $suma) + '/' + (MB $calosc) + ' MB')
                } else {
                    Postep $pasek $od ($co + ' ' + (MB $suma) + ' MB')
                }
            }
        } finally {
            $wyjscie.Dispose()
            $wejscie.Dispose()
        }
    }

    function Suma256([string]$plik) {
        return (Get-FileHash -Algorithm SHA256 -LiteralPath $plik).Hash.ToLowerInvariant()
    }

    # Uruchamia program bez okna i czeka, nie zatrzymujac animacji. Zwraca kod wyjscia i wypisany tekst.
    function Uruchom([string]$program, [string]$argumenty, [string]$katalog = '') {
        $start = New-Object System.Diagnostics.ProcessStartInfo
        $start.FileName = $program
        $start.Arguments = $argumenty
        if ($katalog) { $start.WorkingDirectory = $katalog }
        $start.UseShellExecute = $false
        $start.CreateNoWindow = $true
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        $proces = [Diagnostics.Process]::Start($start)
        $wyjscie = $proces.StandardOutput.ReadToEndAsync()
        $bledy = $proces.StandardError.ReadToEndAsync()
        while (-not $proces.WaitForExit(12)) { Klatka $false }
        $proces.WaitForExit()
        return @{ Kod = $proces.ExitCode; Tekst = ($wyjscie.Result + $bledy.Result) }
    }

    # Glowny numer wersji Javy (np. 21) albo 0, gdy programu nie da sie uruchomic.
    function WersjaJavy([string]$program) {
        return (ProbaJavy $program).Wersja
    }

    # Uruchamia "java -version". Wersja 0 oznacza, ze Java nie wystartowala; Tekst mowi wtedy dlaczego.
    function ProbaJavy([string]$program) {
        try {
            $wynik = Uruchom $program '-version'
            if ($wynik.Tekst -match 'version "(\d+)') { return @{ Wersja = [int]$Matches[1]; Tekst = '' } }
            return @{ Wersja = 0; Tekst = $wynik.Tekst }
        } catch {
            return @{ Wersja = 0; Tekst = $_.Exception.Message }
        }
    }

    # Najwazniejsze zdanie z bledu Javy, np. o braku pamieci, bez pustych wierszy i znakow #.
    function PowodBleduJavy([string]$tekst) {
        $wiersze = @($tekst -split "`r?`n" | ForEach-Object { $_.Trim(' ', '#') } | Where-Object { $_ })
        $pamiec = @($wiersze | Where-Object { $_ -match 'memory|paging file' })
        if ($pamiec.Count -gt 0) { return $pamiec[0] }
        if ($wiersze.Count -gt 0) { return $wiersze[0] }
        return ''
    }

    function Rozpakuj([string]$archiwum, [string]$cel, [int]$pasek, [double]$od, [double]$do) {
        $korzen = [IO.Path]::GetFullPath($cel)
        $zip = [IO.Compression.ZipFile]::OpenRead($archiwum)
        try {
            $ile = $zip.Entries.Count
            $numer = 0
            foreach ($wpis in $zip.Entries) {
                $numer++
                $sciezka = [IO.Path]::GetFullPath((Join-Path $korzen $wpis.FullName))
                if (-not $sciezka.StartsWith($korzen)) { throw (T 'Archiwum z Javą ma nieprawidłową zawartość.') }
                if ($wpis.FullName.EndsWith('/') -or $wpis.FullName.EndsWith('\')) {
                    [void][IO.Directory]::CreateDirectory($sciezka)
                } else {
                    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($sciezka))
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($wpis, $sciezka, $true)
                }
                Postep $pasek ($od + ($do - $od) * ($numer / [double]$ile)) (T 'rozpakowywanie')
            }
        } finally {
            $zip.Dispose()
        }
    }

    # ------------------------------------------------------------------ krok 0: Java

    function AdresJavy {
        if ($env:WLADCA_JAVA_URL) { return $env:WLADCA_JAVA_URL }
        $arch = 'x64'
        if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { $arch = 'aarch64' }
        try {
            # Adoptium przekierowuje na najnowsze archiwum JRE 21 dla Windows.
            $zadanie = $S.KlientBezPrzekierowan.GetAsync("https://api.adoptium.net/v3/binary/latest/21/ga/windows/$arch/jre/hotspot/normal/eclipse")
            Czekaj $zadanie
            $cel = $zadanie.Result.Headers.Location
            if ($cel -and $cel.AbsoluteUri -like 'https://github.com/adoptium/*.zip') { return $cel.AbsoluteUri }
        } catch { }
        # Awaryjnie: znane, sprawdzone wydanie.
        return "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jre_${arch}_windows_hotspot_21.0.8_9.zip"
    }

    function KrokJava {
        Zacznij 0 (T 'szukam Javy')
        $wlasna = Join-Path (Join-Path (Join-Path $Folder 'java') 'bin') $NazwaJava
        if ($env:WLADCA_WYMUS_JAVE -ne '1') {
            if (Test-Path -LiteralPath $wlasna) {
                $wersja = WersjaJavy $wlasna
                if ($wersja -ge $MinJava) { $S.Java = $wlasna; Zakoncz 0 ("Java $wersja " + (T 'gry')); return }
            }
            Postep 0 0.3 (T 'szukam w systemie')
            $systemowa = Get-Command java -ErrorAction SilentlyContinue
            if ($systemowa) {
                $proba = ProbaJavy $systemowa.Source
                $wersja = $proba.Wersja
                if ($wersja -ge $MinJava) { $S.Java = $systemowa.Source; Zakoncz 0 ("Java $wersja " + (T 'z systemu')); return }
                if ($wersja -eq 0) {
                    # Java jest, ale nie startuje (np. brak pamieci). Pobranie drugiej Javy tu nie pomoze.
                    throw ((T 'Java jest zainstalowana, ale nie daje się uruchomić. Zamknij część programów i spróbuj ponownie. Powód: ') + (PowodBleduJavy $proba.Tekst))
                }
            }
        }
        # Javy nie ma albo jest za stara: przenosna Java trafia do folderu gry.
        Postep 0 0.08 (T 'pobieram Javę')
        $adres = AdresJavy
        $archiwum = Join-Path $Folder 'java.zip'
        PobierzPlik $adres $archiwum 0 0.08 0.78 (T 'Java')
        Postep 0 0.80 (T 'sprawdzam sumę')
        $oczekiwana = ((PobierzTekst ($adres + '.sha256.txt')).Trim() -split '\s+')[0].ToLowerInvariant()
        if ((Suma256 $archiwum) -ne $oczekiwana) {
            Remove-Item -LiteralPath $archiwum -Force
            throw (T 'Pobrana Java jest uszkodzona (suma SHA-256 się nie zgadza). Spróbuj ponownie.')
        }
        $tymczasowy = Join-Path $Folder 'java.nowa'
        if (Test-Path -LiteralPath $tymczasowy) { Remove-Item -LiteralPath $tymczasowy -Recurse -Force }
        Rozpakuj $archiwum $tymczasowy 0 0.82 0.98
        $katalog = @(Get-ChildItem -LiteralPath $tymczasowy -Directory)[0]
        if (-not $katalog) { throw (T 'Archiwum z Javą jest puste.') }
        $docelowy = Join-Path $Folder 'java'
        if (Test-Path -LiteralPath $docelowy) { Remove-Item -LiteralPath $docelowy -Recurse -Force }
        Move-Item -LiteralPath $katalog.FullName -Destination $docelowy
        Remove-Item -LiteralPath $tymczasowy -Recurse -Force
        Remove-Item -LiteralPath $archiwum -Force
        if ($NaWindows) {
            $wersja = WersjaJavy $wlasna
            if ($wersja -lt $MinJava) { throw (T 'Pobrana Java nie daje się uruchomić.') }
            $S.Java = $wlasna
            Zakoncz 0 ("Java $wersja " + (T 'pobrana'))
        } else {
            # Poza Windows (tylko testy) pobrana Java dla Windows nie ruszy; dalej idziemy z systemowa.
            $S.Java = (Get-Command java).Source
            Zakoncz 0 (T 'Java pobrana (test)')
        }
    }

    # ------------------------------------------------------------------ krok 1: serwer

    function KrokSerwer {
        Zacznij 1 (T 'pytam o wydanie')
        if ($env:WLADCA_API) { $adres = $env:WLADCA_API } else { $adres = "https://api.github.com/repos/$Repo/releases/latest" }
        try {
            $json = PobierzTekst $adres
        } catch {
            if ($_.Exception.Message -match ' 404 ') {
                throw (T 'Nie znaleziono żadnego wydania gry. Jeśli repozytorium jest prywatne, instalator nie ma do niego dostępu.')
            }
            throw
        }
        Postep 1 0.5 (T 'czytam odpowiedź')
        $wydanie = $json | ConvertFrom-Json
        $plik = $wydanie.assets | Where-Object { $_.name -eq 'wladca-lochow.jar' } | Select-Object -First 1
        if (-not $plik) { throw (T 'Najnowsze wydanie nie zawiera pliku wladca-lochow.jar.') }
        $S.Wersja = ([string]$wydanie.tag_name).TrimStart('v')
        $S.AdresJar = $plik.browser_download_url
        $S.Rozmiar = [long]$plik.size
        if ($plik.digest -and ([string]$plik.digest).StartsWith('sha256:')) {
            $S.Suma = ([string]$plik.digest).Substring(7).ToLowerInvariant()
        } else {
            $sumaPlik = $wydanie.assets | Where-Object { $_.name -eq 'wladca-lochow.jar.sha256' } | Select-Object -First 1
            if (-not $sumaPlik) { throw (T 'Wydanie nie podaje sumy SHA-256, nie da się sprawdzić pobranego pliku.') }
            Postep 1 0.7 (T 'pobieram sumę')
            $S.Suma = ((PobierzTekst $sumaPlik.browser_download_url).Trim() -split '\s+')[0].ToLowerInvariant()
        }
        $ikona = $wydanie.assets | Where-Object { $_.name -eq 'ikona.ico' } | Select-Object -First 1
        if ($ikona) { $S.AdresIkony = $ikona.browser_download_url }
        # przygotowanie pobierania: czy ta wersja juz tu jest?
        if ((Test-Path -LiteralPath $Jar) -and ((Suma256 $Jar) -eq $S.Suma)) { $S.Aktualna = $true }
        Zakoncz 1 ((T 'wersja ') + $S.Wersja)
    }

    # ------------------------------------------------------------------ krok 2: pobieranie

    # Skrot przez IShellLinkW, a nie WScript.Shell: WScript.Shell zapisuje sciezki w stronie kodowej
    # systemu, wiec na Windows po angielsku polskie litery (np. w nazwie uzytkownika) zamienia na inne.
    function Skrot([string]$sciezka, [string]$cel, [string]$argumenty, [string]$ikona) {
        if (-not ('WladcaLochow.Skroty' -as [type])) {
            Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
using System.Text;
namespace WladcaLochow {
    [ComImport, Guid("000214F9-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IShellLinkW {
        void GetPath([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder plik, int ile, IntPtr dane, uint flagi);
        void GetIDList(out IntPtr lista);
        void SetIDList(IntPtr lista);
        void GetDescription([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder opis, int ile);
        void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string opis);
        void GetWorkingDirectory([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder katalog, int ile);
        void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string katalog);
        void GetArguments([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder argumenty, int ile);
        void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string argumenty);
        void GetHotkey(out short klawisz);
        void SetHotkey(short klawisz);
        void GetShowCmd(out int tryb);
        void SetShowCmd(int tryb);
        void GetIconLocation([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder ikona, int ile, out int numer);
        void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string ikona, int numer);
        void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string sciezka, uint zarezerwowane);
        void Resolve(IntPtr okno, uint flagi);
        void SetPath([MarshalAs(UnmanagedType.LPWStr)] string plik);
    }
    [ComImport, Guid("00021401-0000-0000-C000-000000000046")]
    class ShellLink {
    }
    public static class Skroty {
        public static void Utworz(string plik, string cel, string argumenty, string katalog, string opis, string ikona) {
            IShellLinkW skrot = (IShellLinkW)new ShellLink();
            skrot.SetPath(cel);
            skrot.SetArguments(argumenty);
            skrot.SetWorkingDirectory(katalog);
            skrot.SetDescription(opis);
            if (!String.IsNullOrEmpty(ikona)) { skrot.SetIconLocation(ikona, 0); }
            ((IPersistFile)skrot).Save(plik, true);
        }
    }
}
'@
        }
        [WladcaLochow.Skroty]::Utworz($sciezka, $cel, $argumenty, $Folder, (T 'Władca Lochów'), $ikona)
    }

    function KrokPobieranie {
        Zacznij 2 (T 'zaczynam')
        if ($S.Aktualna) {
            Postep 2 0.85 (T 'gra jest aktualna')
        } else {
            $czesc = $Jar + '.part'
            PobierzPlik $S.AdresJar $czesc 2 0.04 0.85 (T 'gra')
            try {
                if (Test-Path -LiteralPath $Jar) { Remove-Item -LiteralPath $Jar -Force }
                Move-Item -LiteralPath $czesc -Destination $Jar
            } catch {
                throw (T 'Nie da się podmienić pliku gry. Zamknij grę, jeśli jest uruchomiona, i spróbuj ponownie.')
            }
        }
        $ikona = ''
        if ($S.AdresIkony) {
            Postep 2 0.88 (T 'ikona')
            $ikona = Join-Path $Folder 'ikona.ico'
            PobierzPlik $S.AdresIkony $ikona 2 0.88 0.92 (T 'ikona')
        }
        if ($NaWindows) {
            Postep 2 0.94 (T 'skróty')
            $S.Javaw = Join-Path ([IO.Path]::GetDirectoryName($S.Java)) 'javaw.exe'
            if (-not (Test-Path -LiteralPath $S.Javaw)) { $S.Javaw = $S.Java }
            $nazwa = (T 'Władca Lochów') + '.lnk'
            $argumenty = '-jar ' + $NazwaJar
            foreach ($miejsce in @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs'))) {
                if ($miejsce) {
                    Skrot (Join-Path $miejsce $nazwa) $S.Javaw $argumenty $ikona
                    # Instalator 1.1.x zapisywal skrot jako "Wladca Lochow" bez polskiej litery; zostalby obok nowego.
                    $staryPlik = Join-Path $miejsce ((T 'Wladca Lochów') + '.lnk')
                    if (Test-Path -LiteralPath $staryPlik) { Remove-Item -LiteralPath $staryPlik -Force }
                }
            }
        }
        if ($S.Aktualna) { Zakoncz 2 (T 'bez zmian') } else { Zakoncz 2 ((MB $S.Rozmiar) + ' MB') }
    }

    # ------------------------------------------------------------------ krok 3: sprawdzanie

    function KrokSprawdzanie {
        Zacznij 3 (T 'suma SHA-256')
        if ((Suma256 $Jar) -ne $S.Suma) {
            Remove-Item -LiteralPath $Jar -Force
            throw (T 'Pobrany plik gry jest uszkodzony (suma SHA-256 się nie zgadza). Spróbuj ponownie.')
        }
        Postep 3 0.45 (T 'test gry')
        $wynik = Uruchom $S.Java ('-jar ' + $NazwaJar + ' --sprawdz') $Folder
        if ($wynik.Kod -ne 0) {
            throw ((T 'Gra nie przeszła testu na tym komputerze: ') + $wynik.Tekst.Trim())
        }
        Zakoncz 3 (T 'wszystko działa')
    }

    # ------------------------------------------------------------------ podglad (bez zmian na dysku)

    function Udawaj([int]$i, [string]$opis, [int]$milisekundy, [string]$wynik) {
        Zacznij $i $opis
        $kroki = [int]($milisekundy / 60)
        for ($k = 1; $k -le $kroki; $k++) {
            Postep $i ($k / [double]$kroki) $opis
            Animuj 60
        }
        Zakoncz $i $wynik
    }

    # ------------------------------------------------------------------ konsola

    function PrzygotujKonsole {
        $S.StareKodowanie = [Console]::OutputEncoding
        [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
        if ($NaWindows) {
            try {
                if (-not ('WladcaLochow.Konsola' -as [type])) {
                    Add-Type -Namespace WladcaLochow -Name Konsola -MemberDefinition @'
[DllImport("kernel32.dll")] public static extern System.IntPtr GetStdHandle(int uchwyt);
[DllImport("kernel32.dll")] public static extern bool GetConsoleMode(System.IntPtr uchwyt, out uint tryb);
[DllImport("kernel32.dll")] public static extern bool SetConsoleMode(System.IntPtr uchwyt, uint tryb);
'@
                }
                $uchwyt = [WladcaLochow.Konsola]::GetStdHandle(-11)
                $tryb = 0
                if ([WladcaLochow.Konsola]::GetConsoleMode($uchwyt, [ref]$tryb)) {
                    $S.StaryTryb = $tryb
                    # 0x4 = obsluga sekwencji sterujacych (kolory, pozycja kursora)
                    [void][WladcaLochow.Konsola]::SetConsoleMode($uchwyt, ($tryb -bor 4))
                }
            } catch { }
        }
        [Console]::Out.Write("${CSI}?1049h${CSI}?25l")
        $S.Ekran = $true
    }

    function PrzywrocKonsole {
        if ($S.Ekran) {
            [Console]::Out.Write("${CSI}0m${CSI}?25h${CSI}?1049l")
            [Console]::Out.Flush()
            $S.Ekran = $false
        }
        if ($S.StaryTryb -ne $null) {
            try { [void][WladcaLochow.Konsola]::SetConsoleMode([WladcaLochow.Konsola]::GetStdHandle(-11), $S.StaryTryb) } catch { }
        }
        if ($S.StareKodowanie) { [Console]::OutputEncoding = $S.StareKodowanie }
    }

    function CzekajNaKlawisz {
        if ($BezStartu -or [Console]::IsInputRedirected) { Animuj 600; return }
        while ([Console]::KeyAvailable) { [void][Console]::ReadKey($true) }
        while (-not [Console]::KeyAvailable) { Klatka $false; Start-Sleep -Milliseconds 12 }
        [void][Console]::ReadKey($true)
    }

    # ------------------------------------------------------------------ przebieg

    $blad = $null
    try {
        PrzygotujKonsole
        Uklad
        Klatka $true

        if ($Podglad) {
            Udawaj 0 (T 'szukam Javy') 1200 (T 'Java 21 z systemu')
            Udawaj 1 (T 'pytam o wydanie') 900 (T 'wersja 1.2.3')
            Udawaj 2 (T 'gra 1.6/1.6 MB') 2600 '1.6 MB'
            Udawaj 3 (T 'test gry') 1200 (T 'wszystko działa')
        } else {
            [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
            Add-Type -AssemblyName System.Net.Http
            Add-Type -AssemblyName System.IO.Compression
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            $S.Klient = New-Object System.Net.Http.HttpClient
            $S.Klient.Timeout = [TimeSpan]::FromMinutes(15)
            $S.Klient.DefaultRequestHeaders.UserAgent.ParseAdd('WladcaLochow-Instalator')
            $uchwyt = New-Object System.Net.Http.HttpClientHandler
            $uchwyt.AllowAutoRedirect = $false
            $S.KlientBezPrzekierowan = New-Object System.Net.Http.HttpClient($uchwyt)
            $S.KlientBezPrzekierowan.Timeout = [TimeSpan]::FromSeconds(20)
            $S.KlientBezPrzekierowan.DefaultRequestHeaders.UserAgent.ParseAdd('WladcaLochow-Instalator')
            [void][IO.Directory]::CreateDirectory($Folder)

            KrokJava
            KrokSerwer
            KrokPobieranie
            KrokSprawdzanie
        }

        if ($Podglad) { $S.Komunikat = T 'Podgląd zakończony. Naciśnij dowolny klawisz.' }
        else { $S.Komunikat = T 'Gotowe! Naciśnij dowolny klawisz, aby zagrać.' }
        $S.KolorKomunikatu = $Kolory.Zloto
        $S.Mruga = $true
        CzekajNaKlawisz
    } catch {
        $blad = $_.Exception.Message
        foreach ($p in $S.Paski) { if ($p.Stan -eq 'trwa') { $p.Stan = 'blad'; $p.Opis = T 'nie udało się' } }
        if ($S.Ekran) {
            $S.Komunikat = T 'Instalacja przerwana. Naciśnij dowolny klawisz, aby zobaczyć szczegóły.'
            $S.KolorKomunikatu = $Kolory.Czerwien
            $S.Mruga = $false
            try { CzekajNaKlawisz } catch { }
        }
    } finally {
        PrzywrocKonsole
        if ($S.Klient) { $S.Klient.Dispose() }
        if ($S.KlientBezPrzekierowan) { $S.KlientBezPrzekierowan.Dispose() }
    }

    if ($blad) {
        Write-Host (T 'Władca Lochów: instalacja się nie powiodła.') -ForegroundColor Red
        Write-Host $blad
        return
    }
    if ($Podglad) { return }
    Write-Host ((T 'Władca Lochów ') + $S.Wersja + (T ' jest zainstalowany w: ') + $Folder)
    if ($NaWindows -and -not $BezStartu) {
        Write-Host (T 'Uruchamiam grę. Następnym razem użyj skrótu na pulpicie.')
        Start-Process -FilePath $S.Javaw -ArgumentList ('-jar ' + $NazwaJar) -WorkingDirectory $Folder
    }
}
