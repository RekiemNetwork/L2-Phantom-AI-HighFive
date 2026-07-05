# Port Notes: Essence → High Five

<p align="center">
  <a href="#pt">🇧🇷 Português</a> ·
  <a href="#es">🇪🇸 Español</a> ·
  <a href="#en">🇬🇧 English</a>
</p>

Documento técnico da adaptação / Documento técnico de la adaptación / Technical notes on the adaptation.

---

## Português <a name="pt"></a>

### 1. Mapeamento de API Essence (RoseVain) → High Five (CT 2.6)

Os paths `org.l2jmobius.*` são idênticos entre as crônicas (o Mobius unificou o naming), então a maior parte compilou direto. As mudanças foram de API/semântica:

| Essence | High Five | Nota |
|---|---|---|
| `setReputation(-X)` | `setKarma(+X)` | **Sinal invertido**: em HF o karma é positivo para PK. |
| `broadcastReputation()` | `broadcastKarma()` | |
| `chargeShot(type)` | `setChargedShot(type, true)` | |
| `doAutoAttack(target)` | `getAI().setIntention(Intention.ATTACK, target)` | O `doAttack` de HF é um único golpe; a intenção ATTACK mantém o ciclo. |
| `getInstanceWorld()` / `setInstance()` | `getInstanceId()` / `setInstanceId(int)` | HF usa ids `int`. |
| `PlayerClass.KAMAEL_SOLDIER` | `MALE_SOLDIER` / `FEMALE_SOLDIER` | Classes base Kamael têm gênero fixo em HF. |
| `Race.SYLPH`, `Race.HIGH_ELF`, `SYLPH_GUNNER` | *(removidos)* | Não existem em HF. |

### 2. Armadilhas de players client-less resolvidas

- **Idioma nulo + multilang**: sem cliente, `_lang` fica nulo; com multilang ativo, `sendDamageMessage` estourava com NPE em `NpcNameLocalisationData` e abortava a cadeia de dano. Fix: `setLang(default)` ao preparar o phantom.
- **IDs de items entre crônicas**: os IDs de Essence apontavam para items diferentes em HF. Todos os packs foram reconstruídos com IDs verificados contra `data/stats/items` de HF.
- **Spawns territoriais HF** (`<spawn zone=>`): sem coordenadas por-npc; resolvidos ao centroide do `<territory>`.
- **Skills de summon**: filtradas pela classe do efeito `Summon` real (não pelo nome).
- **Servitor + login**: restauração do servitor executada ao logar (senão `character_summons` bloqueia `canSummon`).
- **`online` no banco**: forçado a `1` ao logar e reafirmado a cada tick (o autosave o reescreve para 0 sem cliente).

Os 3 patches de `core-patches/` são a parte disto que não dá para resolver pelo datapack.

### 3. Original do autor vs. adicionado no port

**Sistema original (MiaCodeWEB), adaptado a HF:** arquitetura modular, criação client-less, farm em spots reais, spawn com geodata, metas de nível com descanso, MP-rest para mages, packs de equipamento por grade, shots automáticos, limpeza de inventário, PvP/PK, painel `.pmenu`, chat opcional (Gemini).

**Adicionado no port (Rekiem):** gerenciador de população autônomo (curva horária + sessões + auto-start no boot), pirâmide de níveis + faixa manual, comportamento humano (poções em combate, fuga com HP crítico, sentar-regenerar com decisão lutar-ou-fugir, mages priorizam magia, summoners com servitor assistindo e viajando junto), painel GM ampliado (lista paginada, chat público, toggles persistentes), PK com nível mínimo + drops retail + re-equipamento automático, e refinos de realismo (teleport tímido, anti-aglomeração, aparência válida, nomes variados).

### 4. Compatibilidade

L2J Mobius CT 2.6 High Five, JDK 25, in-game. Não interfere com HWID (client-less). ~1000 phantoms → CPU 53–73%, 0 erros. Config em `config/Custom/PhantomAI.ini` (gerado no primeiro arranque).

---

## Español <a name="es"></a>

### 1. Mapeo de API Essence (RoseVain) → High Five (CT 2.6)

Los paths `org.l2jmobius.*` son idénticos entre crónicas (Mobius unificó el naming), así que el grueso compiló directo. Los cambios fueron de API/semántica:

| Essence | High Five | Nota |
|---|---|---|
| `setReputation(-X)` | `setKarma(+X)` | **Signo invertido**: en HF el karma es positivo para PK. |
| `broadcastReputation()` | `broadcastKarma()` | |
| `chargeShot(type)` | `setChargedShot(type, true)` | |
| `doAutoAttack(target)` | `getAI().setIntention(Intention.ATTACK, target)` | El `doAttack` de HF es un solo golpe; la intención ATTACK mantiene el ciclo. |
| `getInstanceWorld()` / `setInstance()` | `getInstanceId()` / `setInstanceId(int)` | HF usa ids `int`. |
| `PlayerClass.KAMAEL_SOLDIER` | `MALE_SOLDIER` / `FEMALE_SOLDIER` | Las clases base Kamael tienen género fijo en HF. |
| `Race.SYLPH`, `Race.HIGH_ELF`, `SYLPH_GUNNER` | *(eliminados)* | No existen en HF. |

### 2. Trampas de players client-less resueltas

- **Idioma nulo + multilang**: sin cliente, `_lang` queda nulo; con multilang activo, `sendDamageMessage` reventaba con NPE en `NpcNameLocalisationData` y abortaba la cadena de daño. Fix: `setLang(default)` al preparar el phantom.
- **IDs de items entre crónicas**: los IDs de Essence apuntaban a items distintos en HF. Todos los packs se reconstruyeron con IDs verificados contra `data/stats/items` de HF.
- **Spawns territoriales HF** (`<spawn zone=>`): sin coordenadas por-npc; se resuelven al centroide del `<territory>`.
- **Skills de summon**: filtradas por la clase del efecto `Summon` real (no por el nombre).
- **Servitor + login**: restauración del servitor al loguear (si no, `character_summons` bloquea `canSummon`).
- **`online` en BD**: forzado a `1` al loguear y reafirmado cada tick (el autosave lo reescribe a 0 sin cliente).

Los 3 parches de `core-patches/` son la parte de esto que no se puede resolver desde el datapack.

### 3. Original del autor vs. añadido en el port

**Sistema original (MiaCodeWEB), adaptado a HF:** arquitectura modular, creación client-less, farmeo en spots reales, spawn con geodata, metas de nivel con descanso, MP-rest de magos, packs de equipo por grado, shots automáticos, limpieza de inventario, PvP/PK, panel `.pmenu`, chat opcional (Gemini).

**Añadido en el port (Rekiem):** gestor de población autónomo (curva horaria + sesiones + auto-arranque en el boot), pirámide de niveles + rango manual, comportamiento humano (pociones en combate, huida a HP crítico, sentarse-regenerar con decisión pelear-o-huir, magos priorizan magia, summoners con servitor asistiendo y viajando junto), panel GM ampliado (lista paginada, chat público, toggles persistentes), PK con nivel mínimo + drops retail + re-equipado automático, y refinos de realismo (teleport tímido, anti-montón, apariencia válida, nombres variados).

### 4. Compatibilidad

L2J Mobius CT 2.6 High Five, JDK 25, in-game. No interfiere con HWID (client-less). ~1000 phantoms → CPU 53–73%, 0 errores. Config en `config/Custom/PhantomAI.ini` (generado en el primer arranque).

---

## English <a name="en"></a>

### 1. Essence (RoseVain) → High Five (CT 2.6) API mapping

`org.l2jmobius.*` paths are identical across chronicles (Mobius unified the naming), so most of it compiled directly. The changes were API/semantics:

| Essence | High Five | Note |
|---|---|---|
| `setReputation(-X)` | `setKarma(+X)` | **Inverted sign**: in HF karma is positive for PK. |
| `broadcastReputation()` | `broadcastKarma()` | |
| `chargeShot(type)` | `setChargedShot(type, true)` | |
| `doAutoAttack(target)` | `getAI().setIntention(Intention.ATTACK, target)` | HF's `doAttack` is a single hit; the ATTACK intention sustains the loop. |
| `getInstanceWorld()` / `setInstance()` | `getInstanceId()` / `setInstanceId(int)` | HF uses `int` ids. |
| `PlayerClass.KAMAEL_SOLDIER` | `MALE_SOLDIER` / `FEMALE_SOLDIER` | Kamael base classes are gender-locked in HF. |
| `Race.SYLPH`, `Race.HIGH_ELF`, `SYLPH_GUNNER` | *(removed)* | Do not exist in HF. |

### 2. Client-less player pitfalls resolved

- **Null language + multilang**: with no client, `_lang` is null; with multilang on, `sendDamageMessage` threw an NPE in `NpcNameLocalisationData` and aborted the damage chain. Fix: `setLang(default)` when preparing the phantom.
- **Cross-chronicle item IDs**: Essence IDs pointed to different items in HF. All gear packs were rebuilt with IDs verified against HF's `data/stats/items`.
- **HF territory spawns** (`<spawn zone=>`): no per-npc coordinates; resolved to the `<territory>` centroid.
- **Summon skills**: filtered by the actual `Summon` effect class, not by name.
- **Servitor + login**: servitor restore runs on login (otherwise `character_summons` blocks `canSummon`).
- **`online` in DB**: forced to `1` on login and re-asserted every tick (the autosave rewrites it to 0 for clientless players).

The 3 `core-patches/` are the part of this that can't be solved from the datapack.

### 3. Author's original vs. added in the port

**Original system (MiaCodeWEB), adapted to HF:** modular architecture, client-less creation, farming at real spots, geodata-safe spawning, level goals with town rest, mage MP-rest, gear packs by grade, automatic shots, inventory cleanup, PvP/PK, `.pmenu` panel, optional chat (Gemini).

**Added in the port (Rekiem):** autonomous population manager (hourly curve + sessions + boot auto-start), level pyramid + manual range, human-like behavior (potions in combat, flee at critical HP, sit-to-regen with fight-or-flee decision, mages prioritize magic, summoners with an assisting servitor that travels along), extended GM panel (paginated list, public chat, persistent toggles), PK with a minimum level + retail drops + automatic re-equip, and realism touches (timid teleport, anti-clustering, valid appearance, varied names).

### 4. Compatibility

L2J Mobius CT 2.6 High Five, JDK 25, in-game. Doesn't interfere with HWID (client-less). ~1000 phantoms → CPU 53–73%, 0 errors. Runtime config in `config/Custom/PhantomAI.ini` (generated on first start).
