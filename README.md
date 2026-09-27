# Kotlin Ray Tracer

Path tracer z książek *Ray Tracing in One Weekend* / *The Next Week* / *The Rest of Your Life*,
napisany w Kotlinie, liczony na GPU przez OpenCL, z odszumianiem Intel Open Image Denoise
i interaktywnym oknem podglądu.

## Wymagania

- **Windows x64** (biblioteki odszumiacza i okno trzymające zegary GPU są przygotowane pod Windows;
  sam render działa też na Linuksie).
- **Karta graficzna z OpenCL** – testowane na NVIDIA GTX 1070. Bez GPU renderer sam przełącza się na CPU
  (dużo wolniej, bez okna i bez odszumiania).
- **JDK 17 lub nowszy** (testowane na Temurin 21) i **Maven**.

Nic więcej nie trzeba instalować: odszumiacz OIDN jest w repozytorium (`native/oidn/windows-x64`).

## Uruchomienie interfejsu graficznego

Z katalogu repozytorium:

```
mvn compile
mvn exec:java -Drt.gui -Drt.scene=cornell-final
```

Otworzy się okno ze sceną. Obraz liczy się od razu i wyostrza w miarę zbierania próbek.

Kilka przydatnych wariantów:

```
# większe okno
mvn exec:java -Drt.gui -Drt.scene=cornell-final -Drt.width=900

# od razu z modelem na scenie (można podać kilka, rozdzielonych średnikiem)
mvn exec:java -Drt.gui -Drt.scene=cornell-final "-Drt.gui.models=../stls/witcher/witcher_assembled.stl"

# z włączonym światłem przednim i kolumną w szachownicę
mvn exec:java -Drt.gui -Drt.scene=cornell-final -Drt.fill=0.4 -Drt.block=checker
```

Duże modele (setki tysięcy – miliony trójkątów) potrzebują więcej pamięci dla Javy.
W PowerShell przed uruchomieniem:

```
$env:MAVEN_OPTS = "-Xmx12g"
```

(w bashu: `export MAVEN_OPTS=-Xmx12g`).

Inne sceny: `bouncing-spheres`, `earth`, `perlin-spheres`, `quads`, `simple-light`, `cornell-box`,
`cornell-blocks`, `cornell-smoke`, `final-week`, `cornell-final`, `showcase`, `cornell-model`, `model`.
Najwięcej funkcji okna (światła do przełączania, obiekty sceny do usunięcia) mają `cornell-final`
i `cornell-model`.

## Sterowanie

Pełna lista jest też w oknie pod **F1** (albo **H**, albo przycisk *Pomoc* w panelu).

| Akcja | Skrót |
|---|---|
| Obrót kamery wokół punktu, na który patrzy | lewy przycisk myszy + przeciąganie |
| Przesuwanie kamery | Shift + lewy przycisk + przeciąganie, albo środkowy przycisk |
| Przybliżanie / oddalanie | kółko myszy albo prawy przycisk + przeciąganie |
| Kąt widzenia | `+` / `-` |
| Reset widoku | `R` |
| Postaw zaznaczony model na podłodze pod kursorem | Ctrl + klik w obraz |
| Usuń zaznaczony obiekt | `Delete` |
| Odszumianie włącz / wyłącz | `D` |
| Zapisz obraz (PPM + PNG, z odszumioną kopią) | `S` |
| Pomoc | `F1` / `H` |
| Zamknij | `Esc` |

Podczas ruchu kamery podgląd liczy się w 1/4 rozdzielczości przy 1 próbce na piksel, żeby był płynny.
Po zatrzymaniu (150 ms) obraz przechodzi na pełną rozdzielczość, zbiera próbki do celu ustawionego
w panelu i jest odszumiany przy 16, 64, 256… próbkach oraz na końcu.

## Panel boczny

- **Obraz** – gotowe rozdzielczości albo własna szerokość i wysokość (*Zastosuj rozdzielczość*),
  **Cel spp** (liczba próbek, przy której render się kończy) z paskiem postępu, odszumianie, zapis.
- **Światła** – lampa sufitowa i światło przednie do włączania i wyłączania.
- **Obiekty na scenie** – lista obiektów: kolumna i szklana kula sceny (można je usunąć i przywrócić
  przyciskiem *Przywróć obiekty sceny*) oraz dodane modele. *Dodaj model…* wczytuje STL, OBJ, PLY,
  OFF, glTF/GLB albo 3MF; zaznaczony model można ustawić polami X / Y / Z, obrotem, wysokością
  i materiałem, albo postawić Ctrl + klikiem. Materiał to gotowy zestaw (szary, biały, czerwony
  i niebieski mat, szkło, diament, lustro, złoto, miedź, stal szczotkowana, świecący biały) albo własny:
  rodzaj (matowy, metal, szkło, świecący), kolor z okna wyboru i parametr zależny od rodzaju
  (chropowatość metalu, współczynnik załamania szkła, moc światła).
  *Oś Z w górę* włącza się sama dla plików do druku 3D (STL, 3MF).

Zapisane obrazy trafiają do katalogu roboczego (albo tam, gdzie wskazuje `-Drt.out`) jako
`viewer-<scena>-<data>.ppm/.png` i `…-denoised.ppm/.png`.

## Render do pliku (bez okna)

Bez `-Drt.gui` program renderuje scenę do pliku i kończy działanie:

```
mvn exec:java -Drt.scene=cornell-final -Drt.width=2000 -Drt.samples=2000 -Drt.out=render.ppm
```

Powstają `render.ppm`, `render.png` i odszumione `render-denoised.ppm/.png`
(`-Drt.denoise=off` wyłącza odszumianie). Pozostałe atrybuty – rozdzielczość, próbki, modele,
materiały, światła – opisuje `AGENTS.md`.

## Rozwiązywanie problemów

- **Czarne okno albo „GPU rendering unavailable”** – brak sterownika OpenCL; zaktualizuj sterownik karty.
- **„bez odszumiania (brak OIDN)” w pasku stanu** – brakuje `native/oidn/windows-x64`
  (np. uruchomiono spoza katalogu repozytorium); wskaż go przez `-Drt.oidn=<ścieżka>`.
- **OutOfMemoryError przy dużym modelu** – zwiększ `MAVEN_OPTS` (`-Xmx12g` lub więcej).
