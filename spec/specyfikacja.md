# Generator dokumentów PDF z formularza — specyfikacja

Status: **po PoC** · Data: 2026-09-26

## 1. Cel

Ułatwić tworzenie **zbioru dokumentów** na podstawie jednego zestawu danych. Użytkownik raz wypełnia formularz (dane osobowe, adresy, wybory), a aplikacja generuje z niego **wiele plików PDF** — po jednym na każdy wybrany szablon. Te same dane pojawiają się wielokrotnie w różnych dokumentach.

## 2. Wymagania

### 2.1 Funkcjonalne

| ID | Wymaganie |
|---|---|
| F1 | Użytkownik wypełnia formularz: pola tekstowe, daty, listy wyboru, checkboxy. |
| F2 | Jeden zbiór danych wypełnia **wiele szablonów** naraz → wiele PDF-ów. |
| F3 | Użytkownik wybiera, które szablony wygenerować (domyślnie wszystkie / zestaw). |
| F4 | Szablony są dokumentami Word, edytowanymi przez **osobę nietechniczną** — bez udziału programisty. |
| F5 | Szablonów jest dużo; dodanie nowego szablonu nie wymaga zmian w kodzie. |
| F6 | Zbiór danych można zapisać i wczytać ponownie (poprawka → ponowne wygenerowanie). |
| F7 | Wybory z formularza mogą wpływać na treść dokumentu (np. tekst „☒/☐”, ewentualnie sekcje warunkowe). |
| F8 | (Opcjonalnie) scalenie wygenerowanych PDF-ów w jeden plik. |
| F9 | Akapit (fragment) oznaczony blokiem `{#skresl:pole}…{/skresl}` jest **przekreślany**, gdy checkbox `pole` nie jest zaznaczony. Znaczniki zawsze znikają. |
| F10 | Pola wyliczane: `kwota_slownie` = kwota słownie (złote i grosze słownie, z odmianą), np. „sto dwadzieścia trzy złote czterdzieści pięć groszy”. |
| F11 | Listy wyboru (select) z zamkniętą listą wartości, np. `jednostka`: miesięcznie / dziennie / godzinowo. |
| F12 | Kwota wyświetlana w formacie `1 234,50` (spacja tysięcy, przecinek dziesiętny). |
| F13 | Szablony **`.xlsx`** (Excel) obok `.docx`: wszystkie pliki `.docx` i `.xlsx` z `data/` trafiają do wspólnego PDF-a (kolejność wg nazw plików); z `.xlsx` wszystkie arkusze. Formuł raczej nie będzie, więc wartości (także kwota) wstawiane są jako sformatowany tekst, tak jak w `.docx`. Do ustalenia na pliku testowym: obszar wydruku, przekreślenie w komórkach. |

### 2.2 Niefunkcjonalne

| ID | Wymaganie |
|---|---|
| N1 | Aplikacja desktopowa, **Windows i Linux**, bez serwera. |
| N2 | Technologia: **Java** (kompetencje zespołu). |
| N3 | Wyłącznie **darmowe** komponenty (bez płatnych licencji typu Aspose). |
| N4 | Treść dokumentów: krótkie dane (adresy, dane osobowe) — **bez długich opisów**, bez tabel o zmiennej liczbie wierszy (do potwierdzenia). |
| N5 | Dane osobowe (RODO): kontrola miejsca zapisu danych i PDF-ów, sprzątanie plików tymczasowych. |
| N6 | Poprawne polskie znaki w formularzu, szablonach i PDF-ach. |

## 3. Ustalenia (decyzje wstępne)

| Obszar | Decyzja | Uzasadnienie |
|---|---|---|
| UI | **JavaFX** | Nowoczesny, wieloplatformowy, Java. |
| Definicja pól | Centralny słownik **`fields.yaml`** (nazwa, etykieta, typ, wymagalność) | Wiele szablonów musi używać tych samych nazw pól; formularz generowany ze słownika. |
| Znaczniki w szablonach | Placeholdery `{nazwa_pola}` (nazwy tylko ASCII: `a-z`, `0-9`, `_`) i bloki `{#skresl:pole}…{/skresl}` wpisywane w Wordzie | Najprostsze dla osoby nietechnicznej; ASCII eliminuje pomyłki `ę`/`e`. |
| Format szablonów | **`.docx`** (wariant A); edycja w **MS 365** | Przekreślenie warunkowe wymaga zmiany formatowania; szablony już są w `.docx`. |
| Silnik szablonów | **Własny renderer na Apache POI (XWPF)** zamiast poi-tl | poi-tl nie obsługuje przekreślenia warunkowego; własny renderer jest mały i skleja rozbite runy. |
| Konwersja do PDF | **LibreOffice headless** sterowany przez **JODConverter** (jeden proces na całą partię) | Darmowy, dobra wierność układu, działa na Windows i Linux. |
| Scalanie PDF | **Apache PDFBox** | Darmowy, prosty. |
| Zapis danych | JSON | Łatwy do wczytania/edycji. |
| Dystrybucja | **jpackage** (.msi / .deb / .rpm z wbudowanym JRE) | Użytkownik nie potrzebuje Javy. |
| Architektura | Wypełnianie szablonu schowane za interfejsem **`TemplateRenderer`** | Możliwość zmiany silnika (patrz 4.1) bez zmian w UI. |

Odrzucone:
- **Formularze PDF (AcroForm)** — przy wielu szablonach edytowanych przez nietechniczną osobę każda zmiana wymagałaby edytora PDF.
- **Aspose.Words** — płatny.
- **documents4j** — wymaga MS Word, tylko Windows.
- **docx4j / XDocReport → PDF (czysta Java)** — słabsza wierność układu.

### Przepływ

```
fields.yaml ──► [JavaFX] formularz ──► zbiór danych (JSON)
                                            │
szablony/*.doc(x) ──► [TemplateRenderer] ◄──┘
                            │  wypełnione dokumenty
                            ▼
                 [JODConverter + LibreOffice] ──► wiele PDF-ów ──► (opcjonalnie) [PDFBox] scalenie
```

## 4. Otwarte kwestie

### 4.1 Format szablonów: `.doc` (Office 97–2003) vs `.docx` — **rozstrzygnięte: wariant A (`.docx`)**

Powód: przekreślenie warunkowe (F9), szablony już w `.docx`, docelowy edytor MS 365. Ewentualne stare `.doc` zapisuje się raz w Wordzie jako `.docx`.

Istniejące szablony to prawdopodobnie stare pliki `.doc`. Biblioteka **poi-tl obsługuje tylko `.docx`**.

| Wariant | Opis | Plusy | Minusy |
|---|---|---|---|
| **A (rekomendowany)** | Jednorazowa migracja `.doc` → `.docx` (najlepiej w Wordzie: „Zapisz jako”), wypełnianie przez **poi-tl**, LibreOffice tylko do PDF | Warunki `{{?pole}}…{{/pole}}`, walidator placeholderów, czysta Java | Wymaga migracji i pracy dalej na `.docx` |
| **B** | Zostają pliki `.doc`; podmiana tekstu przez **LibreOffice UNO API** (znajdź i zamień) + eksport do PDF | Brak migracji; działa z `.doc`, `.docx`, `.odt` | Tylko prosta zamiana tekstu; toporne API; nagłówki/stopki/pola tekstowe do sprawdzenia |

Kryterium wyboru: czy osoba edytująca ma nowszego Worda i czy coś wymusza format `.doc`.

### 4.2 Pozostałe pytania

1. ~~Czy osoba edytująca szablony ma MS Word?~~ Tak, MS 365.
2. Ile jest szablonów i ile unikalnych pól w sumie (rząd wielkości)?
3. Czy szablony grupują się w zestawy (np. „komplet dla klienta indywidualnego”)?
4. Czy potrzebne są sekcje warunkowe, czy wystarczy wstawianie wartości?
5. Jak mają się nazywać wygenerowane pliki PDF (np. `<szablon>_<nazwisko>_<data>.pdf`) i gdzie trafiać?
6. Czy LibreOffice może być instalowany osobno, czy ma być dołączony do instalatora (wersja portable na Windows)?
7. Czy potrzebne jest scalenie PDF-ów w jeden plik?
8. Czy są wymagania co do przechowywania danych osobowych (szyfrowanie, automatyczne usuwanie)?

## 5. Znane ryzyka

| Ryzyko | Mitygacja |
|---|---|
| **Czcionki** — brak czcionki szablonu (np. Calibri) na Linuksie zmienia układ | Narzucić czcionki dostępne wszędzie (Liberation, DejaVu, Carlito) lub dołączyć pliki czcionek do instalacji. |
| **Czcionka Aptos** (domyślna w MS 365) nie ma darmowego odpowiednika metrycznego → PDF z LibreOffice rozjedzie się względem Worda | W szablonach używać Calibri (→ Carlito), Arial (→ Liberation Sans), Times New Roman (→ Liberation Serif), Cambria (→ Caladea); walidator ostrzega o innych. |
| **Literówki w placeholderach** (`{nazwsiko}`) lub zły placeholder w złym miejscu (w pierwszej wersji szablonu wiersz PESEL zawierał `{nr-telefonu}`) | Renderer zgłasza placeholder bez wartości (fail fast); walidator szablonów; próbny PDF z przykładowymi danymi. |
| **Wierność `.doc` w LibreOffice** — ramki, pola tekstowe, makra, stare pola formularzy Worda, złożone tabele | Test konwersji na reprezentatywnych szablonach (krok 1 w sekcji 6). |
| **Brak LibreOffice** na komputerze użytkownika | Sprawdzenie przy starcie + zrozumiały komunikat; ewentualnie dołączenie do instalatora. |
| **RODO** — dane osobowe w JSON/PDF/plikach tymczasowych | Konfigurowalne katalogi, usuwanie tymczasowych `.docx` po konwersji. |

## 6. Wyniki PoC (2026-09-26)

Szablon `data/testowy-dokument.docx`, pola w `config/fields.yaml`: `imie`, `nazwisko`, `pesel` (tekst), `nazwa_firmy` (select: Firma 1 / Firma 2 / Firma z bardzo długą nazwą), `kwota`, `jednostka` (select), `kwota_slownie` (wyliczane), `dodatkowy_checkbox` (checkbox → `{#skresl:dodatkowy_checkbox}`).

- **Rozbite runy potwierdzone w praktyce**: w pliku `{imie}` było zapisane jako `{imi` + `e` + `}`, a `{pesel}` i `{nazwa_firmy}` podobnie. Renderer (`ParagraphText`) skleja tekst akapitu, podmienia wartość z formatowaniem pierwszego fragmentu i dzieli runy na granicach przekreślenia. To wymóg, nie ryzyko.
- Przekreślenie i jego brak, kwota słownie, format kwoty, polskie znaki w PDF: zweryfikowane testami i wizualnie.
- Uruchomienie:
  - testy: `mvn verify` (test z LibreOffice jest pomijany, gdy LibreOffice nie jest zainstalowany),
  - bez UI: `mvn compile exec:java -Dexec.args="data output/dokumenty.pdf imie=Jan nazwisko=Kowalski pesel=90010112345 'nazwa_firmy=Firma 1' kwota=1234,5 jednostka=dziennie dodatkowy_checkbox=false"`,
  - formularz: `mvn javafx:run` → „Generuj PDF” → `output/dokumenty.pdf`.
- **Wiele szablonów → jeden PDF**: wszystkie pliki `.docx` z `data/` są wypełniane w kolejności nazw plików (kolejność ustala się prefiksem, np. `01_wniosek.docx`, `02_umowa.docx`), konwertowane i sklejane (PDFBox) w `output/dokumenty.pdf`. Pliki blokady Worda (`~$…`) i LibreOffice (`.~lock…`) są pomijane. Błąd w szablonie podaje nazwę pliku szablonu. Każdy szablon musi mieć wartości dla wszystkich swoich pól.
- Poza zakresem PoC: wybór podzbioru szablonów, zapis danych do JSON, walidator szablonów, blok `skresl` przez kilka akapitów, scalanie PDF, instalator (jpackage), porządek z logowaniem (komunikaty SLF4J/Log4j przy starcie).

### Dystrybucja (bez JDK i bez źródeł)

`scripts/build-dist.sh` buduje:
- `target/dist/formularz-windows.zip`: `formularz.exe` (Launch4j, JAR wbudowany w exe), `jre/` (Temurin JRE 25 dla Windows), `config/`, `data/`,
- `target/dist/formularz-linux.zip`: `formularz.jar`, `uruchom.sh`, `jre/` (Temurin JRE 25 dla Linuksa), `config/`, `data/`.

JAR zawiera natywne części JavaFX dla Windows i Linuksa. Na komputerze użytkownika trzeba zainstalować tylko **LibreOffice** (na Windows w domyślnym katalogu). Exe ustawia katalog roboczy na swój folder, więc ścieżki `config/`, `data/` i `output/` działają po dwukliku.
Sprawdzone: paczka Linux (PDF + okno) oraz exe w Wine 11.17 (start na wbudowanym JRE, okno). Na prawdziwym Windows jeszcze nie testowane.

### Konwencja dla osoby edytującej szablony

1. Pole: `{nazwa}`, małe litery bez polskich znaków, słowa łączone `_` (np. `{nazwa_firmy}`). Lista nazw w `config/fields.yaml`.
2. Fragment do przekreślenia: `{#skresl:nazwa_checkboxa}tekst{/skresl}` (na razie w obrębie jednego akapitu).
3. Czcionki: Calibri, Arial, Times New Roman lub Cambria (nie Aptos).

## 7. Następne kroki

1. Zebrać kolejne prawdziwe szablony (w `.docx`, z MS 365) i sprawdzić je w PoC.
2. Odpowiedzieć na pozostałe pytania z sekcji 4.2 (zestawy szablonów, nazewnictwo i miejsce zapisu PDF-ów, scalanie, RODO).
3. Uzupełnić słownik pól `config/fields.yaml` o pola z nowych szablonów.
4. Wybór podzbioru szablonów (zestawy) w formularzu; ewentualnie osobne PDF-y zamiast jednego.
5. Zapis i odczyt zbioru danych (JSON).
6. Walidator szablonów: placeholdery spoza `fields.yaml`, niedomknięte bloki, niedozwolone czcionki; próbny PDF.
7. Blok `skresl` przez kilka akapitów (jeśli szablony tego wymagają).
8. Pakowanie jpackage na Windows i Linux, sprawdzenie LibreOffice na Windows.
