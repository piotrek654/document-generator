# Document Generator — Generator dokumentów PDF

Aplikacja desktopowa w języku Java służąca do automatycznego generowania i scalania dokumentów PDF z wielu szablonów `.docx` (Word) oraz `.xlsx`/`.xls` (Excel) na podstawie danych wprowadzonych w formularzu (interfejs graficzny JavaFX) lub przekazanych z wiersza poleceń (CLI).

---

## 1. Wymagania

### Środowisko programistyczne (do kompilacji i budowania)
- **JDK (Java Development Kit) 25** (np. Eclipse Temurin 25)
- **Apache Maven 3.8+**
- **LibreOffice** (wymagany do konwersji `.docx` i `.xlsx`/`.xls` do formatu PDF; testy integracyjne konwertera są uruchamiane tylko wtedy, gdy LibreOffice jest zainstalowany w systemie)
- **Narzędzia powłoki Bash** (do skryptu dystrybucyjnego): `bash`, `curl`, `zip`, `unzip`, `tar` oraz dostęp do Internetu (do pobrania JRE)

### Środowisko uruchomieniowe użytkownika końcowego
- **Paczka ze skryptu dystrybucyjnego (`target/dist/document-generator-windows.zip`)**:
  - Nie wymaga instalacji Javy (pacza zawiera wbudowane środowisko Temurin JRE 25).
  - Wymaga zainstalowanego programu **LibreOffice** (w standardowej ścieżce, np. `C:\Program Files\LibreOffice`).

---

## 2. Kompilacja i uruchamianie

### Kompilacja i testy
```bash
# Kompilacja kodu źródłowego
mvn clean compile

# Uruchomienie testów jednostkowych
mvn test

# Pełna weryfikacja z testami integracyjnymi
mvn verify
```

### Budowanie pojedynczego pliku JAR oraz EXE
```bash
mvn package
```
W katalogu `target/` powstaną:
- `target/document-generator.jar` – tzw. *fat JAR* (zawiera wszystkie zależności, w tym natywne biblioteki JavaFX dla Windows i Linux).
- `target/document-generator.exe` – plik wykonywalny Windows utworzony przez plugin `launch4j-maven-plugin` (opakowujący JAR i odwołujący się do lokalnego katalogu `jre/`).

### Uruchamianie w trybie deweloperskim

#### Interfejs graficzny (JavaFX)
```bash
mvn javafx:run
```

#### Tryb wiersza poleceń (CLI)
```bash
mvn exec:java -Dexec.args="data output/dokumenty.pdf imie=Jan nazwisko=Kowalski pesel=90010112345 'nazwa_firmy=Firma 1' kwota=1234,5 jednostka=dziennie dodatkowy_checkbox=false"
```

---

## 3. Tworzenie paczki dystrybucyjnej dla Windows

Projekt zawiera zautomatyzowany skrypt tworzący gotowe paczki zip zawierające aplikację wraz z wbudowanym środowiskiem Java (JRE), dzięki czemu użytkownik końcowy nie musi instalować Javy na swoim komputerze.

### Wymagania do stworzenia paczki:
- Środowisko uniksowe / powłoka Bash (Linux, macOS, WSL lub Git Bash na Windows).
- Zainstalowane narzędzia: `mvn`, `curl`, `zip`, `unzip`, `tar`.
- Połączenie internetowe (skrypt automatycznie pobiera oficjalne archiwum Eclipse Temurin JRE 25 z API Adoptium i zapisuje je w pamięci podręcznej `~/.cache/document-generator-jre`).

### Jak stworzyć paczkę:
Uruchom skrypt budujący z katalogu głównego projektu:
```bash
./scripts/build-dist.sh
```

### Co robi skrypt:
1. Kompiluje projekt i tworzy `target/document-generator.jar` oraz `target/document-generator.exe` (`mvn -q clean package`).
2. Pobiera (jeśli nie ma w cache) i wypakowuje 64-bitowy **Temurin JRE 25 dla Windows** do podkatalogu `jre/`.
3. Kopiuje pliki konfiguracyjne (`config/`) oraz szablony (`data/`).
4. Pakuje całość do archiwum ZIP:
   - `target/dist/document-generator-windows.zip`
   *(skrypt równolegle generuje również wersję dla Linuksa: `target/dist/document-generator-linux.zip`)*.

---

## 4. Zawartość paczki Windows i uruchomienie przez użytkownika

### Struktura paczki `document-generator-windows.zip`:
```text
document-generator/
├── document-generator.exe       # Główny plik uruchamiający aplikację
├── document-generator.jar       # Plik JAR aplikacji
├── uruchom.bat                  # Skrypt uruchamiający (alternatywa dla .exe)
├── jre/                         # Wbudowane środowisko Java 25 (x64)
├── config/
│   └── fields.yaml              # Konfiguracja pól formularza
└── data/
    └── *                        # Szablony dokumentów (.docx, .xlsx, .xls)
```

### Wymagania i uruchomienie na komputerze z Windows:
1. Zainstalowany program **LibreOffice** (dostępny bezpłatnie na [libreoffice.org](https://www.libreoffice.org/)).
2. Wypakowanie archiwum `document-generator-windows.zip`.
3. Uruchomienie aplikacji poprzez dwuklik na plik `document-generator.exe` (lub `uruchom.bat`).
4. Wygenerowane dokumenty zapisywane są w katalogu `output/dokumenty.pdf`.

---

## 5. Struktura katalogów projektu

- `config/fields.yaml` — definicje pól formularza (tekst, data, kwota, listy wyboru, checkboxy, kwota słownie).
- `data/` — szablony dokumentów w formacie `.docx`, `.xlsx` oraz `.xls` z placeholderami `{nazwa_pola}` oraz blokami `{#skresl:checkbox}...{/skresl}`.
- `output/` — domyślny katalog zapisu wygenerowanego scalonego pliku PDF (`dokumenty.pdf`).
- `scripts/build-dist.sh` — skrypt budujący kompletne paczki dystrybucyjne z wbudowanym JRE.
- `scripts/uruchom.sh` — skrypt startowy dla środowiska Linux.
