# L2 Phantom AI — Port para High Five (Chronicle 2.6)

<p align="center">
  <a href="#portugues">🇧🇷 Português</a> ·
  <a href="#espanol">🇪🇸 Español</a> ·
  <a href="#english">🇬🇧 English</a>
</p>

> **Sistema original / Sistema original / Original system:** **Cristian Barboza — MiaCodeWEB** ([miacodeweb.com](https://miacodeweb.com)).
> Port para High Five por **Rekiem Games Network**, com a permissão do autor / con permiso del autor / with the author's permission.
>
> Projetos originais do autor / Proyectos originales del autor / Author's original projects:
> - Mobius Essence (RoseVain): https://www.l2jbrasil.com/topic/150633-mod-l2-phantom-ai-manager-bots-inteligentes-geodata-fix-gemini-ai-l2j-mobius-essence-rosevain/
> - aCis 409: https://www.l2jbrasil.com/topic/150725-mod-l2-phantom-ai-manager-bots-inteligentes-gemini-ai-l2j-acis-l2jacis-409/
> - GitHub: https://github.com/miacodeweb/L2-Phantom-AI

---

## Português <a name="portugues"></a>

Port funcional do sistema **L2 Phantom AI** para **L2J Mobius CT 2.6 High Five**. O crédito ao MiaCodeWEB está visível in-game (banner de inicialização do GameServer e rodapé do painel `.pmenu`).

### O que é

Jogadores **phantom** (bots) autônomos que fazem o servidor parecer vivo: nascem, evoluem em spots reais do datapack, se equipam por grade, farmam, usam skills, fazem PvP/PK entre si, conversam, morrem e revivem. São personagens reais do banco de dados — **client-less** (`Player.create()/load()` sem `GameClient`, conta `phantom_ai`) — e por isso **não interferem com sistemas HWID / anti-multiconta** baseados em `EnterWorld`. Tudo é gerenciado in-game pelo painel de GM (`.pmenu`). O que este port adicionou sobre o sistema original está detalhado em [`PORT_NOTES.md`](PORT_NOTES.md).

### Estrutura

```
data/scripts/custom/PhantomManager/   ← os 14 scripts do datapack (compilam no boot do GS)
config/Custom/PhantomPlayers.xml       ← persistência dos IDs de phantoms (vazio no início)
core-patches/                          ← 3 patches ao CORE do servidor (OBRIGATÓRIOS)
PORT_NOTES.md                          ← mapeamento Essence→HF, detalhe das mudanças
```

### Instalação

1. Copie `data/scripts/custom/PhantomManager/` para o seu `dist/game/data/scripts/custom/`.
2. Copie `config/Custom/PhantomPlayers.xml` para o seu `dist/game/config/Custom/`.
3. **Aplique os 3 patches de `core-patches/`**.
4. Recompile o GameServer e reinicie.
5. Com um GM: `.pmenu`.

### ⚠️ Os 3 patches ao core são OBRIGATÓRIOS

Um `Player` sem `GameClient` quebra suposições do core que assumem um `EnterWorld` ou a existência de cliente. Sem eles o sistema **compila mas se comporta mal**:

| Patch | Arquivo | O que corrige |
|---|---|---|
| `01-Player-broadcastCharInfo` | `Player.java` | `broadcastCharInfo()` descartava as atualizações visuais em tempo real de players sem cliente (karma, buffs, equipamento). `isOnlineInt()==0` → `!isOnline()`. |
| `02-Q00255_Tutorial-onKill-nullguard` | `Q00255_Tutorial.java` | NPE a cada mob de tutorial morto por um phantom (sem quest state). Null-guard. |
| `03-ChatWhisper-offline-mode` | `ChatWhisper.java` | Sussurrar a um phantom retornava *"Player is in offline mode."*. Limitado aos vendedores offline reais. |

### Testado

L2J Mobius CT 2.6 High Five, JDK 25, in-game. Teste de carga informal: ~1000 phantoms ativos em VPS 12GB/4-cores → CPU 53–73%, 0 erros.

---

## Español <a name="espanol"></a>

Port funcional del sistema **L2 Phantom AI** a **L2J Mobius CT 2.6 High Five**. El crédito a MiaCodeWEB está visible in-game (banner de arranque del GameServer y pie del panel `.pmenu`).

### Qué es

Jugadores **phantom** (bots) autónomos que hacen que el servidor parezca vivo: nacen, suben de nivel en spots reales del datapack, se equipan por grado, farmean, usan skills, hacen PvP/PK entre ellos, chatean, mueren y reviven. Son personajes reales de la base de datos — **client-less** (`Player.create()/load()` sin `GameClient`, cuenta `phantom_ai`) — por lo que **no interfieren con sistemas HWID / anti-multicuenta** basados en `EnterWorld`. Todo se gestiona in-game desde el panel de GM (`.pmenu`). Lo que este port añadió sobre el sistema original está detallado en [`PORT_NOTES.md`](PORT_NOTES.md).

### Estructura

```
data/scripts/custom/PhantomManager/   ← los 14 scripts del datapack (compilan en el boot del GS)
config/Custom/PhantomPlayers.xml       ← persistencia de IDs de phantoms (vacío al empezar)
core-patches/                          ← 3 parches al CORE del servidor (OBLIGATORIOS)
PORT_NOTES.md                          ← mapeo Essence→HF, detalle de cambios
```

### Instalación

1. Copia `data/scripts/custom/PhantomManager/` a tu `dist/game/data/scripts/custom/`.
2. Copia `config/Custom/PhantomPlayers.xml` a tu `dist/game/config/Custom/`.
3. **Aplica los 3 parches de `core-patches/`**.
4. Recompila el GameServer y reinicia.
5. Con un GM: `.pmenu`.

### ⚠️ Los 3 parches al core son OBLIGATORIOS

Un `Player` sin `GameClient` rompe supuestos del core que asumen un `EnterWorld` o la existencia de cliente. Sin ellos el sistema **compila pero se comporta mal**:

| Parche | Fichero | Qué arregla |
|---|---|---|
| `01-Player-broadcastCharInfo` | `Player.java` | `broadcastCharInfo()` descartaba las actualizaciones visuales en vivo de players sin cliente (karma, buffs, equipo). `isOnlineInt()==0` → `!isOnline()`. |
| `02-Q00255_Tutorial-onKill-nullguard` | `Q00255_Tutorial.java` | NPE por cada mob de tutorial que mata un phantom (sin quest state). Null-guard. |
| `03-ChatWhisper-offline-mode` | `ChatWhisper.java` | Susurrar a un phantom devolvía *"Player is in offline mode."*. Limitado a vendedores offline reales. |

### Probado

L2J Mobius CT 2.6 High Five, JDK 25, in-game. Test de carga informal: ~1000 phantoms activos en VPS 12GB/4-cores → CPU 53–73%, 0 errores.

---

## English <a name="english"></a>

Working port of the **L2 Phantom AI** system to **L2J Mobius CT 2.6 High Five**. Credit to MiaCodeWEB is shown in-game (GameServer boot banner and the footer of the `.pmenu` panel).

### What it is

Autonomous **phantom** players (bots) that make the server feel alive: they spawn, level up at real datapack hunting spots, gear up by grade, farm, use skills, PvP/PK each other, chat, die and revive. They are real database characters — **client-less** (`Player.create()/load()` with no `GameClient`, account `phantom_ai`) — so they **don't interfere with HWID / anti-multibox systems** based on `EnterWorld`. Everything is managed in-game from the GM panel (`.pmenu`). What this port added on top of the original system is detailed in [`PORT_NOTES.md`](PORT_NOTES.md).

### Structure

```
data/scripts/custom/PhantomManager/   ← the 14 datapack scripts (compiled on GS boot)
config/Custom/PhantomPlayers.xml       ← phantom ID persistence (empty at first)
core-patches/                          ← 3 server CORE patches (REQUIRED)
PORT_NOTES.md                          ← Essence→HF mapping, change details
```

### Installation

1. Copy `data/scripts/custom/PhantomManager/` into your `dist/game/data/scripts/custom/`.
2. Copy `config/Custom/PhantomPlayers.xml` into your `dist/game/config/Custom/`.
3. **Apply the 3 patches in `core-patches/`**.
4. Rebuild the GameServer and restart.
5. As a GM: `.pmenu`.

### ⚠️ The 3 core patches are REQUIRED

A `Player` with no `GameClient` breaks core assumptions that expect an `EnterWorld` or a live client. Without them the system **compiles but misbehaves**:

| Patch | File | What it fixes |
|---|---|---|
| `01-Player-broadcastCharInfo` | `Player.java` | `broadcastCharInfo()` dropped all live visual updates for clientless players (karma, buffs, gear). `isOnlineInt()==0` → `!isOnline()`. |
| `02-Q00255_Tutorial-onKill-nullguard` | `Q00255_Tutorial.java` | NPE on every tutorial mob killed by a phantom (no quest state). Null-guard. |
| `03-ChatWhisper-offline-mode` | `ChatWhisper.java` | Whispering a phantom returned *"Player is in offline mode."*. Restricted to real offline store traders. |

### Tested

L2J Mobius CT 2.6 High Five, JDK 25, in-game. Informal load test: ~1000 active phantoms on a 12GB/4-core VPS → CPU 53–73%, 0 errors.

---

## Créditos / Créditos / Credits

Original system **L2 Phantom AI Manager** by **MiaCodeWEB — Cristian Barboza** ([miacodeweb.com](https://miacodeweb.com) · contacto@miacodeweb.com). High Five port by **Rekiem Games Network**, with the author's permission.

## Screenshots

<img width="325" height="415" alt="image" src="https://github.com/user-attachments/assets/d2bd80be-45c8-4598-8ea3-11d51eba8e0d" />
<img width="334" height="419" alt="image" src="https://github.com/user-attachments/assets/caebe611-d456-4db6-86bc-c15b8657d3c6" />
<img width="329" height="423" alt="image" src="https://github.com/user-attachments/assets/65770ad0-7d9a-4d9a-b036-3e63fa0daea7" />
