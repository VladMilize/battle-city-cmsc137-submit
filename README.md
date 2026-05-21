# Battle City — CMSC 137

---

## How to Run

### Linux (Native)

Make sure you have JDK 17+ installed:
```bash
sudo apt install openjdk-21-jdk
```

Then run:
```bash
chmod +x run.sh
./run.sh
```

> If `run.sh` fails, open it and update the `JAVA=` line to point to your actual Java bin folder. Find it with `dirname $(which javac)`.

---

### Windows + WSL

1. Open **WSL** (Ubuntu terminal)
2. Navigate to the project folder, e.g.:
   ```bash
   cd /mnt/c/Users/YourName/Downloads/BattleCity
   ```
3. Run the WSL-specific script:
   ```bash
   chmod +x run-windows-wsl.sh
   ./run-windows-wsl.sh
   ```

> The script will auto-detect your Java installation — either from WSL's apt packages or your Windows JDK under `/mnt/c`. If Java isn't found, it will attempt to install OpenJDK 21 automatically.

> **Graphics note:** WSL needs a display server to render the game window. On Windows 11, WSLg handles this automatically. On Windows 10, install [VcXsrv](https://sourceforge.net/projects/vcxsrv/) or [Xming](https://sourceforge.net/projects/xming/), launch it, then run:
> ```bash
> export DISPLAY=:0
> ```
> before running the script.

---

### Windows (Native, no WSL)

1. Make sure [JDK 17+](https://adoptium.net/) is installed and added to your PATH
2. Open **Command Prompt** or **PowerShell** in the project folder
3. Run:
   ```bat
   mkdir out
   for /r src %f in (*.java) do javac -d out "%f"
   xcopy /e /y res\* out\
   java -cp out Main
   ```

> You can paste all four lines at once into Command Prompt.

---

## Local Game

- ENTER / SPACE / L on main menu to start
- WASD = Tank 1 (Red), Arrow Keys = Tank 2 (Blue)
- ENTER = shoot (Tank 1), SPACE = shoot (Tank 2)
- ESC = pause

---

## Network Game

- H = Host (up to 4 players), J = Join
- Enter your name and choose a team
- Host presses ENTER to start when enough players are connected
- T = team chat, Y = all chat, ESC = pause
- R = rematch after game ends, M = back to main menu

---

## Win Conditions

- Destroy the enemy Eagle OR eliminate all enemy tanks

---

## Requirements

- JDK 17+
- Same local network for multiplayer
- Port 5000 open on host machine
